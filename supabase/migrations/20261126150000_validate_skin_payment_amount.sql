-- Require Midtrans's verified amount and currency to match the immutable local order
-- before an entitlement can be granted.
create or replace function public.skin_mark_paid(
  p_order_id uuid,
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
  v_order private.skin_orders%rowtype;
  v_payment private.skin_payments%rowtype;
  v_remote_amount numeric;
  v_remote_currency text;
begin
  select * into v_order
  from private.skin_orders
  where id = p_order_id
  for update;

  if not found then return false; end if;

  select * into v_payment
  from private.skin_payments
  where order_id = p_order_id
    and provider = 'midtrans'
    and provider_order_id = p_provider_order_id
  for update;

  if not found then return false; end if;

  if coalesce(p_payload->>'gross_amount', '') !~ '^[0-9]+([.][0-9]+)? then
    return false;
  end if;

  v_remote_amount := (p_payload->>'gross_amount')::numeric;
  v_remote_currency := upper(coalesce(p_payload->>'currency', ''));

  if v_remote_amount <> v_order.amount
     or v_remote_currency <> upper(v_order.currency) then
    return false;
  end if;

  if v_order.status = 'paid' then return true; end if;
  if v_order.status <> 'pending' then return false; end if;

  update private.skin_orders
  set status = 'paid', paid_at = now()
  where id = p_order_id;

  update private.skin_payments
  set status = 'completed',
      provider_payment_id = coalesce(p_provider_payment_id, provider_payment_id),
      provider_payload = coalesce(p_payload, '{}'::jsonb),
      updated_at = now()
  where id = v_payment.id;

  insert into private.skin_entitlements(device_id, skin_id, order_id, status)
  values (v_order.device_id, v_order.skin_id, v_order.id, 'active')
  on conflict (device_id, skin_id)
  do update set status = 'active', order_id = excluded.order_id, granted_at = now();

  return true;
end;
$$;

revoke all on function public.skin_mark_paid(uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.skin_mark_paid(uuid,text,text,jsonb) to service_role;
 then
    return false;
  end if;

  v_remote_amount := (p_payload->>'gross_amount')::numeric;
  v_remote_currency := upper(coalesce(p_payload->>'currency', ''));

  if v_remote_amount <> v_order.amount
     or v_remote_currency <> upper(v_order.currency) then
    return false;
  end if;

  if v_order.status = 'paid' then return true; end if;
  if v_order.status <> 'pending' then return false; end if;

  update private.skin_orders
  set status = 'paid', paid_at = now()
  where id = p_order_id;

  update private.skin_payments
  set status = 'completed',
      provider_payment_id = coalesce(p_provider_payment_id, provider_payment_id),
      provider_payload = coalesce(p_payload, '{}'::jsonb),
      updated_at = now()
  where id = v_payment.id;

  insert into private.skin_entitlements(device_id, skin_id, order_id, status)
  values (v_order.device_id, v_order.skin_id, v_order.id, 'active')
  on conflict (device_id, skin_id)
  do update set status = 'active', order_id = excluded.order_id, granted_at = now();

  return true;
end;
$$;

revoke all on function public.skin_mark_paid(uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.skin_mark_paid(uuid,text,text,jsonb) to service_role;
