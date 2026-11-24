import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { createI18n, loadLocales, resolveLang, DEFAULT_I18N_DIR } from '../services/telegram/i18n.js';
import { loadCatalog } from '../services/playCatalog.js';

const locales = loadLocales();
const en = locales.get('en');
const EXPECTED = ['ar', 'bn', 'cs', 'de', 'el', 'en', 'es', 'fa', 'fi', 'fr', 'hi', 'hu', 'id', 'it', 'ja', 'ko', 'ms', 'nl', 'pl', 'pt', 'ro', 'ru', 'sv', 'th', 'tl', 'tr', 'uk', 'ur', 'vi', 'zh', 'zh-HK', 'zh-TW'];
const ph = (s) => (String(s).match(/\{\w+\}/g) || []).sort().join(',');

test('all 31 app languages plus English are present', () => {
    assert.deepEqual([...locales.keys()].sort(), EXPECTED);
    assert.equal(readdirSync(DEFAULT_I18N_DIR).filter((f) => f.endsWith('.json')).length, 32);
});

test('every language has exactly the English keys, with the same placeholders and newline count', () => {
    for (const [code, l] of locales) {
        for (const section of ['bot', 'web']) {
            assert.deepEqual(Object.keys(l[section]), Object.keys(en[section]), `${code}.${section} keys`);
            for (const k of Object.keys(en[section])) {
                assert.equal(typeof l[section][k], 'string', `${code}.${section}.${k}`);
                assert.ok(l[section][k].trim().length > 0, `${code}.${section}.${k} empty`);
                assert.equal(ph(l[section][k]), ph(en[section][k]), `${code}.${section}.${k} placeholders`);
                if (section === 'bot' && k === 'help') {
                    assert.equal(l.bot.help.split('\n').length, en.bot.help.split('\n').length, `${code} help lines`);
                    for (const c of ['/daily', '/play', '/leaderboard', '/link', '/remind', '/language', '/app', '/help']) assert.ok(l.bot.help.includes(c), `${code} help keeps ${c}`);
                }
            }
        }
    }
});

test('Telegram limits: command text 3-256, short description <= 120, description <= 512, buttons are short', () => {
    for (const [code, l] of locales) {
        for (const k of Object.keys(l.bot).filter((x) => x.startsWith('cmd_'))) {
            const n = [...l.bot[k]].length;
            assert.ok(n >= 3 && n <= 256, `${code}.${k} ${n}`);
        }
        assert.ok([...l.bot.bot_short_description].length <= 120, `${code} short`);
        assert.ok([...l.bot.bot_description].length <= 512, `${code} description`);
        for (const k of Object.keys(l.bot).filter((x) => x.startsWith('btn_'))) assert.ok([...l.bot[k]].length <= 30, `${code}.${k}`);
    }
});

test('every language names all 23 games, and no em dashes slipped in', () => {
    const slugs = loadCatalog().games.map((g) => g.slug);
    for (const [code, l] of locales) {
        for (const s of slugs) assert.ok(l.games?.[s], `${code} game ${s}`);
    }
    for (const f of readdirSync(DEFAULT_I18N_DIR)) assert.ok(!readFileSync(`${DEFAULT_I18N_DIR}/${f}`, 'utf8').includes('—'), f);
});

test('language resolution: Telegram tags, aliases, regions and fallback', () => {
    const r = (c) => resolveLang(c, locales);
    assert.equal(r('de'), 'de');
    assert.equal(r('DE-at'), 'de');
    assert.equal(r('pt-br'), 'pt');
    assert.equal(r('pt-PT'), 'pt');
    assert.equal(r('zh-hans'), 'zh');
    assert.equal(r('zh-TW'), 'zh-TW');
    assert.equal(r('zh-tw'), 'zh-TW');
    assert.equal(r('zh_TW'), 'zh-TW');
    assert.equal(r('zh-hant'), 'zh-TW');
    assert.equal(r('zh-Hant-TW'), 'zh-TW');
    assert.equal(r('zh-MO'), 'zh-TW');
    assert.equal(r('zh-HK'), 'zh-HK');
    assert.equal(r('zh-hant-hk'), 'zh-HK');
    assert.equal(r('zh'), 'zh');
    assert.equal(r('zh-cn'), 'zh');
    assert.equal(r('zh-sg'), 'zh');
    assert.equal(r('constructor'), 'en');
    assert.equal(r('__proto__'), 'en');
    assert.equal(resolveLang('zh-TW', new Map([['en', {}], ['zh', {}]])), 'zh', 'no Traditional file: falls back to zh, not English');
    assert.equal(r('in'), 'id');
    assert.equal(r('fil'), 'tl');
    assert.equal(r('xx'), 'en');
    assert.equal(r(undefined), 'en');
    assert.equal(r(''), 'en');
});

test('translate: placeholders filled, unknown key visible, English fallback for a missing key', () => {
    const i = createI18n(new Map([['en', { bot: { a: 'Hi {name}', b: 'B' } }], ['xx', { bot: { a: 'Hallo {name}' } }]]));
    assert.equal(i.t('xx', 'a', { name: 'Ana' }), 'Hallo Ana');
    assert.equal(i.t('xx', 'b'), 'B');
    assert.equal(i.t('xx', 'zzz'), 'zzz');
});
