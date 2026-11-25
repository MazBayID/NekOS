
-- Private device-bound purchases and package delivery RPCs for NekOS Skin Store.
create or replace function public.skin_register_device(p_device_hash text, p_app_version text default null)
returns uuid language plpgsql security definer set search_path = pg_catalog, private as $$
declare v_id uuid;
begin
  if p_device_hash !~ '^[0-9a-f]{64}$' then raise exception 'invalid_device_hash'; end if;
  insert into private.device_installations(device_hash, app_version, last_seen_at)
  values (p_device_hash, left(p_app_version, 32), now())
  on conflict (device_hash) do update set app_version=excluded.app_version,last_seen_at=now()
  returning id into v_id;
  return v_id;
end; $$;

create or replace function public.skin_create_order(p_device_hash text,p_skin_id uuid,p_app_version text default null)
returns jsonb language plpgsql security definer set search_path=pg_catalog,private,public as $$
declare v_device uuid; v_skin public.skins%rowtype; v_order uuid;
begin
  v_device:=public.skin_register_device(p_device_hash,p_app_version);
  select * into v_skin from public.skins where id=p_skin_id and status='published';
  if not found then raise exception 'skin_not_found'; end if;
  if v_skin.price_amount<=0 then raise exception 'skin_is_free'; end if;
  if v_skin.currency<>'IDR' or v_skin.price_amount<>trunc(v_skin.price_amount) then raise exception 'unsupported_price_currency'; end if;
  if exists(select 1 from private.skin_entitlements e where e.device_id=v_device and e.skin_id=p_skin_id and e.status='active') then raise exception 'already_owned'; end if;
  insert into private.skin_orders(device_id,skin_id,amount,currency) values(v_device,p_skin_id,v_skin.price_amount,'IDR') returning id into v_order;
  return jsonb_build_object('order_id',v_order,'amount',v_skin.price_amount,'currency','IDR','skin_id',v_skin.id,'skin_name',v_skin.name);
end; $$;

create or replace function public.skin_attach_payment(p_order_id uuid,p_provider_order_id text,p_checkout_url text,p_provider text default 'midtrans')
returns void language plpgsql security definer set search_path=pg_catalog,private as $$
begin
  if not exists(select 1 from private.skin_orders where id=p_order_id and status='pending') then raise exception 'order_not_pending'; end if;
  insert into private.skin_payments(order_id,provider,provider_order_id,checkout_url,status)
  values(p_order_id,p_provider,p_provider_order_id,p_checkout_url,'pending');
end; $$;

create or replace function public.skin_mark_paid(p_order_id uuid,p_provider_order_id text,p_provider_payment_id text,p_payload jsonb default '{}'::jsonb)
returns boolean language plpgsql security definer set search_path=pg_catalog,private as $$
declare v_order private.skin_orders%rowtype; v_payment private.skin_payments%rowtype;
begin
  select * into v_order from private.skin_orders where id=p_order_id for update;
  if not found then return false; end if;
  select * into v_payment from private.skin_payments where order_id=p_order_id and provider='midtrans' and provider_order_id=p_provider_order_id for update;
  if not found then return false; end if;
  if v_order.status='paid' then return true; end if;
  if v_order.status<>'pending' then return false; end if;
  update private.skin_orders set status='paid',paid_at=now() where id=p_order_id;
  update private.skin_payments set status='completed',provider_payment_id=coalesce(p_provider_payment_id,provider_payment_id),provider_payload=coalesce(p_payload,'{}'::jsonb),updated_at=now() where id=v_payment.id;
  insert into private.skin_entitlements(device_id,skin_id,order_id,status) values(v_order.device_id,v_order.skin_id,v_order.id,'active')
  on conflict(device_id,skin_id) do update set status='active',order_id=excluded.order_id,granted_at=now();
  return true;
end; $$;

create or replace function public.skin_update_payment_status(p_order_id uuid,p_status text,p_payload jsonb default '{}'::jsonb)
returns void language plpgsql security definer set search_path=pg_catalog,private as $$
begin
  update private.skin_orders set status=case when p_status='expire' then 'failed' when p_status='cancel' then 'cancelled' when p_status='deny' then 'failed' else status end where id=p_order_id and status='pending';
  update private.skin_payments set status=case when p_status='expire' then 'failed' when p_status='cancel' then 'cancelled' when p_status='deny' then 'failed' else status end,provider_payload=coalesce(p_payload,'{}'::jsonb),updated_at=now() where order_id=p_order_id and provider='midtrans';
end; $$;

create or replace function public.skin_order_status(p_order_id uuid,p_device_hash text)
returns jsonb language plpgsql security definer set search_path=pg_catalog,private,public as $$
declare v_result jsonb;
begin
  select jsonb_build_object('order_id',o.id,'status',o.status,'skin_id',o.skin_id,'skin_name',s.name,'paid_at',o.paid_at,
    'owned',exists(select 1 from private.skin_entitlements e where e.device_id=o.device_id and e.skin_id=o.skin_id and e.status='active'))
  into v_result from private.skin_orders o join private.device_installations d on d.id=o.device_id join public.skins s on s.id=o.skin_id
  where o.id=p_order_id and d.device_hash=p_device_hash;
  return v_result;
end; $$;

create or replace function public.skin_download_info(p_device_hash text,p_skin_id uuid,p_app_version text default null)
returns jsonb language plpgsql security definer set search_path=pg_catalog,private,public as $$
declare v_device uuid; v_result jsonb;
begin
  v_device:=public.skin_register_device(p_device_hash,p_app_version);
  select jsonb_build_object('skin_id',s.id,'skin_key',s.skin_key,'name',s.name,'package_bucket',s.package_bucket,'package_path',s.package_path,
    'package_sha256',s.package_sha256,'package_size_bytes',s.package_size_bytes)
  into v_result from public.skins s where s.id=p_skin_id and s.status='published'
    and (s.price_amount=0 or exists(select 1 from private.skin_entitlements e where e.device_id=v_device and e.skin_id=s.id and e.status='active'));
  return v_result;
end; $$;

revoke all on function public.skin_register_device(text,text) from public,anon,authenticated;
revoke all on function public.skin_create_order(text,uuid,text) from public,anon,authenticated;
revoke all on function public.skin_attach_payment(uuid,text,text,text) from public,anon,authenticated;
revoke all on function public.skin_mark_paid(uuid,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.skin_update_payment_status(uuid,text,jsonb) from public,anon,authenticated;
revoke all on function public.skin_order_status(uuid,text) from public,anon,authenticated;
revoke all on function public.skin_download_info(text,uuid,text) from public,anon,authenticated;
grant execute on function public.skin_register_device(text,text) to service_role;
grant execute on function public.skin_create_order(text,uuid,text) to service_role;
grant execute on function public.skin_attach_payment(uuid,text,text,text) to service_role;
grant execute on function public.skin_mark_paid(uuid,text,text,jsonb) to service_role;
grant execute on function public.skin_update_payment_status(uuid,text,jsonb) to service_role;
grant execute on function public.skin_order_status(uuid,text) to service_role;
grant execute on function public.skin_download_info(text,uuid,text) to service_role;
