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
    'status', case
      when o.status = 'pending'
        and not exists (
          select 1 from private.skin_payments p
          where p.order_id = o.id and p.status in ('created','pending','approved')
        )
      then coalesce((
        select case when p.status = 'cancelled' then 'cancelled' else 'failed' end
        from private.skin_payments p
        where p.order_id = o.id and p.status in ('failed','cancelled')
        order by p.created_at desc limit 1
      ), o.status)
      else o.status
    end,
    'skin_id', o.skin_id,
    'skin_name', s.name,
    'amount', o.amount,
    'currency', o.currency,
    'created_at', o.created_at,
    'paid_at', o.paid_at,
    'owned', exists (
      select 1 from private.skin_entitlements e
      where e.device_id = o.device_id and e.skin_id = o.skin_id and e.status = 'active'
    ),
    'active_payment', (
      select jsonb_build_object(
        'provider', p.provider,
        'provider_order_id', p.provider_order_id,
        'checkout_url', p.checkout_url,
        'status', p.status,
        'created_at', p.created_at
      )
      from private.skin_payments p
      where p.order_id = o.id and p.status in ('created','pending','approved')
      order by p.created_at desc limit 1
    )
  )
  into v_result
  from private.skin_orders o
  join private.device_installations d on d.id = o.device_id
  join public.skins s on s.id = o.skin_id
  where o.id = p_order_id and d.device_hash = p_device_hash;
  return v_result;
end;
$$;