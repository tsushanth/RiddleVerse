# Game creation jobs (submit + poll)

Restores `POST /api/game-creation/jobs/generate`, `GET /jobs/:jobId`, `DELETE /jobs/:jobId` for the Android client
(app versions 173/174/176 call them). The original implementation (fe97d04) proxied a `/jobs/*` API on the
claude-forge-worker, which does not exist in this repo and was reverted with the rest of that commit. This version has no worker dependency: it calls the
existing sync `POST /api/game-creation/generate` on the same machine (127.0.0.1:$PORT), keeps reading the SSE stream in
the background and exposes the latest state to pollers. Paywall, rate limit, coin spend, Supabase save, thumbnail and
analytics all stay in the sync handler (one source of truth). Gate errors (400/402/403/429) are returned to the submit call unchanged.

## Risks
- Job state is in memory, per machine, lost on restart/stop. fly.toml has `min_machines_running = 0` and
  `auto_stop_machines = "stop"`: while a client polls (every 3 s) the machine has traffic and stays up, but a deploy mid-build loses the job
  (the game may still be saved by the sync handler: the SSE read continues until the machine dies).
- With more than one machine a poll can land on a different machine and get 404 (client retries up to 15 times, then reports "Lost contact").
  Check `fly scale show -a quiz-web-frontend`; if count > 1 either scale to 1 for this app or add Fly replay/sticky routing before relying on polling.
- The sync handler has a 10 min worker timeout; very long builds fail as before.
- The Android client now also falls back to the sync SSE path when the jobs route answers 404/405, so this branch is an improvement, not a prerequisite.

## Deploy checklist (do NOT deploy from a dirty tree)
1. `cd web/backend && npm test` (7 new tests in test/gameCreationJobs.test.js, no network).
2. `flyctl secrets list -a quiz-web-frontend` (read only; no secret changes are needed).
3. `flyctl scale show -a quiz-web-frontend` and confirm machine count (see Risks).
4. Merge to main, deploy from a clean worktree of origin/main: `cd web && flyctl deploy`.
5. Smoke: `curl -s -XPOST https://<app>/api/game-creation/jobs/generate -H 'content-type: application/json' -d '{}'` must return 400 "Game description is required" (not 404).
6. Real run from the app (Games > Create > Create a Game) with a test user; poll shows progress, ends with a gameId.
7. Rollback: redeploy the previous release (`flyctl releases -a quiz-web-frontend`, `flyctl deploy --image <previous>`).
