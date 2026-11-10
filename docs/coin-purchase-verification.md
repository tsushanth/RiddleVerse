# Coin purchase verification

`POST /api/coins/purchase` credits a coin pack. Before this change it trusted a client-supplied transaction id.

## How the Android app buys

RevenueCat SDK purchase of a consumable (`com.kreativekoala.riddleverse.coins.{100,500,1200}`), then
`POST /api/coins/purchase {userId, productId, transactionId = Play order id, platform: "android"}` with no Authorization header
and no purchase token. The iOS client sends an empty `transactionId` and has always been rejected with 400.

## What the server does now

1. Coin amount comes from the server-side pack table only (never from the request).
2. `COIN_PURCHASE_VERIFY=enforce` (default, also for any unknown value): the Play order is looked up with the Android Publisher
   `orders.get` API (`services/playPurchaseVerifier.js`). Credit only if state is `PROCESSED`, the order has exactly one line item
   whose product id matches, quantity is 1, and (optionally) the order is not older than `COIN_PURCHASE_MAX_ORDER_AGE_HOURS`.
   Anything the verifier cannot decide (no credential, Google API error) returns `503 {retryable: true}` and credits nothing.
3. Idempotent on the order id (`add_purchased_coins` duplicate check) plus an in-process guard against concurrent claims.
4. `COIN_PURCHASE_VERIFY=interim`: explicit opt-in used only while the verifier credential is not working. No receipt check:
   Android only, order id must look like `GPA.xxxx-xxxx-xxxx-xxxxx`, and every credit is logged as `[Coins][ANOMALY] unverified_credit ...`.
   Second layer in BOTH modes: per-account limits (default 4 purchases and 3000 coins per rolling 24 h, read from `coin_transactions`),
   per-IP limit (20 claims/h) and a global ceiling (30000 coins/UTC day per machine). In enforce mode a limit that trips after
   verification credits nothing and the buyer can retry later (the order stays valid for `COIN_PURCHASE_MAX_ORDER_AGE_HOURS`).
5. Optional `Authorization: Bearer` (Firebase/Supabase token): when present it must verify and match `userId`.
   `COIN_PURCHASE_REQUIRE_AUTH=true` makes it mandatory once clients send it.
6. A creator's own spend never earns a creator share.
7. Payouts are held: `PAYOUTS_ENABLED` must be `true` for `/api/payouts/request` and for `eligible` in `/api/payouts/eligibility`.
   Held requests return 423 `payouts_under_review` and are logged as `[Payouts][HELD]`. Turn on only after the coin ledger has
   been reviewed, because balances can include coins credited in interim mode.

## Credential required for `enforce`

A Google service account that is (a) allowed to call the Android Publisher API (API enabled in the account's Google Cloud project)
and (b) invited in Play Console > Users and permissions with access to this app and the permission
"View financial data, orders and cancellation survey responses". The server looks for the JSON in, in order,
`PLAY_VERIFIER_SERVICE_ACCOUNT`, `GCS_SERVICE_ACCOUNT_KEY`, `FIREBASE_SERVICE_ACCOUNT_KEY`. Use a dedicated, app-scoped account;
do not reuse an account that can publish releases.

Then set `COIN_PURCHASE_VERIFY=enforce` (or remove the variable) and check Fly logs for `verifier_not_configured` / `verifier_unavailable`.

## Behaviour for installed app versions

All released Android versions send the same request, so none needs an update. In `enforce` mode a purchase whose order cannot be
verified shows the old client's generic "Coins added!" message without changing the balance (the client does not read the error);
the order stays retryable on the server side. The next app release should read `success`, show the error, and retry.

## Known limits

- `orders.get` proves a paid order exists, not who bought it. The idempotency key plus the optional bearer check bind it to an
  account; strict binding needs the client to send its ID token (see game-creation auth design).
- Refunds after crediting are not clawed back (no refund notification handler yet).
- `coin_transactions` now has a partial unique index on `iap_transaction_id` (where not null), see
  `web/backend/migrations/coin_transactions_iap_unique.sql`. The function's check-then-insert race now fails the second insert
  (the function rolls back, the handler reports "already processed").
- The database function `spend_coins` credits the creator 70 % of a spend (`virtual_currency.sql`), while the app and `COIN_PACKS`
  settings say 55 % (`CREATOR_SHARE_PCT`). Not changed here: payouts are held and the earnings wording was removed, so the intended
  split is a product decision to be made before payouts are turned on.

## Manual positive test (needed once: no real order was available when this was built)

Use a Play license tester account (Play Console > Settings > License testing) on a device with the release or internal-test build,
signed in to the app with a normal account, and buy the smallest pack (100 coins).
1. Before: note the balance (`GET /api/coins/balance?userId=<uid>`).
2. Buy the pack. Expected in the app: balance +100.
3. Fly logs (`fly logs -a quiz-web-frontend`) should show `[Coins] Purchase: xxxxxx… buying 100 coins ... [enforce]` and then
   `Purchase complete`, with no `[Coins][ANOMALY]` line. A `verifier_unavailable` or `purchase_not_verified` line means the order
   lookup failed: check the `detail`/`code` (`play api 401/403` = service account permissions not active yet; `not_found` = the
   order id format Play returns for test purchases differs; `product_mismatch` = product ids differ).
4. Repeat the same request with the same order id (replay): it must answer "already processed" and the balance must not change.
5. If step 3 fails only for test purchases, set `COIN_PURCHASE_VERIFY=interim` to roll back, and record what `orders.get`
   returned for that order (status and state only).
