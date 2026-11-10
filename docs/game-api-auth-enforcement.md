# Authenticating the game-creation, games, leaderboard and score routes

## Today

`/api/game-creation/*`, `/api/games/*`, `/api/leaderboard/*`, `/api/chat-scores/*` and `POST /api/game-score/:gameId` accept any
caller; the user is whatever `userId` the request body or query names. Shipped Android and iOS clients send no credential on
these routes (only the coin routes on iOS send a Firebase token). LLM-backed routes (generate, remix, tweak, customize,
difficulty/next-level generate) spend money per request.

## Server (this change)

`middleware/gameCreationGuard.js`, mounted in `server.js`:

- Always on: per-IP and per-key hourly limits and a global daily budget (estimated USD) on the spend-bearing routes; a per-IP
  per-minute write limit on games / leaderboard / score writes. All values are environment variables (see the file header).
- Optional identity: `Authorization: Bearer <Firebase or Supabase ID token>`. If present it must verify (401) and, when the body
  has `userId`, equal the verified uid (403). The verified uid is the rate-limit key. Without a token the key is the
  `X-Install-Id` header if sent, else the body `userId`.
- Enforce flag: `GAME_CREATION_REQUIRE_AUTH=true` makes a valid token mandatory for the spend-bearing POST routes. Reads (browse,
  get game) stay open. Default off, because every shipped client would be rejected.

## Rollout

1. Ship the client changes below in the next Android and iOS releases (header only; the server already accepts it).
2. Wait until the release is on 100 % and old versions are a small share of requests. Measure with a log line: count requests with
   and without `Authorization` on the guarded routes (add a counter before flipping).
3. Set `GAME_CREATION_REQUIRE_AUTH=true`. Older app versions then get 401 on creation routes: they need an update to create games
   (browsing and playing keep working). Decide the acceptable share of old versions before flipping.
4. Follow-up: stop trusting body `userId` on `/api/games`, `/api/leaderboard`, `/api/chat-scores`, `/api/game-score`
   (derive it from the verified uid) once step 3 is stable. This needs the same flag on those routers.

## Android change list

- `RetrofitClient.kt` (`HttpClientProvider.client`): add an OkHttp `Interceptor` that, for requests to `puzzleverseai.com` only,
  adds `Authorization: Bearer <token>` using `FirebaseAuth.getInstance().currentUser?.getIdToken(false)` (block with
  `Tasks.await` on the OkHttp thread; refresh once on a 401) and `X-Install-Id: <install_id>` (the id already stored by
  `PuzzleEvents.kt`). Guests (no Firebase user) send only `X-Install-Id`.
- Per-call sites that build their own `Request.Builder()` already go through `HttpClientProvider.client` (GamePreviewScreen,
  GameGenerationViewModel `newBuilder()` copies keep interceptors); verify the `newBuilder()` clients in
  `GameGenerationViewModel.kt:296` and `GamePreviewScreen.kt:411` still carry the interceptor.
- `CoinManager.kt` `recordPurchaseOnBackend`: it relies on the interceptor; also read the response, show the error and retry on
  `503 purchase_verification_unavailable` instead of always showing "Coins added!".
- Handle 401 from creation routes with a sign-in prompt (`GuestGate.requireAccount`) and 429 with the server message.
- Cover with a unit test of the interceptor (host filter, header presence for guest and signed-in).

## iOS change list

- Add one helper (for example `APIClient.authorizedRequest(url:)`) that sets `Authorization: Bearer` from
  `Auth.auth().currentUser?.getIDToken` (the pattern already in `CoinManager.swift:376-395`) and `X-Install-Id` from a stored UUID.
- Use it in: `GameGenerationManager.swift`, `GameTweakView.swift`, `GamePreviewView.swift`, `GameBrowseView.swift`,
  `GamePlayView.swift`, `GameCreationView.swift` (all call `puzzleverseai.com/api/game-creation|games|leaderboard`),
  plus `CoinManager.swift` `recordPurchase` and `CoinStorePaywallView.swift` (send the real StoreKit transaction id, not "").
- Handle 401/429 in the same places as Android.

## Not covered

Per-IP limits can hit users behind a shared NAT; the defaults are generous for that reason. Counters are per Fly machine and reset
on restart. The USD budget is an estimate from per-request cost settings, not billing data.
