import test from 'node:test';
import assert from 'node:assert/strict';
import { validatePayload, toRows } from '../services/puzzleMonitoring.js';
import { pageHitRow, createPageHitRecorder } from '../services/pageHits.js';
import { createLimiter } from '../services/puzzleMonitoring.js';

const NOW = Date.UTC(2026, 10, 22, 12, 0, 0);
const body = (events, platform = 'android') => ({ installId: 'abc', platform, appVersion: '186', events });

test('share_sent / deeplink_open / telegram_cta are accepted with a whitelisted channel label', () => {
    const v = validatePayload(body([
        { type: 'share_sent', puzzleType: 'sudoku', channel: 'telegram' },
        { type: 'deeplink_open', puzzleType: 'app', channel: 'share' },
        { type: 'telegram_cta', puzzleType: 'app', channel: 'banner' },
    ]), NOW);
    assert.ok(v);
    assert.deepEqual(v.events.map((e) => [e.event_type, e.puzzle_type, e.channel]), [['share_sent', 'sudoku', 'telegram'], ['deeplink_open', 'app', 'share'], ['telegram_cta', 'app', 'banner']]);
    const rows = toRows(v, null);
    assert.equal(rows[0].user_id, null);
    assert.equal(rows[0].channel, 'telegram');
});

test('channel labels that are not [a-z0-9_]{1,24} are dropped, not stored', () => {
    const v = validatePayload(body([{ type: 'share_sent', puzzleType: 'sudoku', channel: 'Tele gram; drop table' }]), NOW);
    assert.equal(v.events[0].channel, null);
});

test('rules: share_sent cannot use the reserved "app" type, telegram_cta must, and share_open is server-only', () => {
    assert.equal(validatePayload(body([{ type: 'share_sent', puzzleType: 'app' }]), NOW), null);
    assert.equal(validatePayload(body([{ type: 'telegram_cta', puzzleType: 'sudoku' }]), NOW), null);
    assert.equal(validatePayload(body([{ type: 'share_open', puzzleType: 'sudoku' }]), NOW), null);
    assert.equal(validatePayload(body([{ type: 'web_play_hit', puzzleType: 'sudoku' }]), NOW), null);
});

test('rows of other events never gain a channel key (the column may not exist yet)', () => {
    const v = validatePayload(body([{ type: 'start', puzzleType: 'sudoku' }, { type: 'funnel', puzzleType: 'app', funnelStep: 'first_open' }]), NOW);
    for (const r of toRows(v, null)) assert.ok(!('channel' in r));
});

const req = (over = {}) => ({ method: 'GET', ip: '9.9.9.9', headers: { 'user-agent': 'Mozilla/5.0 (Linux; Android 15) Chrome/130' }, query: {}, ...over });
const game = { kind: 'game', slug: 'sudoku' };

test('page hits: a normal browser open is counted; src=share becomes share_open with the channel', () => {
    assert.deepEqual(pageHitRow(req(), game), { install_id: 'server', platform: 'web', app_version: 'server', event_type: 'web_play_hit', puzzle_type: 'sudoku' });
    const r = pageHitRow(req({ query: { src: 'share', utm_source: 'telegram' } }), game);
    assert.equal(r.event_type, 'share_open');
    assert.equal(r.channel, 'telegram');
    assert.equal(pageHitRow(req(), { kind: 'index' }).puzzle_type, 'index');
});

test('page hits honour Global Privacy Control and Do Not Track, and skip crawlers, previews and non-GET', () => {
    assert.equal(pageHitRow(req({ headers: { 'user-agent': 'Mozilla/5.0', 'sec-gpc': '1' } }), game), null);
    assert.equal(pageHitRow(req({ headers: { 'user-agent': 'Mozilla/5.0', dnt: '1' } }), game), null);
    for (const ua of ['Googlebot/2.1', 'TelegramBot (like TwitterBot)', 'WhatsApp/2.23', 'facebookexternalhit/1.1', 'curl/8', '']) {
        assert.equal(pageHitRow(req({ headers: { 'user-agent': ua } }), game), null, ua);
    }
    assert.equal(pageHitRow(req({ method: 'POST' }), game), null);
    assert.equal(pageHitRow(req(), { kind: 'something_else' }), null);
});

test('page hits store nothing identifying: no ip, no user agent, no query string', () => {
    const r = pageHitRow(req({ query: { src: 'share', utm_source: 'whatsapp', userId: 'secret', email: 'a@b.c' } }), game);
    assert.ok(!JSON.stringify(r).match(/9\.9\.9\.9|Mozilla|secret|a@b\.c/));
});

test('recorder inserts once, retries without channel when the column does not exist yet, and rate limits per ip', async () => {
    const inserts = [];
    let failChannel = true;
    const client = { from: () => ({ insert: async (row) => { inserts.push(row); return failChannel && 'channel' in row ? { error: { message: 'column "channel" of relation "puzzle_events" does not exist' } } : { error: null }; } }) };
    const record = createPageHitRecorder({ getClient: () => client, limiter: createLimiter(), log: { warn() {} } });
    record(req({ query: { src: 'share', utm_source: 'telegram' } }), game);
    await new Promise((r) => setTimeout(r, 10));
    assert.equal(inserts.length, 2);
    assert.ok('channel' in inserts[0] && !('channel' in inserts[1]));
    const before = inserts.length;
    for (let i = 0; i < 40; i++) record(req(), game);
    await new Promise((r) => setTimeout(r, 20));
    assert.ok(inserts.length - before <= 30);
});

test('recorder never throws when the database is missing', () => {
    const record = createPageHitRecorder({ getClient: () => null, limiter: createLimiter() });
    assert.doesNotThrow(() => record(req(), game));
});
