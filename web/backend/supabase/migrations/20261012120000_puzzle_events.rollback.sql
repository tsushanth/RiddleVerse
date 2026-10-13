-- Rollback for 20261012120000_puzzle_events.sql (drops ONLY objects it created; deletes all puzzle_events data).
drop function if exists public.purge_puzzle_events(int);
drop function if exists public.puzzle_monitoring_summary(int, text);
drop table if exists public.puzzle_events;
