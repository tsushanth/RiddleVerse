-- Only after the backend that writes to it has been rolled back: game deletion fails without this table.
drop table if exists public.deleted_games;
