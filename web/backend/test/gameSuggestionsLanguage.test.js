import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { pickSuggestions, normalizeSuggestionRows, buildReplenishPrompt, createReplenishGate } from '../services/gameSuggestions.js';

const rows = [
    { label: 'Snake Neon', prompt: 'p1' }, // no language column yet: English
    { label: 'Brick Breaker', prompt: 'p2', language: 'en' },
    { label: 'Memory Cards', prompt: 'p3', language: 'en' },
    { label: 'Neon Schlange', prompt: 'q1', language: 'de' },
    { label: 'Ziegel', prompt: 'q2', language: 'de' },
    { label: 'Broken', prompt: '', language: 'de' },
    { label: 'Weird', prompt: 'w', language: 'zz' },
];
const items = normalizeSuggestionRows(rows);

test('normalize: rows without or with an unknown language are English; incomplete rows dropped', () => {
    assert.equal(items.length, 6);
    assert.equal(items.find(i => i.label === 'Snake Neon').language, 'en');
    assert.equal(items.find(i => i.label === 'Weird').language, 'en');
});

test('a row of chips is never mixed: only the requested language', () => {
    for (let n = 0; n < 20; n++) {
        const de = pickSuggestions(items, 'de', 6);
        assert.deepEqual(de.map(c => c.label).sort(), ['Neon Schlange', 'Ziegel']);
        const en = pickSuggestions(items, 'en', 6);
        assert.ok(en.every(c => ['Snake Neon', 'Brick Breaker', 'Memory Cards', 'Weird'].includes(c.label)));
        assert.ok(!en.some(c => c.label === 'Ziegel'));
    }
});

test('a language without chips gets none (the app then shows its own localized chips), never English ones', () => {
    assert.deepEqual(pickSuggestions(items, 'fr', 6), []);
    assert.deepEqual(pickSuggestions([], 'en', 6), []);
    assert.deepEqual(pickSuggestions(items, 'en', 2).length, 2);
    assert.deepEqual(pickSuggestions(items, 'xx', 6).every(c => !/Schlange|Ziegel/.test(c.label)), true, 'unknown code behaves as English');
});

test('chips carry only label and prompt', () => {
    assert.deepEqual(Object.keys(pickSuggestions(items, 'de', 1)[0]).sort(), ['label', 'prompt']);
});

test('replenish prompt: English is the original prompt; other languages ask for that language', () => {
    const en = buildReplenishPrompt(['snake'], 'en');
    const de = buildReplenishPrompt(['snake'], 'de');
    assert.ok(!/Write all/.test(en));
    assert.match(de, /Write all text of the "label" and "prompt" fields in German/);
    assert.match(en, /Do NOT suggest any of these existing games: snake/);
    assert.equal(de.replace(/- Write all[^\n]*\n/, ''), en);
});

test('replenish gate: one LLM call per language per window', () => {
    let t = 0;
    const gate = createReplenishGate(1000, () => t);
    assert.equal(gate('de'), true);
    assert.equal(gate('de'), false);
    assert.equal(gate('fr'), true);
    t = 1001;
    assert.equal(gate('de'), true);
});

test('route wiring: the suggestions endpoint answers in the request language and says which', () => {
    const src = fs.readFileSync(new URL('../routes/gameCreation.routes.js', import.meta.url), 'utf8');
    assert.match(src, /const lang = req\.contentLang \|\| DEFAULT_LANGUAGE;\n\s*const suggestions = getRandomSuggestions\(count, lang\);/);
    assert.match(src, /language: lang,\n\s*suggestions/);
    assert.match(src, /select\('label, prompt, language'\)/);
    assert.match(src, /\.\.\.\(lang === DEFAULT_LANGUAGE \? \{\} : \{ language: lang \}\)/);
    const kt = fs.readFileSync(new URL('../../../android/app/src/main/java/com/kreativekoala/riddleverse/GameCreationScreen.kt', import.meta.url), 'utf8');
    assert.match(kt, /suggestions\?count=6&lang=\$appLanguage/);
    assert.match(kt, /optString\("language", ContentLanguage\.DEFAULT\) == appLanguage/);
});
