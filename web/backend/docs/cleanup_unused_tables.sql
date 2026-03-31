-- ============================================
-- RiddleVerse Database Cleanup Script
-- Generated: 2026-01-11
-- ============================================
--
-- BEFORE RUNNING: Review each table and confirm it's safe to delete
-- Run SELECT COUNT(*) on each table first to verify it's empty
--

-- ============================================
-- SAFE TO DELETE (0 code references, 0 rows)
-- ============================================

-- user_trials: Duplicate of device_trials, never used
DROP TABLE IF EXISTS user_trials;

-- subscription_metrics: Never implemented, empty
DROP TABLE IF EXISTS subscription_metrics;

-- forge_analytics: View for Game Forge (feature removed)
DROP VIEW IF EXISTS forge_analytics;

-- ============================================
-- GAME FORGE FEATURE CLEANUP (Feature removed 2026-01-11)
-- ============================================

-- forge_sessions: Claude-powered game creation sessions (feature removed)
DROP TABLE IF EXISTS forge_sessions;

-- game_creation_sessions: AI game creation sessions (feature removed)
DROP TABLE IF EXISTS game_creation_sessions;

-- game_build_jobs: Background jobs for AI game building (feature removed)
DROP TABLE IF EXISTS game_build_jobs;

-- game_template_prompt_chips: AI-generated prompt suggestions (feature removed)
DROP TABLE IF EXISTS game_template_prompt_chips;

-- custom_games: Keep this table but delete AI-created games
-- DELETE FROM custom_games WHERE creation_method = 'claude_code';
-- DELETE FROM custom_games WHERE game_type LIKE 'forge_%';


-- ============================================
-- POTENTIALLY UNUSED (verify before deleting)
-- ============================================

-- Check row counts first:
-- SELECT 'puzzle_hashes' as tbl, COUNT(*) FROM puzzle_hashes
-- UNION ALL SELECT 'generated_puzzles', COUNT(*) FROM generated_puzzles
-- UNION ALL SELECT 'rejected_prefixes', COUNT(*) FROM rejected_prefixes
-- UNION ALL SELECT 'used_letter_sets', COUNT(*) FROM used_letter_sets
-- UNION ALL SELECT 'used_prefixes', COUNT(*) FROM used_prefixes
-- UNION ALL SELECT 'prefix_word_stats', COUNT(*) FROM prefix_word_stats;

-- These are used for puzzle deduplication - DO NOT DELETE unless you want to reset:
-- - puzzle_hashes
-- - generated_puzzles
-- - rejected_prefixes
-- - used_letter_sets
-- - used_prefixes
-- - prefix_word_stats
-- - puzzle_first_cache


-- ============================================
-- CLEANUP OLD DATA (keep tables, delete old rows)
-- ============================================

-- Delete analytics events older than 90 days
DELETE FROM analytics_events
WHERE created_at < NOW() - INTERVAL '90 days';

-- Delete old notification logs older than 30 days
DELETE FROM notification_logs
WHERE sent_at < NOW() - INTERVAL '30 days';

-- Delete old session data older than 60 days
DELETE FROM user_sessions
WHERE started_at < NOW() - INTERVAL '60 days';

-- Delete resolved system alerts older than 30 days
DELETE FROM system_alerts
WHERE resolved = true AND timestamp < NOW() - INTERVAL '30 days';

-- Delete old streak notification jobs older than 30 days
DELETE FROM streak_notification_jobs
WHERE started_at < NOW() - INTERVAL '30 days';

-- Delete old streak notification errors older than 14 days
DELETE FROM streak_notification_errors
WHERE timestamp < NOW() - INTERVAL '14 days';


-- ============================================
-- VACUUM AFTER CLEANUP (reclaim space)
-- ============================================

-- Run these after deleting data:
-- VACUUM ANALYZE analytics_events;
-- VACUUM ANALYZE notification_logs;
-- VACUUM ANALYZE user_sessions;
-- VACUUM ANALYZE system_alerts;


-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Check table sizes:
-- SELECT
--     schemaname,
--     relname as table_name,
--     pg_size_pretty(pg_total_relation_size(relid)) as total_size,
--     pg_size_pretty(pg_relation_size(relid)) as data_size,
--     n_live_tup as row_count
-- FROM pg_stat_user_tables
-- ORDER BY pg_total_relation_size(relid) DESC;

-- List all tables with row counts:
-- SELECT table_name,
--        (xpath('/row/cnt/text()', xml_count))[1]::text::int as row_count
-- FROM (
--   SELECT table_name,
--          query_to_xml(format('SELECT COUNT(*) as cnt FROM %I.%I', 'public', table_name), false, true, '') as xml_count
--   FROM information_schema.tables
--   WHERE table_schema = 'public'
-- ) t
-- ORDER BY row_count DESC;
