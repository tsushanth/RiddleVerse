// Telegram side of the public /play pages: the bot link, the Mini App context, and the score submission.
// The bot opens a game as a Telegram Mini App at /play/<slug>?src=telegram&tgctx=<signed context> (private chats) or via a
// direct link whose start_param IS that context (groups). The page reports only what happened (game, difficulty, time) with
// Telegram's signed initData; the server verifies it and computes the score. Free of imports and browser globals (testable).

export const BOT_USERNAME = 'Riddleverse_bot'
export const SCORE_ENDPOINT = '/api/telegram/score'
const CTX_RE = /^[pd]_[a-z]{2,24}_m?\d{1,20}_[0-9a-f]{20}$/

/** t.me link to the bot; with a slug it opens that game in the chat (/start g_<slug>). */
export function telegramBotUrl(slug) {
  return `https://t.me/${BOT_USERNAME}${slug ? `?start=g_${encodeURIComponent(slug)}` : ''}`
}

/** t.me share sheet with a playable link tagged src=share (the server counts the opens). */
export function telegramShareUrl(slug, text, origin = 'https://puzzleverseai.com') {
  const link = `${origin}/play/${slug}?src=share&utm_source=telegram&utm_medium=web_challenge&utm_campaign=${slug}`
  return `https://t.me/share/url?url=${encodeURIComponent(link)}&text=${encodeURIComponent(text)}`
}

/** { fromTelegram, ctx } from the page URL. ctx is the signed context (query tgctx, or the Mini App start param). */
export function parseTelegramContext(search = '', hash = '') {
  let q
  let h
  try { q = new URLSearchParams(search); h = new URLSearchParams(String(hash).replace(/^#/, '')) } catch { return { fromTelegram: false, ctx: null } }
  const candidate = q.get('tgctx') || q.get('tgWebAppStartParam') || h.get('tgWebAppStartParam')
  const ctx = candidate && CTX_RE.test(candidate) ? candidate : null
  const fromTelegram = q.get('src') === 'telegram' || h.has('tgWebAppData') || q.has('tgWebAppStartParam') || Boolean(ctx)
  return { fromTelegram, ctx }
}

/** The game a start param points at ("p_sudoku_m100_<sig>" -> "sudoku"), or null. The server still verifies the signature. */
export function slugFromContext(ctx) {
  return ctx && CTX_RE.test(ctx) ? ctx.split('_')[1] : null
}
export const kindFromContext = (ctx) => (ctx && ctx.startsWith('d_') ? 'd' : 'p')

export function buildScorePayload({ initData, slug, difficulty, durationMs, ctx }) {
  const body = { initData, slug, kind: kindFromContext(ctx), durationMs: Math.max(0, Math.round(Number(durationMs) || 0)) }
  if (['easy', 'medium', 'hard'].includes(difficulty)) body.difficulty = difficulty
  if (ctx) body.ctx = ctx
  return body
}

/** POSTs a finished round. Resolves to the server's JSON or null; never throws (a failed score must not break the game). */
export async function submitScore(env, args) {
  try {
    const initData = env.initData
    if (!initData) return null
    const res = await env.fetch(env.endpoint || SCORE_ENDPOINT, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(buildScorePayload({ ...args, initData })),
    })
    if (!res.ok) return null
    return await res.json()
  } catch {
    return null
  }
}
