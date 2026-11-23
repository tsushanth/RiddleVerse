import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
    SUPPORTED_LANGUAGES, LANGUAGE_NAMES, matchLanguage, fromAcceptLanguage, resolveRequestLanguage, languageName,
    languageDirective, langCacheSuffix, contentLanguageMiddleware, currentLanguage, buildGamePrompt,
} from '../utils/contentLanguage.js';

// config/database.js builds the Supabase client at import time from env.
process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
const { buildRiddlePrompt } = await import('../services/puzzleService.js');
const { getNextDailyPuzzle, hasDailyPuzzles, dailyContentLanguage, getDailyPuzzles, setLocalizedPool, _resetDailyCacheForTests } = await import('../services/dailyPuzzles.js');

test('the 30 app locales are exactly the 30 shared i18n languages', () => {
    const dir = new URL('../../shared/i18n/', import.meta.url);
    const files = fs.readdirSync(dir).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)).sort();
    assert.deepEqual([...SUPPORTED_LANGUAGES].sort(), files);
    assert.equal(SUPPORTED_LANGUAGES.length, 30);
});

test('matchLanguage: Android tags, regions, aliases, unsupported', () => {
    assert.equal(matchLanguage('de'), 'de');
    assert.equal(matchLanguage('de-AT'), 'de');
    assert.equal(matchLanguage('pt-BR'), 'pt');
    assert.equal(matchLanguage('pt_BR'), 'pt');
    assert.equal(matchLanguage('zh-CN'), 'zh');
    assert.equal(matchLanguage('in'), 'id');
    assert.equal(matchLanguage('fil'), 'tl');
    assert.equal(matchLanguage('he'), null);
    assert.equal(matchLanguage('nb'), null);
    assert.equal(matchLanguage(''), null);
    assert.equal(matchLanguage(undefined), null);
    assert.equal(matchLanguage('*'), null);
});

test('fromAcceptLanguage: q-values order, skips unsupported tags', () => {
    assert.equal(fromAcceptLanguage('fr-CH, fr;q=0.9, en;q=0.8'), 'fr');
    assert.equal(fromAcceptLanguage('en;q=0.5, ja;q=0.9'), 'ja');
    assert.equal(fromAcceptLanguage('he, ru;q=0.5'), 'ru');
    assert.equal(fromAcceptLanguage('he'), null);
    assert.equal(fromAcceptLanguage('de;q=0, es;q=0.1'), 'es');
    assert.equal(fromAcceptLanguage(undefined), null);
});

test('resolveRequestLanguage: x-app-language > ?lang > body > Accept-Language > en', () => {
    assert.equal(resolveRequestLanguage({ headers: { 'x-app-language': 'ko', 'accept-language': 'de' }, query: { lang: 'fr' } }), 'ko');
    assert.equal(resolveRequestLanguage({ headers: { 'accept-language': 'de' }, query: { lang: 'fr' } }), 'fr');
    assert.equal(resolveRequestLanguage({ headers: { 'accept-language': 'de' }, query: {}, body: { lang: 'it' } }), 'it');
    assert.equal(resolveRequestLanguage({ headers: { 'accept-language': 'es-MX,es;q=0.9' }, query: {} }), 'es');
    assert.equal(resolveRequestLanguage({ headers: {}, query: {} }), 'en');
    assert.equal(resolveRequestLanguage({ headers: { 'accept-language': 'he' } }), 'en');
    assert.equal(resolveRequestLanguage({}), 'en');
});

test('English adds nothing to prompts or cache keys; other languages do', () => {
    assert.equal(languageDirective('en'), '');
    assert.equal(languageDirective(undefined), '');
    assert.equal(langCacheSuffix('en'), '');
    assert.equal(langCacheSuffix('xx'), '');
    assert.equal(langCacheSuffix('de'), '_de');
    assert.match(languageDirective('de'), /in German/);
    assert.match(languageDirective('pt', 'riddle text'), /riddle text in Brazilian Portuguese/);
    assert.equal(languageName('zz'), 'English');
    for (const code of SUPPORTED_LANGUAGES) assert.ok(LANGUAGE_NAMES[code]);
});

test('middleware sets req.contentLang and makes it available for the rest of the request', async () => {
    const req = { headers: { 'accept-language': 'tr' }, query: {} };
    const res = { vary() {} };
    let seen;
    await new Promise(resolve => contentLanguageMiddleware(req, res, () => { seen = currentLanguage(); resolve(); }));
    assert.equal(req.contentLang, 'tr');
    assert.equal(seen, 'tr');
    assert.equal(currentLanguage(), 'en', 'outside a request it is English');
});

test('buildGamePrompt: How to Play note is added once; language only for non-English', () => {
    assert.equal(buildGamePrompt('A snake game', 'en'), "A snake game\n\nInclude a brief 'How to Play' note in the game UI.");
    // old clients append the English note themselves: not duplicated
    const old = "A snake game\n\nInclude a brief 'How to Play' note in the game UI.";
    assert.equal(buildGamePrompt(old, 'en'), old);
    const de = buildGamePrompt('Ein Schlangenspiel', 'de');
    assert.match(de, /How to Play/);
    assert.match(de, /in German/);
    assert.ok(!/in German/.test(buildGamePrompt('x', 'en')));
});

test('riddle prompt: language directive only for non-English; English prompt unchanged', () => {
    const en = buildRiddlePrompt('space', 3, 'Q&A', 'en');
    const de = buildRiddlePrompt('space', 3, 'Q&A', 'de');
    assert.ok(!/Write all/.test(en));
    assert.match(de, /Write all riddle text \(question, answer, options and hint\) in German/);
    assert.ok(de.startsWith(en.split('Respond ONLY with valid JSON.')[0].trimEnd()));
    assert.equal(buildRiddlePrompt('space', 3, 'Q&A'), en);
});

test('daily cache: languages do not share entries; a missing localized pool falls back to English and says so', () => {
    _resetDailyCacheForTests();
    const en = [{ puzzleId: 'e1', question: 'English one' }];
    const de = [{ puzzleId: 'd1', question: 'Deutsch eins' }];
    setLocalizedPool('riddle', 'easy', 'en', en);
    assert.equal(hasDailyPuzzles('riddle', 'easy', 'de'), true, 'falls back to English');
    assert.equal(dailyContentLanguage('riddle', 'easy', 'de'), 'en');
    assert.equal(getNextDailyPuzzle('riddle', 'easy', 'de').puzzleId, 'e1');
    setLocalizedPool('riddle', 'easy', 'de', de);
    assert.equal(dailyContentLanguage('riddle', 'easy', 'de'), 'de');
    assert.equal(getNextDailyPuzzle('riddle', 'easy', 'de').puzzleId, 'd1');
    assert.equal(getNextDailyPuzzle('riddle', 'easy', 'en').puzzleId, 'e1', 'English clients never see German');
    assert.equal(getNextDailyPuzzle('riddle', 'easy', 'fr').puzzleId, 'e1');
    const bulk = getDailyPuzzles(['riddle'], 'easy', undefined, 'de');
    assert.equal(bulk.puzzles.riddle[0].puzzleId, 'd1');
    assert.equal(bulk.contentLanguage.riddle, 'de');
    assert.equal(getDailyPuzzles(['riddle'], 'easy').puzzles.riddle[0].puzzleId, 'e1');
    _resetDailyCacheForTests();
});

test('wiring: the language reaches generate-riddle, fetch-next-puzzle, the daily list and game prompts', () => {
    const read = (p) => fs.readFileSync(new URL(p, import.meta.url), 'utf8');
    assert.match(read('../server.js'), /app\.use\(contentLanguageMiddleware\)/);
    assert.match(read('../routes/generation.routes.js'), /puzzleId, req\.contentLang\)/);
    assert.match(read('../services/puzzleService.js'), /createPuzzles\(topic, batchSize, format, lang\)/);
    assert.match(read('../routes/puzzleEndpoints.routes.js'), /getNextDailyPuzzle\(puzzleType, difficulty, lang\)/);
    assert.match(read('../routes/dailyPuzzlesBulk.routes.js'), /req\.contentLang/);
    const gc = read('../routes/gameCreation.routes.js');
    assert.match(gc, /buildGamePrompt\(prompt, req\.contentLang\)/);
    assert.match(gc, /buildGamePrompt\(remixDescription, req\.contentLang\)/);
});
