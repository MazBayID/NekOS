alter table private.skin_payments
  add constraint skin_payments_provider_supported_check
  check (provider in ('midtrans','paypal'));

alter table private.skin_payment_events
  add constraint skin_payment_events_provider_supported_check
  check (provider in ('midtrans','paypal'));

create or replace function public.skin_mark_paid(
  p_provider text,
  p_provider_order_id text,
  p_provider_payment_id text,
  p_payload jsonb default '{}'::jsonb
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, private
as $$
declare
  v_order_id uuid;
  v_order private.skin_orders%rowtype;
  v_payment private.skin_payments%rowtype;
  v_remote_amount numeric;
  v_remote_currency text;
begin
  if p_provider not in ('midtrans','paypal') then return false; end if;

  select order_id into v_order_id
  from private.skin_payments
  where provider=p_provider and provider_order_id=p_provider_order_id;
  if not found then return false; end if;

  select * into v_order
  from private.skin_orders
  where id=v_order_id
  for update;
  if not found then return false; end if;

  select * into v_payment
  from private.skin_payments
  where order_id=v_order_id
    and provider=p_provider
    and provider_order_id=p_provider_order_id
  for update;
  if not found then return false; end if;

  if v_payment.status not in ('created','pending','approved','completed') then
    return false;
  end if;

  if coalesce(p_payload->>'gross_amount','') !~ '^[0-9]+([.][0-9]+)?$' then
    return false;
  end if;
  v_remote_amount := (p_payload->>'gross_amount')::numeric;
  v_remote_currency := upper(coalesce(p_payload->>'currency',''));
  if v_remote_amount<>v_order.amount or v_remote_currency<>upper(v_order.currency) then
    return false;
  end if;
  if (p_provider='paypal' and v_order.currency<>'USD')
     or (p_provider='midtrans' and v_order.currency<>'IDR') then
    return false;
  end if;

  if v_order.status='paid' then
    update private.skin_payments
    set status='completed',
        provider_payment_id=coalesce(p_provider_payment_id,provider_payment_id),
        provider_payload=coalesce(p_payload,'{}'::jsonb),
        updated_at=now()
    where id=v_payment.id;
    return true;
  end if;
  if v_order.status<>'pending' then return false; end if;

  update private.skin_orders
  set status='paid',paid_at=now()
  where id=v_order_id;

  update private.skin_payments
  set status='completed',
      provider_payment_id=coalesce(p_provider_payment_id,provider_payment_id),
      provider_payload=coalesce(p_payload,'{}'::jsonb),
      updated_at=now()
  where id=v_payment.id;

  insert into private.skin_entitlements(device_id,skin_id,order_id,status)
  values(v_order.device_id,v_order.skin_id,v_order.id,'active')
  on conflict(device_id,skin_id) do update
    set status='active',order_id=excluded.order_id,granted_at=now();

  return true;
end;
$$;

create or replace function public.skin_update_payment_status(
  p_provider text,
  p_provider_order_id text,
  p_status text,
  p_payload jsonb default '{}'::jsonb
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, private
as $$
declare
  v_payment_status text;
begin
  if p_provider not in ('midtrans','paypal') then return false; end if;

  v_payment_status := case lower(p_status)
    when 'expire' then 'failed'
    when 'expired' then 'failed'
    when 'deny' then 'failed'
    when 'failed' then 'failed'
    when 'cancel' then 'cancelled'
    when 'cancelled' then 'cancelled'
    else null
  end;
  if v_payment_status is null then return false; end if;

  update private.skin_payments
  set status=v_payment_status,
      provider_payload=coalesce(p_payload,'{}'::jsonb),
      updated_at=now()
  where provider=p_provider
    and provider_order_id=p_provider_order_id
    and status in ('created','pending','approved');

  return found;
end;
$$;

create or replace function public.skin_create_paypal_order(
  p_device_hash text,
  p_skin_id uuid,
  p_app_version text default null
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, private, public
as $$
declare
  v_device uuid;
  v_skin public.skins%rowtype;
  v_order_id uuid;
  v_amount numeric;
  v_currency text;
  v_has_payment boolean;
  v_has_active_payment boolean;
begin
  v_device := public.skin_register_device(p_device_hash,p_app_version);

  -- Serialize all gateway choices for the same device/skin with the same lock.
  perform pg_advisory_xact_lock(
    hashtextextended(v_device::text || ':' || p_skin_id::text,0)
  );

  select * into v_skin
  from public.skins
  where id=p_skin_id and status='published';
  if not found then raise exception 'skin_not_found'; end if;
  if v_skin.price_amount<=0 then raise exception 'skin_is_free'; end if;
  if v_skin.price_usd is null or v_skin.price_usd<=0 then
    raise exception 'paypal_price_not_configured';
  end if;

  if exists (
    select 1 from private.skin_entitlements e
    where e.device_id=v_device and e.skin_id=p_skin_id and e.status='active'
  ) then
    raise exception 'already_owned';
  end if;

  select o.id,o.amount,o.currency
  into v_order_id,v_amount,v_currency
  from private.skin_orders o
  where o.device_id=v_device
    and o.skin_id=p_skin_id
    and o.status='pending'
  order by o.created_at desc
  limit 1
  for update;

  if found then
    select
      exists(select 1 from private.skin_payments p where p.order_id=v_order_id),
      exists(select 1 from private.skin_payments p
        where p.order_id=v_order_id and p.status in ('created','pending','approved'))
    into v_has_payment,v_has_active_payment;

    -- Preserve any active attempt regardless of gateway. The Edge Function
    -- detects a provider mismatch and will not create a second checkout.
    if v_has_active_payment or (not v_has_payment and v_currency='USD') then
      return jsonb_build_object(
        'order_id',v_order_id,'amount',v_amount,'currency',v_currency,
        'skin_id',p_skin_id,'skin_name',v_skin.name,'reused',true
      );
    end if;

    update private.skin_orders
    set status='failed'
    where id=v_order_id and status='pending';
  end if;

  insert into private.skin_orders(device_id,skin_id,amount,currency)
  values(v_device,p_skin_id,v_skin.price_usd,'USD')
  returning id into v_order_id;

  return jsonb_build_object(
    'order_id',v_order_id,'amount',v_skin.price_usd,'currency','USD',
    'skin_id',v_skin.id,'skin_name',v_skin.name,'reused',false
  );
end;
$$;

revoke all on function public.skin_mark_paid(text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.skin_update_payment_status(text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.skin_create_paypal_order(text,uuid,text) from public,anon,authenticated;
grant execute on function public.skin_mark_paid(text,text,text,jsonb) to service_role;
grant execute on function public.skin_update_payment_status(text,text,text,jsonb) to service_role;
grant execute on function public.skin_create_paypal_order(text,uuid,text) to service_role;
