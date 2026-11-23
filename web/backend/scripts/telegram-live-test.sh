#!/usr/bin/env bash
# Smoke test for the live Telegram bot. Read-only except the optional step 7 (one /start reply to YOUR OWN chat).
#   RIDDLEVERSE_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... [TEST_CHAT_ID=<your numeric Telegram id>] ./telegram-live-test.sh
# Never prints the token or the secret.
set -u
BASE="${BASE:-https://puzzleverseai.com}"
TOKEN="${RIDDLEVERSE_BOT_TOKEN:-}"
SECRET="${TELEGRAM_WEBHOOK_SECRET:-}"
pass=0; fail=0
ok()   { echo "PASS  $1"; pass=$((pass+1)); }
bad()  { echo "FAIL  $1"; fail=$((fail+1)); }
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 60 "$@"; }

[ -n "$TOKEN" ] || { echo "set RIDDLEVERSE_BOT_TOKEN"; exit 2; }
[ -n "$SECRET" ] || { echo "set TELEGRAM_WEBHOOK_SECRET"; exit 2; }

echo "1. getMe (read-only)"
curl -s --max-time 20 "https://api.telegram.org/bot${TOKEN}/getMe" | grep -q '"username":"Riddleverse_bot"' && ok "bot is @Riddleverse_bot" || bad "getMe did not return @Riddleverse_bot"

echo "2. getWebhookInfo"
INFO=$(curl -s --max-time 20 "https://api.telegram.org/bot${TOKEN}/getWebhookInfo")
echo "$INFO" | grep -q "\"url\":\"${BASE}/api/telegram/webhook\"" && ok "webhook url set" || bad "webhook url is not ${BASE}/api/telegram/webhook (run scripts/telegramSetup.mjs --apply)"
echo "$INFO" | grep -q 'last_error_message' && bad "Telegram reports a delivery error: $(echo "$INFO" | grep -o '"last_error_message":"[^"]*"')" || ok "no delivery error reported"

echo "3. webhook rejects a request without the secret (expect 401)"
[ "$(code -X POST "$BASE/api/telegram/webhook" -H 'content-type: application/json' -d '{"update_id":1}')" = "401" ] && ok "401 without secret" || bad "webhook did not answer 401 without the secret"

echo "4. webhook rejects a wrong secret (expect 401)"
[ "$(code -X POST "$BASE/api/telegram/webhook" -H 'content-type: application/json' -H 'X-Telegram-Bot-Api-Secret-Token: wrong' -d '{"update_id":1}')" = "401" ] && ok "401 with wrong secret" || bad "wrong secret was not rejected"

echo "5. link page and redeem endpoint"
FAKE=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
[ "$(code "$BASE/app/link?code=$FAKE" -A 'Mozilla/5.0 (Linux; Android 15)')" = "200" ] && ok "/app/link serves the open-app page" || bad "/app/link did not return 200"
[ "$(code -X POST "$BASE/api/telegram/link/redeem" -H 'content-type: application/json' -d "{\"code\":\"$FAKE\"}")" = "410" ] && ok "unknown code is 410" || bad "redeem with an unknown code was not 410"

echo "6. score endpoint rejects unsigned data (expect 401)"
[ "$(code -X POST "$BASE/api/telegram/score" -H 'content-type: application/json' -d '{"initData":"hash=00","slug":"sudoku","durationMs":60000}')" = "401" ] && ok "401 for bad initData" || bad "score endpoint accepted bad initData"

echo "7. (optional) one real update to YOUR chat"
if [ -n "${TEST_CHAT_ID:-}" ]; then
  read -r -p "Send a /start reply to chat ${TEST_CHAT_ID} now? Type yes: " A
  if [ "$A" = "yes" ]; then
    UID_=$(date +%s)
    R=$(code -X POST "$BASE/api/telegram/webhook" -H 'content-type: application/json' -H "X-Telegram-Bot-Api-Secret-Token: ${SECRET}" \
      -d "{\"update_id\":${UID_},\"message\":{\"message_id\":1,\"text\":\"/start\",\"from\":{\"id\":${TEST_CHAT_ID},\"first_name\":\"Test\",\"language_code\":\"en\"},\"chat\":{\"id\":${TEST_CHAT_ID},\"type\":\"private\"}}}")
    [ "$R" = "200" ] && ok "webhook acked 200 (check Telegram: the welcome message should arrive within ~20 s on a cold machine)" || bad "authorized update was not acked 200 (got $R)"
  else echo "skipped"; fi
else echo "skipped (set TEST_CHAT_ID to your own Telegram id to try it)"; fi

echo
echo "Manual checks (about 10 minutes, on your phone):"
cat <<'M'
 a. Telegram > @Riddleverse_bot > /start : welcome in your language with the menu buttons.
 b. /language > pick Deutsch > the menu answers in German; /help shows German.
 c. /daily : the game matches the app's daily puzzle (Today's puzzle card in the app, same number and game).
 d. Tap "Play now" (opens inside Telegram), solve the puzzle: a "<name> scored N" message arrives in the chat.
 e. Add the bot to a test group, /play sudoku, tap Play (opens the page; scores in groups need the direct-link Mini App, see docs/telegram-and-sharing.md).
 f. /link : tap the button on an Android phone with the app: confirm dialog > "Telegram connected". Tap the same button again: "expired or already used".
 g. Reply STOP-style: block the bot, unblock, /start works again. /remind then /remind again toggles.
 h. Android: Home banner, a result screen card, Settings > Telegram rows open the bot; Share a game > the link opens the web page (or the app when installed).
M
echo
echo "result: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
