-- App language of each registered push token (one of the 30 app languages, 'en' by default). Push text (streak
-- reminders, "puzzles ready") is localized from it. The backend works without this column: tokens are then treated as
-- English, so it can be applied before or after the deploy.
ALTER TABLE user_notification_tokens ADD COLUMN IF NOT EXISTS language TEXT;
