-- Rollback for 20261011120000_auth_identity_map.sql (drops only the new objects).
drop function if exists public.resolve_canonical_uid(uuid);
drop table if exists public.auth_identity_map;
