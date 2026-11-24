// The app shows server errors and build status in the user's language by mapping stable codes to string resources.
// These tests keep the three sides in step: every error the routes send has a code, and every code the backend or the
// worker can send is one the Android app knows (GameServerText.kt) with a string in all 32 locales.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const read = (p) => fs.readFileSync(new URL(p, import.meta.url), 'utf8');
const routes = read('../routes/gameCreation.routes.js');
const guard = read('../middleware/gameCreationGuard.js');
const workerCodes = read('../../../game-worker/statusCodes.js');
const kt = read('../../../android/app/src/main/java/com/kreativekoala/riddleverse/GameServerText.kt');

const ktBlock = (name) => {
    const m = new RegExp(`private val ${name} = mapOf\\(([\\s\\S]*?)\\n    \\)`).exec(kt);
    assert.ok(m, `${name} map in GameServerText.kt`);
    return new Set([...m[1].matchAll(/"(\w+)" to /g)].map(x => x[1]));
};
const errorCodesKnownToApp = ktBlock('errorRes');
const messageCodesKnownToApp = ktBlock('messageRes');
const detailCodesKnownToApp = ktBlock('detailRes');

test('every literal error the game-creation routes send carries a code (old clients still get the English text)', () => {
    const missing = [];
    for (const m of routes.matchAll(/\berror: ('(?:[^'\\\n]|\\.)*'|`[^`\n]*`)(?!, code:)/g)) {
        missing.push(m[1].slice(0, 60));
    }
    assert.deepEqual(missing, []);
    assert.ok([...routes.matchAll(/code: '(\w+)'/g)].length > 60);
});

test('error codes sent by the routes, the guard and the worker are all mapped by the app', () => {
    const sent = new Set();
    for (const m of routes.matchAll(/code: '(\w+)'/g)) sent.add(m[1]);
    for (const m of guard.matchAll(/code: '(\w+)'/g)) sent.add(m[1]);
    for (const m of workerCodes.matchAll(/\],\s*'(\w+)'\],?/g)) sent.add(m[1]);
    // The error-code table of the worker (second table in the file).
    const errTable = workerCodes.slice(workerCodes.indexOf('const ERROR_CODES'));
    for (const m of errTable.matchAll(/, '(\w+)'\]/g)) sent.add(m[1]);
    sent.add('quota_exhausted');
    const unknown = [...sent].filter(c => !errorCodesKnownToApp.has(c));
    assert.deepEqual(unknown, [], 'codes the app cannot map');
    for (const c of ['description_required', 'build_connection_lost', 'free_limit_reached', 'rate_limited']) assert.ok(sent.has(c), c);
});

test('status codes of the worker are all mapped by the app', () => {
    const msg = new Set([...workerCodes.slice(0, workerCodes.indexOf('// [pattern')).matchAll(/\/, '(\w+)'\],/g)].map(m => m[1]));
    const det = new Set([...workerCodes.slice(workerCodes.indexOf('const DETAIL_CODES'), workerCodes.indexOf('/** Codes for one')).matchAll(/\/, '(\w+)', \d\]/g)].map(m => m[1]));
    assert.ok(msg.size >= 12 && det.size >= 12, `${msg.size} message codes, ${det.size} detail codes`);
    assert.deepEqual([...msg].filter(c => !messageCodesKnownToApp.has(c)), []);
    assert.deepEqual([...det].filter(c => !detailCodesKnownToApp.has(c)), []);
});

test('the status events of every proxied build carry the phase and the codes', () => {
    const statusForwards = [...routes.matchAll(/phase: parsed\.phase,\n(\s*)messageCode: parsed\.messageCode,\n\s*detailCode: parsed\.detailCode,\n\s*detailArgs: parsed\.detailArgs,/g)];
    assert.equal(statusForwards.length, [...routes.matchAll(/phase: parsed\.phase,/g)].length);
    assert.ok(statusForwards.length >= 6);
});

test('worker errors that the backend proxies keep their code', () => {
    assert.equal([...routes.matchAll(/code: parsed\.code/g)].length, [...routes.matchAll(/error: parsed\.error/g)].length);
});

test('every new app string exists in all 32 locales', () => {
    const resDir = new URL('../../../android/app/src/main/res/', import.meta.url);
    const names = (f) => [...fs.readFileSync(f, 'utf8').matchAll(/<string name="(\w+)"/g)].map(m => m[1]);
    const base = names(new URL('values/strings_server_text.xml', resDir));
    assert.equal(base.length, 47);
    const dirs = fs.readdirSync(resDir).filter(d => /^values-([a-z]{2}(-r[A-Z]{2})?|b\+zh\+Hant)$/.test(d));
    assert.equal(dirs.length, 32);
    for (const d of dirs) assert.deepEqual(names(new URL(`${d}/strings_server_text.xml`, resDir)), base, d);
    // every resource the app maps exists
    for (const m of kt.matchAll(/R\.string\.(gen_\w+)/g)) assert.ok(base.includes(m[1]), m[1]);
});
