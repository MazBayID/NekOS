-- Prevent concurrent purchase requests from creating multiple pending orders
-- for the same device and skin. Reuse the original order and checkout URL.
create unique index if not exists skin_orders_one_pending_per_device_skin
on private.skin_orders(device_id, skin_id)
where status = 'pending';

create or replace function public.skin_create_order(
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
  v_checkout_url text;
begin
  v_device := public.skin_register_device(p_device_hash, p_app_version);

  perform pg_advisory_xact_lock(
    hashtextextended(v_device::text || ':' || p_skin_id::text, 0)
  );

  select * into v_skin
  from public.skins
  where id = p_skin_id and status = 'published';

  if not found then raise exception 'skin_not_found'; end if;
  if v_skin.price_amount <= 0 then raise exception 'skin_is_free'; end if;
  if v_skin.currency <> 'IDR' or v_skin.price_amount <> trunc(v_skin.price_amount) then
    raise exception 'unsupported_price_currency';
  end if;

  if exists (
    select 1 from private.skin_entitlements e
    where e.device_id = v_device and e.skin_id = p_skin_id and e.status = 'active'
  ) then
    raise exception 'already_owned';
  end if;

  select o.id, o.amount, o.currency
  into v_order_id, v_amount, v_currency
  from private.skin_orders o
  where o.device_id = v_device
    and o.skin_id = p_skin_id
    and o.status = 'pending'
  order by o.created_at desc
  limit 1
  for update;

  if found then
    select p.checkout_url into v_checkout_url
    from private.skin_payments p
    where p.order_id = v_order_id and p.provider = 'midtrans'
    order by p.created_at desc
    limit 1;

    return jsonb_build_object(
      'order_id', v_order_id,
      'amount', v_amount,
      'currency', v_currency,
      'skin_id', p_skin_id,
      'skin_name', v_skin.name,
      'checkout_url', v_checkout_url,
      'reused', true
    );
  end if;

  insert into private.skin_orders(device_id, skin_id, amount, currency)
  values (v_device, p_skin_id, v_skin.price_amount, 'IDR')
  returning id into v_order_id;

  return jsonb_build_object(
    'order_id', v_order_id,
    'amount', v_skin.price_amount,
    'currency', 'IDR',
    'skin_id', v_skin.id,
    'skin_name', v_skin.name,
    'checkout_url', null,
    'reused', false
  );
end;
$$;

create or replace function public.skin_attach_payment(
  p_order_id uuid,
  p_provider_order_id text,
  p_checkout_url text,
  p_provider text default 'midtrans'
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, private
as $$
begin
  if not exists (
    select 1 from private.skin_orders
    where id = p_order_id and status = 'pending'
  ) then
    raise exception 'order_not_pending';
  end if;

  insert into private.skin_payments(
    order_id, provider, provider_order_id, checkout_url, status
  )
  values (
    p_order_id, p_provider, p_provider_order_id, p_checkout_url, 'pending'
  )
  on conflict (provider, provider_order_id)
    where provider_order_id is not null
  do update set
    checkout_url = excluded.checkout_url,
    updated_at = now()
  where private.skin_payments.order_id = excluded.order_id;

  if not found then raise exception 'payment_order_conflict'; end if;
end;
$$;

revoke all on function public.skin_create_order(text,uuid,text) from public, anon, authenticated;
grant execute on function public.skin_create_order(text,uuid,text) to service_role;
revoke all on function public.skin_attach_payment(uuid,text,text,text) from public, anon, authenticated;
grant execute on function public.skin_attach_payment(uuid,text,text,text) to service_role;
