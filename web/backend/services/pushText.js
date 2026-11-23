// Localized push notification text. Strings live in web/shared/i18n/<lang>.json (section "push", machine translated,
// same files as the Telegram bot); anything missing falls back to English. English notifications keep their original
// wording (the callers only localize when the token's language is not English).
import { loadLocales, translate, resolveLang, DEFAULT_LANG } from './telegram/i18n.js';

let locales = null;
function getLocales() {
    if (!locales) locales = loadLocales();
    return locales;
}

/** Supported language code for a stored token language (null/unknown -> 'en'). */
export function pushLanguage(code) {
    return resolveLang(code, getLocales());
}

export function pushText(lang, key, params = {}) {
    return translate(getLocales(), pushLanguage(lang), 'push', key, params);
}

/**
 * Localized streak notification for a non-English language, as { title, message }; null for English (the caller keeps
 * its original English wording). type: 'streak_at_risk' | 'comeback' | 'celebration'.
 */
export function localizedStreakPush(type, lang, days) {
    const l = pushLanguage(lang);
    if (l === DEFAULT_LANG) return null;
    if (type === 'streak_at_risk') {
        return { title: `🔥 ${pushText(l, 'streak_risk_title')}`, message: pushText(l, 'streak_risk_body', { days }) };
    }
    if (type === 'comeback') {
        return {
            title: `💪 ${pushText(l, 'comeback_title')}`,
            message: days > 7 ? pushText(l, 'comeback_record', { days }) : pushText(l, 'comeback_new'),
        };
    }
    if (type === 'celebration') {
        return { title: `🎉 ${pushText(l, 'celebrate_title', { days })}`, message: pushText(l, 'celebrate_body', { days }) };
    }
    return null;
}

/** Localized "custom puzzles ready" notification for a non-English language, else null. */
export function localizedPuzzlesReadyPush(lang, topic, count) {
    const l = pushLanguage(lang);
    if (l === DEFAULT_LANG) return null;
    return { title: `🎉 ${pushText(l, 'puzzles_ready_title')}`, body: pushText(l, 'puzzles_ready_body', { topic, count }) };
}
