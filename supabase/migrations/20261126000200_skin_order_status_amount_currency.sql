create or replace function public.skin_order_status(p_order_id uuid, p_device_hash text)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'pg_catalog', 'private', 'public'
as $function$
declare v_result jsonb;
begin
  select jsonb_build_object(
    'order_id',o.id,
    'status',o.status,
    'skin_id',o.skin_id,
    'skin_name',s.name,
    'amount',o.amount,
    'currency',o.currency,
    'created_at',o.created_at,
    'paid_at',o.paid_at,
    'owned',exists(
      select 1 from private.skin_entitlements e
      where e.device_id=o.device_id and e.skin_id=o.skin_id and e.status='active'
    )
  )
  into v_result
  from private.skin_orders o
  join private.device_installations d on d.id=o.device_id
  join public.skins s on s.id=o.skin_id
  where o.id=p_order_id and d.device_hash=p_device_hash;
  return v_result;
end; $function$;
