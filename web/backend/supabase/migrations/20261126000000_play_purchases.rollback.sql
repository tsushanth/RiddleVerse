-- Drops the two tables and every purchase record in them. Roll the backend back first.
begin;
drop table if exists public.user_subscriptions;
drop table if exists public.play_purchases;
commit;
