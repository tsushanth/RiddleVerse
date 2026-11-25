-- Google Play purchases the server has verified. Additive: two new tables, server (service role) only.
begin;

-- Which account a purchase token belongs to (the first that presented it). Only a SHA-256 of the token.
create table if not exists public.play_purchases (
    token_hash text primary key,
    user_id text not null,
    kind text not null check (kind in ('subscription', 'coins')),
    product_id text not null,
    created_at timestamptz not null default now()
);

-- One row per subscription token. The token itself is kept to look up renewals at Play.
create table if not exists public.user_subscriptions (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    email text,
    product_id text not null,
    base_plan_id text,
    subscription_tier text not null,
    purchase_token_hash text not null unique,
    purchase_token text not null,
    latest_order_id text,
    play_state text,
    status text not null,
    subscription_start_date timestamptz,
    subscription_end_date timestamptz,
    platform text not null default 'android',
    test_purchase boolean not null default false,
    cancellation_date timestamptz,
    cancellation_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists user_subscriptions_user_idx on public.user_subscriptions (user_id);
create index if not exists user_subscriptions_due_idx on public.user_subscriptions (status, subscription_end_date);

alter table public.play_purchases enable row level security;
alter table public.user_subscriptions enable row level security;
revoke all privileges on public.play_purchases, public.user_subscriptions from anon, authenticated, public;
grant all privileges on public.play_purchases, public.user_subscriptions to service_role;

commit;
