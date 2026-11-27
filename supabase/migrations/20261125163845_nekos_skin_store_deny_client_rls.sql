
create policy "Deny client access to skins"
  on public.skins for all to anon, authenticated
  using (false) with check (false);

create policy "Deny client access to device installations"
  on private.device_installations for all to anon, authenticated
  using (false) with check (false);

create policy "Deny client access to skin orders"
  on private.skin_orders for all to anon, authenticated
  using (false) with check (false);

create policy "Deny client access to skin payments"
  on private.skin_payments for all to anon, authenticated
  using (false) with check (false);

create policy "Deny client access to skin entitlements"
  on private.skin_entitlements for all to anon, authenticated
  using (false) with check (false);

create policy "Deny client access to payment events"
  on private.skin_payment_events for all to anon, authenticated
  using (false) with check (false);
