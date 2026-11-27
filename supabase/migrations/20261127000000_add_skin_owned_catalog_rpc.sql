create or replace function public.skin_owned_catalog(
  p_device_hash text,
  p_app_version text default null
)
returns jsonb
language plpgsql
security definer
set search_path to 'pg_catalog', 'private', 'public'
as $function$
declare
  v_device uuid;
  v_result jsonb;
begin
  v_device := public.skin_register_device(p_device_hash, p_app_version);

  select coalesce(jsonb_agg(e.skin_id order by e.granted_at), '[]'::jsonb)
    into v_result
  from private.skin_entitlements e
  join public.skins s on s.id = e.skin_id
  where e.device_id = v_device
    and e.status = 'active'
    and s.status = 'published';

  return v_result;
end;
$function$;

revoke all on function public.skin_owned_catalog(text, text) from public, anon, authenticated;
grant execute on function public.skin_owned_catalog(text, text) to service_role;
