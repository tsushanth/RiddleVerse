// Bot strings: one JSON per language in web/shared/i18n (same files the /play pages use). Machine translations, see
// _meta.review. Anything missing in a language falls back to English, so a half-finished file never breaks a reply.
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { matchChinese } from '../../utils/contentLanguage.js';

const here = path.dirname(fileURLToPath(import.meta.url));
// Local: web/backend/services/telegram -> web/shared/i18n. Docker: /backend/services/telegram -> /shared/i18n.
export const DEFAULT_I18N_DIR = path.resolve(here, '..', '..', '..', 'shared', 'i18n');
export const DEFAULT_LANG = 'en';

export function loadLocales(dir = DEFAULT_I18N_DIR) {
    const out = new Map();
    for (const f of fs.readdirSync(dir)) {
        if (!f.endsWith('.json')) continue;
        const code = f.slice(0, -5);
        out.set(code, JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')));
    }
    if (!out.has(DEFAULT_LANG)) throw new Error('en.json missing in ' + dir);
    return out;
}

// Telegram sends IETF tags (en, pt-br, zh-hans, zh-hant, zh-tw, in some clients "fil" or the legacy "in" / "iw").
// Chinese is resolved by script/region (see matchChinese): Simplified -> zh, Taiwan/Macau/zh-Hant -> zh-TW, Hong Kong -> zh-HK.
const ALIASES = { in: 'id', iw: 'he', fil: 'tl', nb: 'no' };

/** Best supported language code for a Telegram language_code (or a stored choice). Always returns a loaded code. */
export function resolveLang(code, locales) {
    if (typeof code !== 'string' || !code) return DEFAULT_LANG;
    const c = code.toLowerCase().replace(/_/g, '-');
    const base = c.split('-')[0];
    if (base === 'zh') {
        const zh = matchChinese(c);
        return locales.has(zh) ? zh : locales.has('zh') ? 'zh' : DEFAULT_LANG;
    }
    if (Object.hasOwn(ALIASES, c) && locales.has(ALIASES[c])) return ALIASES[c];
    if (locales.has(c)) return c;
    const b = Object.hasOwn(ALIASES, base) ? ALIASES[base] : base;
    return locales.has(b) ? b : DEFAULT_LANG;
}

export function languageList(locales) {
    return [...locales.entries()].map(([code, v]) => ({ code, name: v._meta?.name || code }))
        .sort((a, b) => (a.code === DEFAULT_LANG ? -1 : b.code === DEFAULT_LANG ? 1 : a.code.localeCompare(b.code)));
}

/** t(lang, 'bot', 'welcome', { name }) with {placeholders}. Unknown key returns the key itself (visible in tests). */
export function translate(locales, lang, section, key, params = {}) {
    const raw = locales.get(lang)?.[section]?.[key] ?? locales.get(DEFAULT_LANG)?.[section]?.[key] ?? key;
    return String(raw).replace(/\{(\w+)\}/g, (m, k) => (params[k] !== undefined ? String(params[k]) : m));
}

export function gameName(locales, lang, slug) {
    return locales.get(lang)?.games?.[slug] ?? locales.get(DEFAULT_LANG)?.games?.[slug] ?? slug;
}

export function createI18n(locales = loadLocales()) {
    return {
        locales,
        resolve: (code) => resolveLang(code, locales),
        t: (lang, key, params) => translate(locales, lang, 'bot', key, params),
        web: (lang, key, params) => translate(locales, lang, 'web', key, params),
        game: (lang, slug) => gameName(locales, lang, slug),
        languages: () => languageList(locales),
        has: (code) => locales.has(code),
    };
}
