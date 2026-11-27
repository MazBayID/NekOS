-- Provider-aware payment attempts for Midtrans and Duitku.
-- One active attempt per order prevents a customer from paying two gateways at once.
create unique index if not exists skin_payments_one_active_attempt_per_order
on private.skin_payments(order_id)
where status in ('created', 'pending', 'approved');

create or replace function public.skin_active_payment_attempt(p_order_id uuid)
returns jsonb
language sql
security definer
set search_path = pg_catalog, private
as $$
  select jsonb_build_object(
    'provider', p.provider,
    'provider_order_id', p.provider_order_id,
    'provider_payment_id', p.provider_payment_id,
    'checkout_url', p.checkout_url,
    'status', p.status
  )
  from private.skin_payments p
  where p.order_id = p_order_id
    and p.status in ('created', 'pending', 'approved')
  order by p.created_at desc
  limit 1;
$$;

drop function if exists public.skin_mark_paid(uuid, text, text, jsonb);
create function public.skin_mark_paid(
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
  if p_provider not in ('midtrans', 'duitku') then return false; end if;

  select order_id into v_order_id
  from private.skin_payments
  where provider = p_provider
    and provider_order_id = p_provider_order_id;

  if not found then return false; end if;

  select * into v_order
  from private.skin_orders
  where id = v_order_id
  for update;
  if not found then return false; end if;

  select * into v_payment
  from private.skin_payments
  where order_id = v_order_id
    and provider = p_provider
    and provider_order_id = p_provider_order_id
  for update;
  if not found then return false; end if;

  if coalesce(p_payload->>'gross_amount', '') !~ '^[0-9]+([.][0-9]+)?$' then
    return false;
  end if;
  v_remote_amount := (p_payload->>'gross_amount')::numeric;
  v_remote_currency := upper(coalesce(p_payload->>'currency', ''));

  if v_remote_amount <> v_order.amount
     or v_remote_currency <> upper(v_order.currency) then
    return false;
  end if;

  if v_order.status = 'paid' then
    update private.skin_payments
    set status = 'completed',
        provider_payment_id = coalesce(p_provider_payment_id, provider_payment_id),
        provider_payload = coalesce(p_payload, '{}'::jsonb),
        updated_at = now()
    where id = v_payment.id;
    return true;
  end if;
  if v_order.status <> 'pending' then return false; end if;

  update private.skin_orders
  set status = 'paid', paid_at = now()
  where id = v_order_id;

  update private.skin_payments
  set status = 'completed',
      provider_payment_id = coalesce(p_provider_payment_id, provider_payment_id),
      provider_payload = coalesce(p_payload, '{}'::jsonb),
      updated_at = now()
  where id = v_payment.id;

  insert into private.skin_entitlements(device_id, skin_id, order_id, status)
  values (v_order.device_id, v_order.skin_id, v_order.id, 'active')
  on conflict (device_id, skin_id)
  do update set
    status = 'active',
    order_id = excluded.order_id,
    granted_at = now();

  return true;
end;
$$;

drop function if exists public.skin_update_payment_status(uuid, text, jsonb);
create function public.skin_update_payment_status(
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
  if p_provider not in ('midtrans', 'duitku') then return false; end if;

  v_payment_status := case lower(p_status)
    when 'expire' then 'failed'
    when 'expired' then 'failed'
    when 'deny' then 'failed'
    when 'failed' then 'failed'
    when 'cancel' then 'cancelled'
    when 'cancelled' then 'cancelled'
    when '02' then 'failed'
    else null
  end;
  if v_payment_status is null then return false; end if;

  update private.skin_payments
  set status = v_payment_status,
      provider_payload = coalesce(p_payload, '{}'::jsonb),
      updated_at = now()
  where provider = p_provider
    and provider_order_id = p_provider_order_id
    and status in ('created', 'pending', 'approved');

  return found;
end;
$$;

create or replace function public.skin_order_status(p_order_id uuid, p_device_hash text)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, private, public
as $$
declare
  v_result jsonb;
begin
  select jsonb_build_object(
    'order_id', o.id,
    'status', o.status,
    'skin_id', o.skin_id,
    'skin_name', s.name,
    'amount', o.amount,
    'currency', o.currency,
    'created_at', o.created_at,
    'paid_at', o.paid_at,
    'owned', exists(
      select 1 from private.skin_entitlements e
      where e.device_id = o.device_id
        and e.skin_id = o.skin_id
        and e.status = 'active'
    ),
    'active_payment', (
      select jsonb_build_object(
        'provider', p.provider,
        'provider_order_id', p.provider_order_id,
        'checkout_url', p.checkout_url,
        'status', p.status
      )
      from private.skin_payments p
      where p.order_id = o.id
        and p.status in ('created', 'pending', 'approved')
      order by p.created_at desc
      limit 1
    )
  )
  into v_result
  from private.skin_orders o
  join private.device_installations d on d.id = o.device_id
  join public.skins s on s.id = o.skin_id
  where o.id = p_order_id
    and d.device_hash = p_device_hash;

  return v_result;
end;
$$;

revoke all on function public.skin_active_payment_attempt(uuid) from public, anon, authenticated;
revoke all on function public.skin_mark_paid(text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.skin_update_payment_status(text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.skin_active_payment_attempt(uuid) to service_role;
grant execute on function public.skin_mark_paid(text, text, text, jsonb) to service_role;
grant execute on function public.skin_update_payment_status(text, text, text, jsonb) to service_role;
