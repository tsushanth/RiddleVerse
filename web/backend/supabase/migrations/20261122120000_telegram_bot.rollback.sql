-- Rollback for 20261122120000_telegram_bot.sql (drops bot state; chat_scores / game_sessions rows are untouched).
drop index if exists public.puzzle_events_channel_idx;
alter table public.puzzle_events drop column if exists channel;
drop table if exists public.rv_telegram_link_codes;
drop table if exists public.rv_telegram_updates;
drop table if exists public.rv_telegram_users;
