-- A terminal Midtrans attempt consumes its provider order ID permanently.
-- Keep reusing an order only while it has an active attempt, or before any
-- payment attempt has been attached. Close terminal orders before creating a
-- fresh local order so the partial unique index allows a new purchase attempt.
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
  v_has_payment boolean;
  v_has_active_payment boolean;
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
    select
      exists(select 1 from private.skin_payments p where p.order_id = v_order_id),
      exists(select 1 from private.skin_payments p
        where p.order_id = v_order_id and p.status in ('created', 'pending', 'approved'))
    into v_has_payment, v_has_active_payment;

    if v_has_active_payment or not v_has_payment then
      select p.checkout_url into v_checkout_url
      from private.skin_payments p
      where p.order_id = v_order_id
        and p.status in ('created', 'pending', 'approved')
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

    -- Midtrans order IDs cannot be reused after a payment attempt is terminal.
    -- Close the old local order so the next attempt receives a fresh UUID.
    update private.skin_orders
    set status = 'failed'
    where id = v_order_id and status = 'pending';
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

revoke all on function public.skin_create_order(text,uuid,text) from public, anon, authenticated;
grant execute on function public.skin_create_order(text,uuid,text) to service_role;
