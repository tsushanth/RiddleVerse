# RiddleVerse metrics: events and how to read them weekly

All events are anonymous: a random per-install id, no email, no device id, no free text. They go to `POST /api/puzzle-events`
and land in `public.puzzle_events` (Supabase). Debug builds log them but do not send them unless the `debug_emit` pref is set.

## Event list

| Wanted event | Wire form (`event_type` / `funnel_step`) | Fires | Source |
|---|---|---|---|
| first_open | `funnel` / `first_open` | once per NEW install (installs older than 2 days at first run of the build are "legacy" and skip install steps) | `Funnel.kt` |
| puzzle_started | `start` (+ `first_puzzle_started` once) | every puzzle shown | `PuzzleEvents.puzzleShown` |
| puzzle_completed | `complete` / `fail` / `abandon` (+ `first_puzzle_completed` once) | every puzzle result | `PuzzleEvents.puzzleFinished` |
| starter puzzle | `puzzle_type = 'starter'`: `start` / `complete` / `abandon` on Android, `web_play_view` / `web_play_start` / `web_play_complete` on web | the first puzzle a new player meets (builds after 188; web from 2026-11-26) | `StarterPuzzle.kt`, `utils/starterPuzzle.js` |
| reminder push opened | `deeplink_open` with `channel = 'push_reminder'` | a tap on the server reminder (builds after 188 only) | `MainActivity.kt` |
| automated traffic | column `traffic`: `testlab` / `other` / null | set by the server at ingest, from 2026-11-26 | `services/trafficClass.js` |
| daily_completed | `funnel` / `daily_completed` once per install, and `daily_solved` for EVERY solved daily (`score` = streak length, `days_since_install`) | on a first solve of that day's daily | `Funnel.dailySolved` |
| streak_day | `daily_solved.score` (every solve) plus `funnel` / `streak_3`, `streak_7` once | same | `Funnel.dailySolved` |
| notif_permission_granted | `funnel` / `notif_permission_granted` (and `notif_permission_denied`) | the Android 13+ dialog result, only after the player taps "Remind me" (offered after a solved puzzle) or the Settings switch. Builds after 188 no longer ask at launch | `Retention.kt` |
| reminder_enabled | `funnel` / `reminder_enabled` | reminder switched on | `Retention.kt` |
| welcome_back_shown | `funnel` / `welcome_back_shown` | the "Welcome back" card is shown (3+ days away) | `Retention.kt` |
| return_d1 / return_d7 | `app_open` with `days_since_install` = 1 / 7 (one per local day per install) | first foreground of the day | `Funnel.appOpen` |
| supporter_offer_shown / supporter_purchased | `funnel` | only once the remote flag is on and the product exists | `Supporter.kt` |

`return_d1` / `return_d7` are not separate events: they are derived from `app_open` rows (`days_since_install = 1` or `7`), which is
what the `retention_by_cohort` view in the funnel migration computes. One-time steps are reported once per install.

`first_puzzle_completed` in builds up to 188 is also sent when the first puzzle was failed or skipped; later builds send it
only after a solve.

## Next-day return

The goal measure: of new installs, the share with an `app_open` on the next calendar day. Query 1 of
`docs/next-day-return.sql`.

Most "new installs" are not people. Release-time crawlers sign in, open the first tile and leave after a second. The
query splits them off by the `traffic` label (rows from 2026-11-26) and, for older rows, by two heuristics (three or more
first opens of one version within an hour; first tap 35 to 65 seconds after first open). Both are inferences.

Baseline, 2026-11-26 (installs of the last 30 days that are old enough):

| Cohort | Eligible | Started a puzzle | Completed one | Back next day | Back any later day |
|---|---|---|---|---|---|
| All new installs | 107 | 79 | 15 | 0 | 3 |
| Likely people (inferred) | 28 | 15 | 5 | 0 | 3 |
| Looks automated (inferred) | 79 | 64 | 10 | 0 | 0 |

Among the likely people the first puzzle was an anagram for 11 of 15 starters, and 1 of those 11 solved it. All 32
installs that met Trail first are in the automated group. With about one likely person a day, a change in this measure
takes weeks to show.

## New-player reminder push (OFF)

`web/backend/services/newPlayerReminder.js`. One push a day to signed-in Android players with a young account who did
not open the app today. Text is a draft in `REMINDER_TEXT` and needs the owner's approval before the switch is set.

| Setting (Fly env) | Default | Limit |
|---|---|---|
| `NEW_PLAYER_REMINDER_ENABLED` | unset (off) | `true` turns it on |
| `NEW_PLAYER_REMINDER_DAILY_CAP` | 20 | 200 |
| `NEW_PLAYER_REMINDER_NEW_DAYS` (account age) | 7 | 30 |
| `NEW_PLAYER_REMINDER_HOUR_UTC` (3 hour window) | 15 | 21 |
| `NEW_PLAYER_REMINDER_MAX_PER_PLAYER` | 3 | 7 |

Preview without sending or writing: `node scripts/newPlayerReminderPlan.mjs` on the server (prints counts only).
Firestore reads per run: 0. Supabase: at most 6 reads, then 1 insert and 1 update for the run and for each push (at the
default cap of 20: 42 writes). Firebase Auth: at most 10 batch lookups. No duplicates: fixed `notification_logs` ids for the run and for each person per day. Opt-out:
a `user_notification_preferences` row with `daily_notifications` or `puzzle_reminders` false (builds after 188 write
one when the player answers the reminder question), an inactive token, or notifications blocked on the phone. Machines
stop when idle, so a day with no running machine inside the window sends nothing.

## Weekly view

Read-only queries: `docs/funnel-queries.sql` (D1/D7 by cohort, funnel conversion, DAU) and `docs/retention-queries.sql`
(daily completion rate, streak distribution, return after first solve, reminder funnel, win-back, supporter offer).

Telegram bot, linking, sharing and web `/play` usage is a separate table with its own queries: `docs/channels-metrics.md`.

## Deploy order (important)

1. Owner applies `web/backend/supabase/migrations/20261101120000_funnel_events.sql` (not applied by this work; check with `\d public.puzzle_events`).
2. Deploy the web backend (allow-list for the new steps and `daily_solved`). The backend rejects a WHOLE batch if it contains a
   step or type it does not know, so the backend must be live BEFORE an app build that sends the new events is released.
3. Release the Android build.

Caveats: the ingest endpoint is unauthenticated and rate-limited, so small absolute numbers are noisy; staged rollouts mix old and
new builds; a legacy install reports `app_open` and the retention steps but never `first_open`.
