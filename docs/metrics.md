# RiddleVerse metrics: events and how to read them weekly

All events are anonymous: a random per-install id, no email, no device id, no free text. They go to `POST /api/puzzle-events`
and land in `public.puzzle_events` (Supabase). Debug builds log them but do not send them unless the `debug_emit` pref is set.

## Event list

| Wanted event | Wire form (`event_type` / `funnel_step`) | Fires | Source |
|---|---|---|---|
| first_open | `funnel` / `first_open` | once per NEW install (installs older than 2 days at first run of the build are "legacy" and skip install steps) | `Funnel.kt` |
| puzzle_started | `start` (+ `first_puzzle_started` once) | every puzzle shown | `PuzzleEvents.puzzleShown` |
| puzzle_completed | `complete` / `fail` / `abandon` (+ `first_puzzle_completed` once) | every puzzle result | `PuzzleEvents.puzzleFinished` |
| daily_completed | `funnel` / `daily_completed` once per install, and `daily_solved` for EVERY solved daily (`score` = streak length, `days_since_install`) | on a first solve of that day's daily | `Funnel.dailySolved` |
| streak_day | `daily_solved.score` (every solve) plus `funnel` / `streak_3`, `streak_7` once | same | `Funnel.dailySolved` |
| notif_permission_granted | `funnel` / `notif_permission_granted` (and `notif_permission_denied`) | the Android 13+ dialog result, only after the player taps "Remind me" or the Settings switch | `Retention.kt` |
| reminder_enabled | `funnel` / `reminder_enabled` | reminder switched on | `Retention.kt` |
| welcome_back_shown | `funnel` / `welcome_back_shown` | the "Welcome back" card is shown (3+ days away) | `Retention.kt` |
| return_d1 / return_d7 | `app_open` with `days_since_install` = 1 / 7 (one per local day per install) | first foreground of the day | `Funnel.appOpen` |
| supporter_offer_shown / supporter_purchased | `funnel` | only once the remote flag is on and the product exists | `Supporter.kt` |

`return_d1` / `return_d7` are not separate events: they are derived from `app_open` rows (`days_since_install = 1` or `7`), which is
what the `retention_by_cohort` view in the funnel migration computes. One-time steps are reported once per install.

## Weekly view

Read-only queries: `docs/funnel-queries.sql` (D1/D7 by cohort, funnel conversion, DAU) and `docs/retention-queries.sql`
(daily completion rate, streak distribution, return after first solve, reminder funnel, win-back, supporter offer).

## Deploy order (important)

1. Owner applies `web/backend/supabase/migrations/20261101120000_funnel_events.sql` (not applied by this work; check with `\d public.puzzle_events`).
2. Deploy the web backend (allow-list for the new steps and `daily_solved`). The backend rejects a WHOLE batch if it contains a
   step or type it does not know, so the backend must be live BEFORE an app build that sends the new events is released.
3. Release the Android build.

Caveats: the ingest endpoint is unauthenticated and rate-limited, so small absolute numbers are noisy; staged rollouts mix old and
new builds; a legacy install reports `app_open` and the retention steps but never `first_open`.
