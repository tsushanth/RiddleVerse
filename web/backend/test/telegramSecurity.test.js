import test from 'node:test';
import assert from 'node:assert/strict';
import { safeEqual, verifyInitData, signInitDataForTest, signContext, verifyContext, newLinkCode, hashLinkCode, LINK_CODE_RE } from '../services/telegram/security.js';
import { TOKEN } from './telegramHelpers.js';

const NOW = Date.UTC(2026, 10, 22, 12, 0, 0);
const sec = (ms) => String(Math.floor(ms / 1000));
const fields = (over = {}) => ({ auth_date: sec(NOW - 60e3), query_id: 'AAH', user: JSON.stringify({ id: 42, first_name: 'Ana', username: 'ana' }), ...over });

test('safeEqual: equal, different, different length, non-strings', () => {
    assert.equal(safeEqual('abc', 'abc'), true);
    assert.equal(safeEqual('abc', 'abd'), false);
    assert.equal(safeEqual('abc', 'abcd'), false);
    assert.equal(safeEqual('abc', undefined), false);
    assert.equal(safeEqual(null, 'abc'), false);
});

test('initData: a correctly signed payload is accepted and yields the user', () => {
    const r = verifyInitData(signInitDataForTest(fields(), TOKEN), TOKEN, { now: () => NOW });
    assert.equal(r.user.id, 42);
    assert.equal(r.user.username, 'ana');
});

test('initData: wrong token, tampered field, missing hash, junk are rejected', () => {
    const good = signInitDataForTest(fields(), TOKEN);
    assert.equal(verifyInitData(good, 'other:token', { now: () => NOW }), null);
    assert.equal(verifyInitData(good.replace('Ana', 'Eve'), TOKEN, { now: () => NOW }), null);
    assert.equal(verifyInitData(good.replace(/hash=[0-9a-f]+/, 'hash='), TOKEN, { now: () => NOW }), null);
    assert.equal(verifyInitData('not a query string', TOKEN, { now: () => NOW }), null);
    assert.equal(verifyInitData('', TOKEN, { now: () => NOW }), null);
    assert.equal(verifyInitData(undefined, TOKEN, { now: () => NOW }), null);
    assert.equal(verifyInitData('x'.repeat(5000), TOKEN, { now: () => NOW }), null);
});

test('initData: older than 24 h or from the future is rejected', () => {
    const old = signInitDataForTest(fields({ auth_date: sec(NOW - 25 * 3600e3) }), TOKEN);
    assert.equal(verifyInitData(old, TOKEN, { now: () => NOW }), null);
    const future = signInitDataForTest(fields({ auth_date: sec(NOW + 3600e3) }), TOKEN);
    assert.equal(verifyInitData(future, TOKEN, { now: () => NOW }), null);
});

test('initData: user without a numeric id is rejected', () => {
    const bad = signInitDataForTest(fields({ user: JSON.stringify({ id: 'x' }) }), TOKEN);
    assert.equal(verifyInitData(bad, TOKEN, { now: () => NOW }), null);
});

test('context: round trip, negative group ids, and tampering', () => {
    const c = signContext({ kind: 'p', slug: 'sudoku', chatId: -1001234567890 }, TOKEN);
    assert.deepEqual(verifyContext(c, TOKEN), { kind: 'p', slug: 'sudoku', chatId: '-1001234567890' });
    assert.match(c, /^[A-Za-z0-9_-]+$/); // valid Telegram start_param alphabet
    assert.equal(verifyContext(c.replace('sudoku', 'wordhive'), TOKEN), null);
    assert.equal(verifyContext(c.replace('m1001', 'm2001'), TOKEN), null);
    assert.equal(verifyContext(c, 'other:token'), null);
    assert.equal(verifyContext('p_sudoku_m1_', TOKEN), null);
    assert.equal(verifyContext(undefined, TOKEN), null);
    assert.throws(() => signContext({ kind: 'x', slug: 'sudoku', chatId: 1 }, TOKEN));
});

test('link codes: URL-safe, unique, 32 chars, stored only as a hash', () => {
    const a = newLinkCode(); const b = newLinkCode();
    assert.match(a, LINK_CODE_RE);
    assert.notEqual(a, b);
    assert.notEqual(hashLinkCode(a), a);
    assert.equal(hashLinkCode(a), hashLinkCode(a));
});
