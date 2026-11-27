-- Where a telemetry row came from: 'testlab' (Firebase Test Lab / Play pre-launch devices), 'other', or null (rows
-- written before this column, or no readable address). The address itself is never stored.
alter table public.puzzle_events add column if not exists traffic text;
