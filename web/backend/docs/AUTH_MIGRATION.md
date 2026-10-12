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
- `POST /api/auth/supabase-exchange` (stage 2b, `routes/authLink.routes.js`): `Authorization: Bearer <Supabase access token>`,
  body ignored. Verifies the token server-side (`auth.getUser`), looks up `auth_identity_map` by `supabase_user_id`;
  an existing row's `canonical_uid` is used as-is (including a mapped legacy Firebase uid), otherwise a NEW mapping
  `sb_<supabase_user_id>` (provider `supabase`, `firebase_uid` null) is inserted. Then returns
  `200 { firebaseCustomToken, canonicalUid, isNew }` where the token is `admin.auth().createCustomToken(canonicalUid, {provider:'supabase'})`.
  The client calls `signInWithCustomToken`, so `FirebaseAuth.currentUser.uid === canonicalUid` and ~200 call sites are unchanged.
  Errors: 401 `{error:'invalid_token'}` (missing/forged/expired), 503 `{error:'auth_bridge_unavailable'}` (service key,
  Supabase config or Firebase admin missing), 429 (30/min/IP), 500 `{error:'exchange_failed'}`. Idempotent; never re-keys an
  existing uid, never merges by email, never writes user data (only inserts a map row).
- Tests: `npm run test:auth` (node:test, no new dependencies, fully mocked).

## Rollout stages
1. (this change) Table + dual verification + link endpoint, deployed dark: no client sends Supabase tokens yet.
2. Android/web sign-in swap: add Supabase sign-in; on first sign-in with an existing Firebase session call
   `link-supabase` with both tokens (lazy migration). Keep RevenueCat on the canonical uid.
3. New users: `supabase-exchange` assigns `sb_<supabase_user_id>` to Supabase users with no mapping. Caveat: a
   returning user who signs in with Supabase (email+password) but was never linked gets a NEW account, not their old
   Firebase data; existing users must be linked via `link-supabase` while still signed in with Firebase.
4. Retire Firebase only after linked-user coverage is measured (rows in `auth_identity_map` vs active users),
   Firestore dependencies are gone, and the old app versions' traffic is negligible. Not before.

## Rollback
Code: revert the branch/deploy the previous image (Firebase path is byte-for-byte the old behaviour).
DB: run `20261011120000_auth_identity_map.rollback.sql` (drops the function and table only).

## Fly configuration (do not set from this branch)
- Present on Fly (names verified with `flyctl secrets list`): `FIREBASE_SERVICE_ACCOUNT_KEY`, `SUPABASE_ANON_KEY`,
  `SUPABASE_SERVICE_KEY`, `SUPABASE_URL`. The code reads `SUPABASE_SERVICE_KEY` (and also accepts
  `SUPABASE_SERVICE_ROLE_KEY` as an alias). No new secret is needed. (An earlier version of this note said only the
  anon key existed; that was wrong.)
- Supabase dashboard: enable Email provider for the exchange flow; decide on email confirmation (unconfirmed users still
  get valid access tokens only if confirmation is off).

## Deploy checklist
1. Merge branch; confirm `20261011120000_auth_identity_map.sql` is applied (it is).
2. `npm run test:auth` and `node --check server.js` from `web/backend`.
3. `flyctl secrets list -a <app>` shows the four names above (no change needed).
4. Deploy from a clean worktree. Smoke: `POST /api/auth/supabase-exchange` with no header -> 401 `invalid_token`;
   with a real test user's access token -> 200 and `signInWithCustomToken` works; repeat -> same `canonicalUid`, `isNew:false`.
5. Check the service account has the `iam.serviceAccounts.signBlob` / Token Creator permission if `createCustomToken`
   fails with a permission error (service-account JSON keys sign locally and normally do not need it).

## Rollback for supabase-exchange
Nothing to roll back except redeploying the previous image. Rows already inserted are inert (table has its own
rollback SQL; do not drop it if any `sb_` users exist, as it is their only identity record).

## Known limits
- "Same human" at link time is attested only by possession of both valid tokens; no email cross-check.
- Supabase token revocation is honoured via `getUser` on every request (one network call per Supabase-authenticated request).
- `auth.users` in this project currently has 0 rows.
