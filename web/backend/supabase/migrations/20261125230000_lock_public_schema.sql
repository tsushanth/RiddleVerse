-- Only the backend (service role) may touch this database. Before this, the publishable key that ships in the app had
-- read and write access to most tables, to several functions and to three storage buckets.
-- Public storage objects stay readable by URL (the buckets are public); the monitor's outcome_checks function (token
-- protected) stays callable. A new function in schema public must "revoke execute ... from public" in its own migration.
begin;

revoke all privileges on all tables in schema public from anon, authenticated, public;
revoke all privileges on all sequences in schema public from anon, authenticated, public;
revoke execute on all functions in schema public from anon, authenticated, public;
grant execute on all functions in schema public to service_role;
grant execute on function public.outcome_checks(text) to anon;

-- Second layer: row level security with no policy denies every role except the owner and the service role.
do $$
declare t record;
begin
    for t in select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity
    loop
        execute format('alter table public.%I enable row level security', t.relname);
    end loop;
end $$;

-- Tables, sequences and functions created later by migrations do not get the old grants back.
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke execute on functions from anon, authenticated;

-- Anyone could upload, overwrite or delete audio, images and game bundles. The backend writes with the service role.
drop policy if exists "Allow all operations on puzzle-audio" on storage.objects;
drop policy if exists "Allow uploads to game-bundles" on storage.objects;
drop policy if exists "Allow uploads to puzzle-images" on storage.objects;
drop policy if exists "Allow upsert on game-bundles" on storage.objects;
drop policy if exists "Allow upsert on puzzle-images" on storage.objects;

commit;
