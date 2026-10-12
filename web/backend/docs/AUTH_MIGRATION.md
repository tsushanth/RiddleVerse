# Auth migration: Firebase -> Supabase Auth (stage 1 + 2, additive)

## Design
Every existing Firebase UID stays the canonical user id (`canonical_uid`). All user tables
(`user_id`, `firebase_uid`, `creator_id`) and RevenueCat `logIn()` keep using it. Supabase Auth is an
additional identity provider mapped onto that id. Nothing is re-keyed.

## What exists
- Supabase project `uujjodxicvifmiwlimob` (RiddleVerse). Migration
  `supabase/migrations/20261011120000_auth_identity_map.sql` (rollback: `.rollback.sql` next to it), applied.
- Table `public.auth_identity_map(supabase_user_id uuid PK -> auth.users ON DELETE CASCADE, canonical_uid text UNIQUE, firebase_uid, provider, created_at, linked_at)`.
  RLS enabled, no policies, anon/authenticated revoked: service role only.
- `public.resolve_canonical_uid(uuid)`: SECURITY DEFINER, `search_path=''`, EXECUTE only for service_role.
- `auth/verifyIdentity.js`: `verifyBearerToken(header)` / `verifyToken(token)` -> `{uid, provider, raw}`.
  Firebase first (unchanged; original firebase-admin error is rethrown). Only if Firebase rejects AND the token's
  unverified `iss` equals `${SUPABASE_URL}/auth/v1` is Supabase tried (`auth.getUser(jwt)`, server-side validation),
  then mapped via `resolve_canonical_uid`. No mapping -> `IdentityError('unlinked', 403)`; uids are never minted.
  Positive mapping lookups are cached 60s; misses are not cached.
- Call sites switched: `middleware/payoutAuth.js` (accepts linked Supabase tokens; new 403 `Account not linked`
  only for unlinked Supabase tokens), `routes/auth.routes.js` (`/auth/google`), `routes/dashProxy.js`
  (Firebase-only on purpose: admin stays Google/Firebase). Firestore usage untouched. Other `admin.auth()` uses
  (`getUser`, `getUsers`, `getUserByEmail`) are Admin lookups, not token verification, and are unchanged.
- `POST /api/auth/link-supabase` (`routes/authLink.routes.js`): `Authorization: Bearer <Firebase ID token>`,
  body `{ "supabaseAccessToken": "..." }`. 201 created, 200 `alreadyLinked`, 409 on any conflicting link,
  401/400 on bad input, 503 if the service key is missing. Rate limited 20/min/IP.
- Tests: `npm run test:auth` (node:test, no new dependencies, fully mocked).

## Rollout stages
1. (this change) Table + dual verification + link endpoint, deployed dark: no client sends Supabase tokens yet.
2. Android/web sign-in swap: add Supabase sign-in; on first sign-in with an existing Firebase session call
   `link-supabase` with both tokens (lazy migration). Keep RevenueCat on the canonical uid.
3. New users: decide how canonical uid is assigned to users with no Firebase uid (must be done by the backend at
   link/signup time, not here). Not implemented.
4. Retire Firebase only after linked-user coverage is measured (rows in `auth_identity_map` vs active users),
   Firestore dependencies are gone, and the old app versions' traffic is negligible. Not before.

## Rollback
Code: revert the branch/deploy the previous image (Firebase path is byte-for-byte the old behaviour).
DB: run `20261011120000_auth_identity_map.rollback.sql` (drops the function and table only).

## Fly configuration needed before deploy (do not set from this branch)
- NEW: `SUPABASE_SERVICE_ROLE_KEY` (the running backend only has `SUPABASE_ANON_KEY`, which cannot read the
  RLS-protected map). Without it the Firebase path is unaffected; Supabase tokens and link-supabase return 503.
- Existing, must be present: `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
- Supabase dashboard: Auth providers to be enabled later at stage 2; nothing is required for stage 1.

## Known limits
- "Same human" at link time is attested only by possession of both valid tokens; no email cross-check.
- Supabase token revocation is honoured via `getUser` on every request (one network call per Supabase-authenticated request).
- `auth.users` in this project currently has 0 rows.
