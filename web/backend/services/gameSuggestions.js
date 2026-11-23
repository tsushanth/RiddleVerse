// Create-game idea chips (table game_suggestions), per language.
//
// A row of chips must never mix languages: the pool is filtered to the app language, and when the server has none for
// that language it returns an empty list so the app shows its own localized chips instead of English ones.
import { DEFAULT_LANGUAGE, LANGUAGE_NAMES, languageDirective } from '../utils/contentLanguage.js';

/** Rows from game_suggestions; rows without a language column (migration not applied yet) are English. */
export function normalizeSuggestionRows(rows) {
    return (rows || [])
        .filter(r => r && r.label && r.prompt)
        .map(r => ({
            label: r.label,
            prompt: r.prompt,
            language: LANGUAGE_NAMES[r.language] ? r.language : DEFAULT_LANGUAGE,
        }));
}

/** Random `count` chips of exactly one language. Returns [] when that language has none. */
export function pickSuggestions(items, lang, count = 6, random = Math.random) {
    const want = LANGUAGE_NAMES[lang] ? lang : DEFAULT_LANGUAGE;
    const pool = (items || []).filter(i => (i.language || DEFAULT_LANGUAGE) === want);
    const shuffled = pool.map(i => ({ i, k: random() })).sort((a, b) => a.k - b.k).map(x => x.i);
    return shuffled.slice(0, Math.max(0, Math.min(count, shuffled.length))).map(({ label, prompt }) => ({ label, prompt }));
}

/** The LLM prompt that replenishes the pool, in the requested language. English text is the original prompt. */
export function buildReplenishPrompt(existingLabels, lang = DEFAULT_LANGUAGE) {
    const directive = languageDirective(lang, 'text of the "label" and "prompt" fields');
    return `You are a creative game designer. Generate 6 unique, fun mini-game ideas for a mobile game creation platform. Each game must be buildable as a single-file HTML5 game with touch controls.

Return a JSON array of exactly 6 objects with "label" (2-3 word catchy name) and "prompt" (detailed game description in 2-4 sentences covering: core mechanic, controls, scoring, win/lose condition, and one visual flair detail).

Do NOT suggest any of these existing games: ${existingLabels.join(', ')}

Requirements:
- Games must work on mobile (touch/tap/swipe/drag controls)
- Each game should be a different genre (e.g., puzzle, action, arcade, strategy, word, reflex)
- Descriptions should be specific enough that a developer could build the game from the description alone
- Keep labels short and catchy (2-3 words max)
${directive ? `- ${directive}\n` : ''}
Respond with ONLY the JSON array, no other text.`;
}

/** Once per language per window: a pool that is too small asks for a replenishment (an LLM call), never a storm. */
export function createReplenishGate(windowMs = 60 * 60 * 1000, now = () => Date.now()) {
    const last = new Map();
    return (lang) => {
        const t = now();
        if (last.has(lang) && t - last.get(lang) < windowMs) return false;
        last.set(lang, t);
        return true;
    };
}
