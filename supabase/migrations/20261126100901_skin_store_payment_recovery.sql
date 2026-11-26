-- Make the payment-attempt row durable before calling Midtrans.
-- A retry reuses the same provider order ID and can fill in the checkout URL later.
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
    checkout_url = coalesce(excluded.checkout_url, private.skin_payments.checkout_url),
    updated_at = now()
  where private.skin_payments.order_id = excluded.order_id;

  if not found then raise exception 'payment_order_conflict'; end if;
end;
$$;

revoke all on function public.skin_attach_payment(uuid,text,text,text) from public, anon, authenticated;
grant execute on function public.skin_attach_payment(uuid,text,text,text) to service_role;
