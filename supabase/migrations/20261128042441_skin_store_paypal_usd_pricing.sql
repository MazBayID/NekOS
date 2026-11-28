alter table public.skins
  add column price_usd numeric(10,2);

alter table public.skins
  add constraint skins_price_usd_check
  check (price_usd is null or price_usd >= 0);
