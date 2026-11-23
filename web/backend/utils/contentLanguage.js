// Content language for server-generated text.
//
// The Android app ships 30 UI locales. Before this module the server never knew which one the user had picked, so
// every LLM prompt, every cache and every push notification was English. The client now sends its locale on every
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
}));

export const SUPPORTED_LANGUAGES = Object.freeze(Object.keys(LANGUAGE_NAMES));

// Android / legacy codes that differ from the codes of web/shared/i18n.
const ALIASES = Object.freeze(Object.assign(Object.create(null), {
    in: 'id', fil: 'tl', 'pt-br': 'pt', 'pt-pt': 'pt', 'zh-cn': 'zh', 'zh-hans': 'zh', 'zh-sg': 'zh',
    // Traditional Chinese is not a shipped locale: do not pretend it is Simplified.
    nb: null, no: null,
}));

/** Best supported language for one tag ('de', 'de-AT', 'pt_BR', 'in'). Returns null when unsupported. */
export function matchLanguage(tag) {
    if (typeof tag !== 'string') return null;
    const c = tag.trim().toLowerCase().replace(/_/g, '-');
    if (!c || c === '*') return null;
    if (c in ALIASES) return ALIASES[c];
    if (LANGUAGE_NAMES[c]) return c;
    const base = c.split('-')[0];
    if (base in ALIASES) return ALIASES[base];
    return LANGUAGE_NAMES[base] ? base : null;
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
