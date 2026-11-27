
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to service_role;

create table public.skins (
  id uuid primary key default gen_random_uuid(),
  skin_key text not null unique
    check (skin_key ~ '^[a-z0-9_]+$'),
  name text not null,
  author text not null,
  description text,
  schema_version smallint not null check (schema_version > 0),
  min_nekos_version text,
  preview_path text,
  package_bucket text not null
    check (package_bucket in ('skin-packages-free','skin-packages-premium')),
  package_path text not null unique,
  package_sha256 text not null
    check (package_sha256 ~ '^[a-f0-9]{64}$'),
  package_size_bytes bigint not null check (package_size_bytes > 0),
  price_amount numeric(12,2) not null default 0
    check (price_amount >= 0),
  currency text not null default 'USD'
    check (currency ~ '^[A-Z]{3}$'),
  status text not null default 'draft'
    check (status in ('draft','published','archived')),
  created_at timestamptz not null default now(),
  published_at timestamptz,
  constraint skins_publication_timestamp_check check (
    (status = 'draft' and published_at is null)
    or (status in ('published','archived') and published_at is not null)
  ),
  constraint skins_package_price_bucket_check check (
    (price_amount = 0 and package_bucket = 'skin-packages-free')
    or (price_amount > 0 and package_bucket = 'skin-packages-premium')
  )
);

create index skins_catalog_idx
  on public.skins(status, created_at desc);

alter table public.skins enable row level security;
revoke all on table public.skins from public, anon, authenticated;
grant all on table public.skins to service_role;

create table private.device_installations (
  id uuid primary key default gen_random_uuid(),
  device_hash text not null unique
    check (device_hash ~ '^[a-f0-9]{64}$'),
  identity_scheme text not null default 'android_id_hmac_v1',
  app_version text,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table private.skin_orders (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null
    references private.device_installations(id) on delete restrict,
  skin_id uuid not null
    references public.skins(id) on delete restrict,
  amount numeric(12,2) not null check (amount > 0),
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  status text not null default 'pending'
    check (status in ('pending','paid','failed','cancelled','refunded')),
  created_at timestamptz not null default now(),
  paid_at timestamptz
);

create index skin_orders_device_idx
  on private.skin_orders(device_id, created_at desc);
create index skin_orders_skin_idx
  on private.skin_orders(skin_id);

create table private.skin_payments (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null
    references private.skin_orders(id) on delete restrict,
  provider text not null,
  provider_order_id text,
  provider_payment_id text,
  checkout_url text,
  status text not null default 'created'
    check (status in ('created','pending','approved','completed','failed','cancelled','refunded')),
  provider_payload jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index skin_payments_provider_order_unique
  on private.skin_payments(provider, provider_order_id)
  where provider_order_id is not null;
create unique index skin_payments_provider_payment_unique
  on private.skin_payments(provider, provider_payment_id)
  where provider_payment_id is not null;
create index skin_payments_order_idx
  on private.skin_payments(order_id, created_at desc);

create table private.skin_entitlements (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null
    references private.device_installations(id) on delete restrict,
  skin_id uuid not null
    references public.skins(id) on delete restrict,
  order_id uuid not null unique
    references private.skin_orders(id) on delete restrict,
  status text not null default 'active'
    check (status in ('active','revoked')),
  granted_at timestamptz not null default now(),
  unique(device_id, skin_id)
);

create index skin_entitlements_device_idx
  on private.skin_entitlements(device_id);

create table private.skin_payment_events (
  id uuid primary key default gen_random_uuid(),
  provider text not null,
  provider_event_id text not null,
  event_type text not null,
  payload jsonb,
  processing_status text not null default 'received'
    check (processing_status in ('received','processed','failed')),
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  unique(provider, provider_event_id)
);

alter table private.device_installations enable row level security;
alter table private.skin_orders enable row level security;
alter table private.skin_payments enable row level security;
alter table private.skin_entitlements enable row level security;
alter table private.skin_payment_events enable row level security;

revoke all on all tables in schema private from public, anon, authenticated;
grant all on all tables in schema private to service_role;

