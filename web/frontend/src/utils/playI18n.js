// Language for the public /play chrome (buttons, nav, footer). Game names, rules and the SEO text stay English: the crawler
// text is injected by the backend and the game ports are English-only. Strings come from web/shared/i18n/<code>.json (the
// "web" section), the same files the Telegram bot uses. Machine translations, see _meta.review in each file.
// Free of imports and of top-level browser globals so node tests can load it.

export const RTL = new Set(['ar', 'fa', 'ur', 'he'])
const ALIASES = { in: 'id', iw: 'he', fil: 'tl', 'zh-hans': 'zh', 'zh-cn': 'zh', 'zh-sg': 'zh', 'zh-hant': 'zh', 'zh-tw': 'zh', 'zh-hk': 'zh' }

/** First supported language among the candidates (query ?lang=, then navigator.languages). Defaults to 'en'. */
export function pickLang(candidates, supported) {
  const has = new Set(supported)
  for (const raw of candidates || []) {
    if (typeof raw !== 'string' || !raw) continue
    const c = raw.toLowerCase().replace(/_/g, '-')
    if (ALIASES[c] && has.has(ALIASES[c])) return ALIASES[c]
    if (has.has(c)) return c
    const base = c.split('-')[0]
    const b = ALIASES[base] || base
    if (has.has(b)) return b
  }
  return 'en'
}

/** {placeholders} filled; a key missing in the language falls back to English, then to the key itself. */
export function format(table, fallback, key, params = {}) {
  const raw = (table && table[key]) ?? (fallback && fallback[key]) ?? key
  return String(raw).replace(/\{(\w+)\}/g, (m, k) => (params[k] !== undefined ? String(params[k]) : m))
}

export const isRtl = (lang) => RTL.has(lang)
