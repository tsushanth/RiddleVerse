import test from 'node:test'
import assert from 'node:assert/strict'
import {
  telegramBotUrl, telegramShareUrl, parseTelegramContext, slugFromContext, kindFromContext, buildScorePayload, submitScore, BOT_USERNAME,
} from '../../src/utils/telegramPlay.js'
import { pickLang, format, isRtl } from '../../src/utils/playI18n.js'
import { createRoundTracker, createWebPlayAnalytics } from '../../src/utils/webPlayAnalytics.js'

const CTX = 'p_sudoku_m1001234_0123456789abcdef0123'

test('bot link: plain, and with a game (/start g_<slug>)', () => {
  assert.equal(telegramBotUrl(), `https://t.me/${BOT_USERNAME}`)
  assert.equal(BOT_USERNAME, 'Riddleverse_bot')
  assert.equal(telegramBotUrl('sudoku'), 'https://t.me/Riddleverse_bot?start=g_sudoku')
})

test('share link carries src=share and the utm tags, and is URL-encoded inside the t.me link', () => {
  const u = new URL(telegramShareUrl('sudoku', 'Beat me & try it'))
  assert.equal(u.origin + u.pathname, 'https://t.me/share/url')
  const inner = new URL(u.searchParams.get('url'))
  assert.equal(inner.pathname, '/play/sudoku')
  assert.equal(inner.searchParams.get('src'), 'share')
  assert.equal(inner.searchParams.get('utm_source'), 'telegram')
  assert.equal(u.searchParams.get('text'), 'Beat me & try it')
})

test('context: private Mini App (query), group direct link (start param in query or hash), plain visit, junk', () => {
  assert.deepEqual(parseTelegramContext(`?src=telegram&tgctx=${CTX}`, ''), { fromTelegram: true, ctx: CTX })
  assert.deepEqual(parseTelegramContext(`?tgWebAppStartParam=${CTX}`, ''), { fromTelegram: true, ctx: CTX })
  assert.deepEqual(parseTelegramContext('', `#tgWebAppData=x&tgWebAppStartParam=${CTX}`), { fromTelegram: true, ctx: CTX })
  assert.deepEqual(parseTelegramContext('?src=telegram', ''), { fromTelegram: true, ctx: null })
  assert.deepEqual(parseTelegramContext('', ''), { fromTelegram: false, ctx: null })
  assert.deepEqual(parseTelegramContext('?tgctx=<script>', ''), { fromTelegram: false, ctx: null })
  assert.equal(slugFromContext(CTX), 'sudoku')
  assert.equal(slugFromContext('nope'), null)
  assert.equal(kindFromContext(CTX), 'p')
  assert.equal(kindFromContext('d_sudoku_42_0123456789abcdef0123'), 'd')
})

test('score payload sends what happened, never a score', () => {
  const b = buildScorePayload({ initData: 'i', slug: 'sudoku', difficulty: 'hard', durationMs: 12345.6, ctx: CTX })
  assert.deepEqual(b, { initData: 'i', slug: 'sudoku', kind: 'p', durationMs: 12346, difficulty: 'hard', ctx: CTX })
  assert.ok(!('score' in b))
  assert.ok(!('difficulty' in buildScorePayload({ initData: 'i', slug: 'x', difficulty: 'impossible', durationMs: 1 })))
  assert.equal(buildScorePayload({ initData: 'i', slug: 'x', durationMs: -5 }).durationMs, 0)
})

test('submitScore: posts JSON, returns the reply, and never throws', async () => {
  const calls = []
  const ok = await submitScore({ initData: 'signed', fetch: async (u, i) => { calls.push([u, i]); return { ok: true, json: async () => ({ ok: true, score: 250 }) } } }, { slug: 'sudoku', durationMs: 5000 })
  assert.equal(ok.score, 250)
  assert.equal(calls[0][0], '/api/telegram/score')
  assert.equal(JSON.parse(calls[0][1].body).initData, 'signed')
  assert.equal(await submitScore({ initData: '', fetch: async () => { throw new Error('no') } }, { slug: 'x' }), null)
  assert.equal(await submitScore({ initData: 's', fetch: async () => { throw new Error('offline') } }, { slug: 'x' }), null)
  assert.equal(await submitScore({ initData: 's', fetch: async () => ({ ok: false }) }, { slug: 'x' }), null)
})

test('language pick: ?lang first, then browser languages, aliases, regions, fallback to en', () => {
  const sup = ['en', 'de', 'pt', 'zh', 'id', 'ar']
  assert.equal(pickLang(['de'], sup), 'de')
  assert.equal(pickLang([null, 'fr-FR', 'de-AT'], sup), 'de')
  assert.equal(pickLang(['pt-BR'], sup), 'pt')
  assert.equal(pickLang(['zh-Hant-TW'], sup), 'zh')
  assert.equal(pickLang(['in'], sup), 'id')
  assert.equal(pickLang(['xx', 'yy'], sup), 'en')
  assert.equal(pickLang([], sup), 'en')
  assert.equal(pickLang(undefined, sup), 'en')
  assert.equal(isRtl('ar'), true)
  assert.equal(isRtl('de'), false)
})

test('format: placeholders, English fallback, key fallback', () => {
  assert.equal(format({ a: 'x {n}' }, { a: 'y', b: 'B' }, 'a', { n: 3 }), 'x 3')
  assert.equal(format({}, { b: 'B' }, 'b'), 'B')
  assert.equal(format({}, {}, 'zzz'), 'zzz')
})

test('round tracker returns the finished round (for the Telegram score) only for a counted solve', () => {
  const posts = []
  const analytics = createWebPlayAnalytics({
    navigator: {}, window: {}, localStorage: { getItem: () => null, setItem() {} }, sessionStorage: { getItem: () => null, setItem() {} },
    fetch: async (u, i) => { posts.push(JSON.parse(i.body)) }, uuid: () => 'id', now: () => 1000, endpoint: '/e',
  })
  let t = 1000
  const tr = createRoundTracker(analytics, 'sudoku', () => t)
  assert.equal(tr.answered(true, 'easy'), undefined, 'no start yet')
  tr.interact('easy')
  t = 31000
  assert.equal(tr.answered(false, 'easy'), undefined, 'wrong answer')
  assert.deepEqual(tr.answered(true, 'easy'), { durationMs: 30000, difficulty: 'easy' })
  assert.equal(tr.answered(true, 'easy'), undefined, 'one solve counts once')
})
