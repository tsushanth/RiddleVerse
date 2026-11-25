-- Copies of games removed through DELETE /api/game-creation/:gameId. Shipped apps call that route without a credential
-- and creator ids are public, so a delete must be reversible. Written by the backend (service role) only.
create table if not exists public.deleted_games (
    id uuid primary key default gen_random_uuid(),
    game_id uuid not null,
    creator_id text,
    verified boolean not null default false,
    game jsonb not null,
    variants jsonb not null default '[]'::jsonb,
    deleted_at timestamptz not null default now()
);
create index if not exists idx_deleted_games_game on public.deleted_games (game_id);
create index if not exists idx_deleted_games_creator on public.deleted_games (creator_id);
alter table public.deleted_games enable row level security;
revoke all on public.deleted_games from anon, authenticated;

-- Restore one game (play history is not kept; it cascades with the delete):
--   insert into public.custom_games
--     select (jsonb_populate_record(null::public.custom_games, d.game)).*
--     from public.deleted_games d where d.game_id = '<game id>' order by d.deleted_at desc limit 1;
--   insert into public.game_difficulty_variants
--     select (jsonb_populate_record(null::public.game_difficulty_variants, v)).*
--     from (select variants from public.deleted_games where game_id = '<game id>' order by deleted_at desc limit 1) d,
--     jsonb_array_elements(d.variants) v;
