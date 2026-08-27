-- Migration: create game_sessions table
-- Run this in Supabase SQL editor

create table if not exists game_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id text not null,
  game_id text not null,
  source text not null check (source in ('ios', 'android', 'telegram', 'web')),
  chat_id text,                          -- Telegram group chat id (nullable)
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  duration_seconds int,
  is_billable bool default false,        -- true when duration > 15s
  score int,
  score_source text check (score_source in ('reported', 'derived', 'default')),
  forced_by text,                        -- how the session ended
  events jsonb default '[]'::jsonb       -- raw interaction signals array
);

create index if not exists game_sessions_user_id_idx on game_sessions(user_id);
create index if not exists game_sessions_game_id_idx on game_sessions(game_id);
create index if not exists game_sessions_started_at_idx on game_sessions(started_at desc);
create index if not exists game_sessions_chat_id_idx on game_sessions(chat_id) where chat_id is not null;
