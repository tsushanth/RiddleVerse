# RiddleVerse Database Schema

**Last Updated:** 2026-01-11
**Database:** Supabase (PostgreSQL)
**Project ID:** uujjodxicvifmiwlimob
**URL:** https://uujjodxicvifmiwlimob.supabase.co

---

## Tables Overview (44 Tables)

| Table | Description |
|-------|-------------|
| `analytics_events` | User analytics and event tracking |
| `common_words` | Word dictionary for puzzles (crossword, word search, etc.) |
| `custom_games` | User-created games via AI Forge |
| `custom_leaderboard` | Leaderboard for custom puzzle sets |
| `daily_puzzles` | Daily puzzle assignments per user |
| `device_trials` | Free trial tracking per device |
| `forge_analytics` | AI game generation analytics (view) |
| `forge_sessions` | AI game generation sessions |
| `game_build_jobs` | Background build jobs for games |
| `game_creation_sessions` | Game creation session tracking |
| `game_plays` | Game play session records |
| `game_template_prompt_chips` | Prompt suggestions for game templates |
| `generated_puzzles` | Cache of generated puzzles |
| `generation_usage_log` | API usage tracking for puzzle generation |
| `ltv_audience_summary` | Lifetime value audience analytics |
| `ltv_user_data` | Per-user lifetime value metrics |
| `notification_configs` | Push notification configurations |
| `notification_jobs` | Scheduled notification job tracking |
| `notification_logs` | Push notification delivery logs |
| `prefix_word_stats` | Word prefix statistics for puzzles |
| `puzzle_first_cache` | Cache of first puzzle per type/difficulty |
| `puzzle_hashes` | Deduplication hashes for puzzles |
| `puzzle_path` | Puzzle sequence/progression chains |
| `puzzle_performances` | User puzzle performance metrics |
| `puzzle_regeneration_requests` | Requests to regenerate puzzle sets |
| `puzzle_sets` | Custom puzzle set metadata |
| `puzzles` | Main puzzle content storage |
| `rejected_prefixes` | Prefixes rejected for puzzle generation |
| `screenshot_library` | Shared screenshot assets |
| `streak_notification_errors` | Streak notification error logs |
| `streak_notification_jobs` | Streak notification job tracking |
| `subscription_metrics` | Subscription event tracking |
| `system_alerts` | System alert and monitoring logs |
| `system_config` | Application configuration key-values |
| `used_letter_sets` | Tracking used letter combinations |
| `used_prefixes` | Tracking used word prefixes |
| `user_feedback` | User feedback submissions |
| `user_generation_limits` | API rate limiting per user |
| `user_notification_preferences` | User notification settings |
| `user_notification_tokens` | FCM/APNs push notification tokens |
| `user_ratings` | App store rating prompts |
| `user_sessions` | User session tracking |
| `user_tiers` | Subscription tier definitions |
| `user_topics` | User topic preferences |
| `user_trials` | User trial period tracking |
| `users` | User account data |
| `word_antonyms` | Antonym pairs for puzzles |
| `word_frequency` | Word frequency data |

---

## Core Tables

### puzzles
Main puzzle content storage - the heart of the application.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | bigint | YES | Auto-increment ID |
| puzzleid | text | YES | Unique puzzle identifier (UUID) |
| type | text | YES | Puzzle type (wordsearch, crossword, etc.) |
| difficulty | text | YES | easy/medium/hard |
| question | text | YES | Puzzle question/content (JSON for complex types) |
| answer | text | YES | Correct answer(s) |
| hint | text | NO | Optional hint |
| options | jsonb | NO | Multiple choice options |
| parentSetId | text | NO | Parent riddle set ID |
| source | text | NO | Generation source |
| status | text | NO | Puzzle status |
| timestamp | timestamptz | NO | Creation timestamp |

### puzzle_path
Links puzzles in sequential order for progression.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| puzzleid | uuid | YES | Current puzzle ID (PK) |
| nextpuzzleid | uuid | NO | Next puzzle in sequence |
| type | text | YES | Puzzle type |
| difficulty | text | NO | Difficulty level |

### puzzle_sets
Custom puzzle set metadata (riddle-xxx IDs).

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | text | YES | Puzzle set ID (riddle-timestamp) |
| name | text | NO | Set name |
| creator | text | NO | Creator email |
| format | text | NO | Puzzle format type |
| puzzle_count | integer | NO | Number of puzzles |
| status | text | NO | completed/failed/generating |
| created_at | timestamptz | YES | Creation time |
| updated_at | timestamptz | YES | Last update |

---

## User Tables

### users
Core user account data.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| user_id | text | YES | Firebase UID (PK) |
| email | text | YES | User email |
| subscription_tier | text | NO | Current subscription |
| daily_count | integer | NO | Daily puzzle count |
| monthly_count | integer | NO | Monthly puzzle count |
| total_count | integer | NO | Total puzzles solved |
| trial_prompt_dismiss_count | integer | NO | Times dismissed trial prompt |
| trial_prompt_last_dismissed | bigint | NO | Last dismiss timestamp |
| created_at | timestamptz | NO | Account creation |
| updated_at | timestamptz | NO | Last update |

### user_notification_tokens
Push notification tokens for FCM/APNs.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_email | text | YES | User email |
| user_id | text | NO | Firebase UID |
| firebase_uid | text | NO | Firebase UID |
| fcm_token | text | YES | FCM/APNs token |
| platform | text | YES | ios/android |
| is_active | boolean | NO | Token active status |
| last_used | timestamptz | NO | Last notification sent |
| created_at | timestamptz | NO | Token creation |
| updated_at | timestamptz | NO | Last update |

### user_generation_limits
Rate limiting for puzzle generation API.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| user_email | varchar | YES | User email (PK) |
| tier | varchar | NO | Subscription tier |
| daily_limit | integer | NO | Max daily generations |
| daily_used | integer | NO | Used today |
| monthly_limit | integer | NO | Max monthly generations |
| monthly_used | integer | NO | Used this month |
| last_daily_reset | date | NO | Last daily reset date |
| last_monthly_reset | date | NO | Last monthly reset date |
| custom_limits | jsonb | NO | Custom limit overrides |
| created_at | timestamp | NO | Record creation |
| updated_at | timestamp | NO | Last update |

### user_sessions
Session tracking for analytics.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_id | uuid | NO | Firebase UID |
| session_id | varchar | YES | Session identifier |
| started_at | timestamptz | NO | Session start |
| ended_at | timestamptz | NO | Session end |
| duration_seconds | integer | NO | Session duration |
| puzzles_solved | integer | NO | Puzzles completed |
| total_score | integer | NO | Session score |
| app_version | varchar | NO | App version |
| device_info | jsonb | NO | Device details |

---

## Game Creation Tables

### custom_games
User-created games via AI Forge.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| title | varchar | YES | Game title |
| description | text | NO | Game description |
| html_content | text | NO | Full HTML game code |
| source_code | text | NO | Native app source |
| creator_id | varchar | NO | Firebase UID |
| creator_name | varchar | NO | Display name |
| game_type | varchar | NO | Game category |
| platform_type | varchar | NO | webview/ios/android |
| play_count | integer | NO | Times played |
| rating | numeric | NO | Average rating |
| is_featured | boolean | NO | Featured flag |
| initial_prompt | text | NO | Original user prompt |
| initial_screenshot_url | text | NO | Reference image |
| screenshot_urls | text[] | NO | Game screenshots |
| creation_method | text | NO | legacy/forge/template |
| compilation_metadata | jsonb | NO | Build info |
| created_at | timestamptz | NO | Creation time |
| updated_at | timestamptz | NO | Last update |

### forge_sessions
AI game generation session tracking.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_id | text | NO | Firebase UID |
| type | text | NO | game/puzzle |
| status | text | NO | created/generating/completed/failed |
| messages | jsonb | NO | Chat history |
| current_html | text | NO | Latest HTML output |
| build_attempts | integer | NO | Retry count |
| saved_game_id | uuid | NO | FK to custom_games |
| created_at | timestamptz | NO | Session start |
| updated_at | timestamptz | NO | Last activity |

### game_build_jobs
Background job queue for game compilation.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| session_id | uuid | NO | FK to forge_sessions |
| user_id | text | NO | Firebase UID |
| status | text | NO | pending/running/completed/failed |
| mode | text | NO | Build mode |
| prompt_text | text | NO | Generation prompt |
| template_id | text | NO | Template used |
| html_content | text | NO | Generated HTML |
| final_layout | jsonb | NO | Layout data |
| error_message | text | NO | Error details |
| additional_instructions | text | NO | Extra instructions |
| original_job_id | uuid | NO | Parent job for retries |
| game_id | uuid | NO | FK to custom_games |
| started_at | timestamptz | NO | Job start |
| completed_at | timestamptz | NO | Job completion |
| created_at | timestamptz | NO | Record creation |

### game_plays
Game play session records.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| game_id | uuid | NO | FK to custom_games |
| player_id | varchar | NO | Firebase UID |
| score | integer | NO | Play score |
| completion_time | integer | NO | Time in seconds |
| played_at | timestamptz | NO | Play timestamp |

---

## Leaderboard Tables

### custom_leaderboard
Leaderboard entries for custom puzzle sets.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| parentsetid | text | YES | Puzzle set ID (riddle-xxx) |
| userid | text | YES | Firebase UID |
| timetaken | integer | YES | Completion time (seconds) |
| score | integer | YES | Points earned |
| createdat | timestamptz | NO | Entry timestamp |

### puzzle_performances
Individual puzzle performance tracking.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_id | uuid | NO | Firebase UID |
| puzzle_type | varchar | YES | Puzzle type |
| difficulty | varchar | YES | Difficulty level |
| is_correct | boolean | YES | Solved correctly |
| time_spent_seconds | numeric | NO | Time taken |
| hints_used | integer | NO | Hints used |
| score | integer | NO | Points earned |
| session_id | varchar | NO | Session reference |
| additional_data | jsonb | NO | Extra metrics |
| created_at | timestamptz | NO | Record creation |

---

## Analytics Tables

### analytics_events
User event tracking.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_id | uuid | NO | Firebase UID |
| event_name | varchar | YES | Event name |
| event_parameters | jsonb | NO | Event data |
| session_id | varchar | NO | Session reference |
| app_version | varchar | NO | App version |
| device_info | jsonb | NO | Device details |
| user_properties | jsonb | NO | User properties |
| created_at | timestamptz | NO | Event time |

### ltv_user_data
Lifetime value calculations per user.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| user_id | text | YES | Firebase UID (PK) |
| tier | text | YES | LTV tier |
| ltv | numeric | YES | Calculated LTV |
| actual_revenue | numeric | NO | Actual revenue |
| predicted_revenue | numeric | NO | Predicted revenue |
| recommended_bid | numeric | NO | Ad bid suggestion |
| engagement_score | integer | NO | Engagement metric |
| retention_score | integer | NO | Retention metric |
| progression_score | integer | NO | Progress metric |
| total_sessions | integer | NO | Session count |
| total_puzzles_solved | integer | NO | Puzzles completed |
| days_since_last_activity | integer | NO | Days inactive |
| is_active | boolean | NO | Active user flag |
| is_churned | boolean | NO | Churned flag |
| is_dormant | boolean | NO | Dormant flag |
| should_target | boolean | NO | Ad targeting flag |
| conversion_intent | text | NO | Conversion likelihood |
| segments | text[] | NO | User segments |
| metrics | jsonb | NO | Additional metrics |
| calculated_at | timestamptz | YES | Calculation time |
| updated_at | timestamptz | YES | Last update |

---

## Notification Tables

### notification_configs
Push notification campaign configurations.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | integer | YES | Primary key |
| name | varchar | YES | Campaign name |
| title | varchar | YES | Notification title |
| message | text | YES | Notification body |
| schedule_type | varchar | YES | daily/weekly/one-time |
| target_time | time | YES | Send time |
| rules | jsonb | YES | Targeting rules |
| priority | varchar | YES | high/normal/low |
| is_active | boolean | YES | Active flag |
| created_by | varchar | YES | Creator email |
| success_count | integer | YES | Successful sends |
| failure_count | integer | YES | Failed sends |
| total_sent | integer | YES | Total sent |
| last_sent_at | timestamptz | NO | Last send time |
| next_scheduled_at | timestamptz | NO | Next send time |
| created_at | timestamptz | YES | Creation time |
| updated_at | timestamptz | YES | Last update |

### notification_logs
Individual notification delivery logs.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_email | text | NO | Recipient email |
| notification_type | text | NO | Notification type |
| title | text | NO | Notification title |
| body | text | NO | Notification body |
| data | jsonb | NO | Payload data |
| success | boolean | NO | Delivery success |
| error_message | text | NO | Error details |
| fcm_response | jsonb | NO | FCM response |
| sent_at | timestamptz | NO | Send timestamp |

---

## Word/Dictionary Tables

### common_words
Main word dictionary for puzzle generation.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| word | text | YES | The word (PK) |
| part_of_speech | text | NO | noun/verb/adj/etc. |
| definition | text | NO | Word definition |
| has_definition | boolean | NO | Has definition flag |
| difficulty | text | NO | easy/medium/hard |
| frequency_rank | integer | NO | Usage frequency |
| syllable_count | integer | NO | Syllables |
| crossword_score | float8 | NO | Crossword suitability |
| overlap_score | integer | NO | Letter overlap score |
| pattern_score | float8 | NO | Pattern matching |
| ... | ... | ... | (30+ additional columns for NLP features) |

### word_antonyms
Antonym pairs for word puzzles.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | integer | YES | Primary key |
| word_1 | varchar | YES | First word |
| word_2 | varchar | YES | Antonym |
| confidence_score | numeric | YES | Match confidence |
| source | varchar | YES | Data source |
| semantic_distance | numeric | NO | Semantic difference |
| puzzle_quality_score | numeric | NO | Puzzle suitability |
| manual_verified | boolean | NO | Human verified |
| validation_status | varchar | NO | Validation state |
| usage_examples | text | NO | Example sentences |
| created_at | timestamp | NO | Creation time |
| updated_at | timestamp | NO | Last update |

### word_frequency
Word frequency statistics.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | integer | YES | Primary key |
| word | varchar | YES | The word |
| frequency | integer | YES | Frequency count |
| length | integer | YES | Word length |
| part_of_speech | text | NO | Word type |
| definition | text | NO | Definition |
| has_definition | boolean | NO | Has definition |
| is_common_word | boolean | NO | Common word flag |
| overlap_score | integer | NO | Letter overlap |
| created_at | timestamp | NO | Creation time |
| updated_at | timestamp | NO | Last update |

---

## System Tables

### system_config
Application configuration key-value store.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| key | varchar | YES | Config key (PK) |
| value | jsonb | YES | Config value |
| description | text | NO | Description |
| updated_by | varchar | NO | Last updater |
| updated_at | timestamp | NO | Last update |

### system_alerts
System monitoring and alerts.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| category | text | YES | Alert category |
| severity | text | YES | critical/warning/info |
| operation | text | YES | Operation that failed |
| puzzle_type | text | NO | Related puzzle type |
| difficulty | text | NO | Related difficulty |
| puzzle_id | text | NO | Related puzzle ID |
| error_message | text | NO | Error details |
| metadata | jsonb | NO | Additional data |
| resolved | boolean | NO | Resolution status |
| resolved_at | timestamptz | NO | Resolution time |
| resolved_by | text | NO | Who resolved |
| timestamp | timestamptz | YES | Alert time |

---

## Subscription/Trial Tables

### device_trials
Device-based trial tracking.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| device_id | text | YES | Device identifier |
| device_fingerprint | text | NO | Browser fingerprint |
| first_user_id | uuid | NO | First user on device |
| trial_start | timestamptz | YES | Trial start |
| trial_end | timestamptz | YES | Trial end |
| trial_used | boolean | NO | Trial consumed |
| platform | text | NO | ios/android/web |
| created_at | timestamptz | NO | Record creation |
| updated_at | timestamptz | NO | Last update |

### user_tiers
Subscription tier definitions.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| tier_name | varchar | YES | Tier name (PK) |
| description | text | NO | Tier description |
| daily_limit | integer | NO | Daily generation limit |
| monthly_limit | integer | NO | Monthly generation limit |
| price_monthly | numeric | NO | Monthly price |
| features | jsonb | NO | Feature flags |
| is_active | boolean | NO | Tier active |
| created_at | timestamp | NO | Creation time |

### subscription_metrics
Subscription event tracking.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_id | uuid | NO | Firebase UID |
| device_id | text | NO | Device ID |
| event_type | text | YES | Event type |
| platform | text | NO | Platform |
| source | text | NO | Attribution source |
| metadata | jsonb | NO | Event data |
| created_at | timestamptz | NO | Event time |

---

## Daily Puzzles

### daily_puzzles
Daily puzzle assignments per user.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| id | uuid | YES | Primary key |
| user_email | text | YES | User email |
| topic | text | YES | Puzzle topic |
| puzzle_set | jsonb | YES | Puzzle data |
| generation_date | date | YES | Date assigned |
| puzzle_count | integer | NO | Number of puzzles |
| status | text | NO | completed/pending |
| is_current | boolean | NO | Current day flag |
| generation_timestamp | timestamptz | NO | Generation time |
| created_at | timestamptz | NO | Record creation |
| updated_at | timestamptz | NO | Last update |

---

## Indexes (Important)

Key indexes for performance:
- `puzzles`: Index on `(type, difficulty)`, `puzzleid`
- `puzzle_path`: Index on `puzzleid`, `(type, difficulty)`
- `custom_leaderboard`: Index on `parentsetid`, `(parentsetid, score)`
- `user_notification_tokens`: Index on `user_email`, `fcm_token`
- `analytics_events`: Index on `user_id`, `event_name`, `created_at`

---

## Common Queries

### Get next puzzle in sequence
```sql
SELECT p.* FROM puzzles p
JOIN puzzle_path pp ON p.puzzleid = pp.nextpuzzleid
WHERE pp.puzzleid = 'current-puzzle-id';
```

### Get user's puzzle progress
```sql
SELECT puzzle_type, difficulty, COUNT(*) as solved
FROM puzzle_performances
WHERE user_id = 'user-id' AND is_correct = true
GROUP BY puzzle_type, difficulty;
```

### Get leaderboard for puzzle set
```sql
SELECT userid, score, timetaken, createdat
FROM custom_leaderboard
WHERE parentsetid = 'riddle-xxx'
ORDER BY score DESC, timetaken ASC
LIMIT 10;
```

### Get active notification tokens
```sql
SELECT fcm_token, platform
FROM user_notification_tokens
WHERE user_email = 'user@email.com'
AND is_active = true;
```
