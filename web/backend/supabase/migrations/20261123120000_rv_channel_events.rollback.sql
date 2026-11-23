-- Rollback for 20261123120000_rv_channel_events.sql: drops the channel event log and its two functions. Nothing else is touched
-- (puzzle_events, the rv_telegram_* tables and chat_scores / game_sessions are unaffected). The bot keeps working: its event
-- writer backs off quietly when the table is missing.
drop function if exists public.rv_channel_stats(int);
drop function if exists public.rv_purge_channel_events(int);
drop table if exists public.rv_channel_events;
