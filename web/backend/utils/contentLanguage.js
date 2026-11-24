// Content language for server-generated text.
//
// The Android app ships 32 UI locales (Traditional Chinese for Taiwan and Hong Kong included). Before this module the
// server never knew which one the user had picked, so every LLM prompt, every cache and every push notification was English. The client now sends its locale on every
// request (Accept-Language and x-app-language) and the backend resolves it here to one of SUPPORTED_LANGUAGES.
//
// Rules:
// - Unknown or missing languages resolve to 'en' (English behaviour is unchanged).
// - English adds nothing to prompts and nothing to cache keys, so existing cache entries and prompts stay as they were.
import { AsyncLocalStorage } from 'node:async_hooks';

export const DEFAULT_LANGUAGE = 'en';

// Code -> English name of the language, used inside LLM prompts ("Write ... in German").
// Null-prototype on purpose: a header value like 'constructor' or '__proto__' must never resolve to an inherited member.
export const LANGUAGE_NAMES = Object.freeze(Object.assign(Object.create(null), {
    en: 'English', ar: 'Arabic', bn: 'Bengali', cs: 'Czech', de: 'German', el: 'Greek', es: 'Spanish', fa: 'Persian',
    fi: 'Finnish', fr: 'French', hi: 'Hindi', hu: 'Hungarian', id: 'Indonesian', it: 'Italian', ja: 'Japanese',
    ko: 'Korean', ms: 'Malay', nl: 'Dutch', pl: 'Polish', pt: 'Brazilian Portuguese', ro: 'Romanian', ru: 'Russian',
    sv: 'Swedish', th: 'Thai', tl: 'Filipino (Tagalog)', tr: 'Turkish', uk: 'Ukrainian', ur: 'Urdu', vi: 'Vietnamese',
    zh: 'Simplified Chinese',
    // Traditional script, regional wording: its own languages, never Simplified (files zh-TW.json / zh-HK.json).
    'zh-TW': 'Traditional Chinese (Taiwan)', 'zh-HK': 'Traditional Chinese (Hong Kong)',
}));

export const SUPPORTED_LANGUAGES = Object.freeze(Object.keys(LANGUAGE_NAMES));

// Android / legacy codes that differ from the codes of web/shared/i18n.
const ALIASES = Object.freeze(Object.assign(Object.create(null), {
    in: 'id', fil: 'tl', 'pt-br': 'pt', 'pt-pt': 'pt', 'zh-cn': 'zh', 'zh-hans': 'zh', 'zh-sg': 'zh',
    nb: null, no: null,
}));

// Lower-case tag -> canonical code ('zh-tw' -> 'zh-TW'); tags are matched lower-cased. Null-prototype like the maps above.
const CANONICAL = Object.freeze(Object.assign(Object.create(null),
    Object.fromEntries(Object.keys(LANGUAGE_NAMES).map(k => [k.toLowerCase(), k]))));

/**
 * Chinese tag (lower-cased, e.g. 'zh-hant-hk', 'zh-mo', 'zh-sg') -> 'zh' | 'zh-TW' | 'zh-HK'.
 * Hans / CN / SG / MY and a bare 'zh' stay Simplified (old clients send just 'zh'); Hant or TW / HK / MO is Traditional,
 * with Hong Kong wording for HK and Taiwan wording for everything else (Macau, bare zh-Hant), as the app's resources do.
 */
export function matchChinese(c) {
    const parts = c.split('-').slice(1);
    if (parts.includes('hans')) return 'zh';
    if (parts.includes('hk')) return 'zh-HK';
    if (parts.includes('hant') || parts.includes('tw') || parts.includes('mo')) return 'zh-TW';
    return 'zh';
}

/** Best supported language for one tag ('de', 'de-AT', 'pt_BR', 'in'). Returns null when unsupported. */
export function matchLanguage(tag) {
    if (typeof tag !== 'string') return null;
    const c = tag.trim().toLowerCase().replace(/_/g, '-');
    if (!c || c === '*') return null;
    if (c in ALIASES) return ALIASES[c];
    if (c in CANONICAL) return CANONICAL[c];
    const base = c.split('-')[0];
    if (base === 'zh') return matchChinese(c);
    if (base in ALIASES) return ALIASES[base];
    return base in CANONICAL ? CANONICAL[base] : null;
}

/** First supported language of an Accept-Language header, honouring q-values ('de-AT,de;q=0.9,en;q=0.8'). */
export function fromAcceptLanguage(header) {
    if (typeof header !== 'string' || !header) return null;
    const ranked = header.split(',').map((part, index) => {
        const [tag, ...params] = part.trim().split(';');
        const q = params.map(p => p.trim()).find(p => p.startsWith('q='));
        const weight = q ? Number(q.slice(2)) : 1;
        return { tag, weight: Number.isFinite(weight) ? weight : 0, index };
    }).filter(e => e.weight > 0).sort((a, b) => b.weight - a.weight || a.index - b.index);
    for (const { tag } of ranked) {
        const m = matchLanguage(tag);
        if (m) return m;
    }
    return null;
}

/** Resolve the content language of a request: x-app-language, then ?lang= / body.lang, then Accept-Language. */
export function resolveRequestLanguage(req) {
    const h = req?.headers || {};
    const candidates = [
        matchLanguage(h['x-app-language']),
        matchLanguage(req?.query?.lang),
        matchLanguage(req?.body && typeof req.body === 'object' ? (req.body.lang || req.body.language) : null),
        fromAcceptLanguage(h['accept-language']),
    ];
    return candidates.find(Boolean) || DEFAULT_LANGUAGE;
}

export function languageName(lang) {
    return LANGUAGE_NAMES[lang] || LANGUAGE_NAMES[DEFAULT_LANGUAGE];
}

/** Sentence appended to an LLM prompt. Empty for English so English prompts are byte-for-byte unchanged. */
export function languageDirective(lang, what = 'user-facing text') {
    if (!lang || lang === DEFAULT_LANGUAGE || !LANGUAGE_NAMES[lang]) return '';
    return `Write all ${what} in ${languageName(lang)}. Keep JSON keys, enum values and code identifiers exactly as specified (they stay in English).`;
}

/** Suffix for cache keys: '' for English (existing keys keep working), '_xx' otherwise. */
export function langCacheSuffix(lang) {
    return !lang || lang === DEFAULT_LANGUAGE || !LANGUAGE_NAMES[lang] ? '' : `_${lang}`;
}

const als = new AsyncLocalStorage();

/** Express middleware: sets req.contentLang and exposes it through currentLanguage() for the rest of the request. */
export function contentLanguageMiddleware(req, res, next) {
    req.contentLang = resolveRequestLanguage(req);
    // Vary so a shared cache never serves a German body to an English client.
    res.vary?.('Accept-Language');
    als.run(req.contentLang, next);
}

/** Language of the request currently being handled ('en' outside a request, e.g. cron jobs). */
export function currentLanguage() {
    return als.getStore() || DEFAULT_LANGUAGE;
}

/** The prompt of a user-written game idea with the language/how-to-play instructions the server owns. */
export function buildGamePrompt(prompt, lang) {
    const base = String(prompt || '').trim();
    // Old clients appended the English note themselves; do not duplicate it.
    const hasNote = /how to play/i.test(base);
    const parts = [base];
    if (!hasNote) parts.push("Include a brief 'How to Play' note in the game UI.");
    if (lang && lang !== DEFAULT_LANGUAGE && LANGUAGE_NAMES[lang]) {
        parts.push(`Write all text the player sees in the game (menus, buttons, instructions, the How to Play note, score and game-over messages) in ${languageName(lang)}, unless the description above explicitly asks for another language.`);
    }
    return parts.join('\n\n');
}
