-- Stage 1 of Firebase -> Supabase Auth migration (ADDITIVE ONLY).
-- Maps a Supabase Auth user onto the canonical (Firebase) uid that every
-- existing table keys on. Service role only: RLS enabled, no policies.
-- Rollback: 20261011120000_auth_identity_map.rollback.sql

create table if not exists public.auth_identity_map (
  supabase_user_id uuid primary key references auth.users(id) on delete cascade,
  canonical_uid    text not null unique,
  firebase_uid     text,
  provider         text,
  created_at       timestamptz not null default now(),
  linked_at        timestamptz not null default now()
);

create index if not exists auth_identity_map_firebase_uid_idx
  on public.auth_identity_map (firebase_uid) where firebase_uid is not null;

alter table public.auth_identity_map enable row level security;
-- Intentionally NO policies: anon/authenticated get no access; service_role bypasses RLS.
revoke all on table public.auth_identity_map from anon, authenticated;

create or replace function public.resolve_canonical_uid(p_supabase_user_id uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select m.canonical_uid from public.auth_identity_map m
  where m.supabase_user_id = p_supabase_user_id;
$$;

revoke all on function public.resolve_canonical_uid(uuid) from public, anon, authenticated;
grant execute on function public.resolve_canonical_uid(uuid) to service_role;
