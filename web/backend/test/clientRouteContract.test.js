// Every backend path the Android app requests must exist on the server. Three screens once called paths that were never
// served; the single-page-app catch-all answered 200 with HTML, so nothing noticed.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { RETIRED_PATHS, UNSERVED_PATHS } from '../middleware/missingApi.js';

const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const androidSrc = path.resolve(backend, '../../android/app/src/main/java');
const read = (f) => fs.readFileSync(f, 'utf8');

/** Requested by app code that no screen reaches. Remove an entry together with the code. */
const UNREACHABLE_IN_APP = ['/api/force-generate-with-path-management'];

function serverRoutes() {
    const server = read(path.join(backend, 'server.js'));
    const files = new Map(); // router variable -> file
    for (const m of server.matchAll(/^import\s+(?:(\w+)|\{([^}]+)\}|(\w+),\s*\{[^}]+\})\s+from\s+'(\.\/routes\/[^']+)'/gm)) {
        const names = (m[1] || m[3] || m[2]).split(',').map((n) => n.trim()).filter(Boolean);
        for (const n of names) files.set(n, path.join(backend, m[4]));
    }
    const routes = [];
    const add = (p) => routes.push(p.replace(/\/{2,}/g, '/').replace(/(.)\/$/, '$1'));
    const paths = (literal) => [...literal.matchAll(/['"]([^'"]+)['"]/g)].map((m) => m[1]);
    for (const m of server.matchAll(/app\.(?:get|post|put|delete|patch)\(\s*(['"][^'"]+['"])/g)) paths(m[1]).filter((p) => p !== '*').forEach(add);
    for (const m of server.matchAll(/app\.use\(\s*['"]([^'"]+)['"]\s*,([^;]*)\)\s*;/g)) {
        for (const name of m[2].split(',').map((s) => s.trim())) {
            if (!files.has(name)) continue;
            for (const r of read(files.get(name)).matchAll(/\b(?:\w*[rR]outer|ingest|admin)\.(?:get|post|put|delete|patch|all)\(\s*(\[[^\]]*\]|['"][^'"]+['"])/g)) {
                paths(r[1]).forEach((p) => add(`${m[1]}/${p}`));
            }
        }
    }
    return routes.map((r) => new RegExp(`^${r.replace(/[.+]/g, '\\$&').replace(/:\w+/g, '[^/]+').replace(/\*/g, '.*')}/?$`));
}

function walk(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]));
}

function androidPaths() {
    const found = new Map(); // path -> file name
    const bases = { GAME_BASE: '/api/game-creation', SERVER_URL: '', API_BASE: '', 'BuildConfig.API_BASE_URL': '' };
    for (const file of walk(androidSrc).filter((f) => f.endsWith('.kt') || f.endsWith('.java'))) {
        const src = read(file);
        const literals = [
            ...[...src.matchAll(/"https:\/\/puzzleverseai\.com(\/[^"\s]*)/g)].map((m) => m[1]),
            ...[...src.matchAll(/"\$\{?(GAME_BASE|SERVER_URL|API_BASE|BuildConfig\.API_BASE_URL)\}?(\/[^"\s]*)/g)].map((m) => bases[m[1]] + m[2]),
            ...[...src.matchAll(/@(?:GET|POST|PUT|DELETE|PATCH)\("\/?([^"]+)"\)/g)].map((m) => `/${m[1]}`),
        ];
        for (const raw of literals) {
            const p = raw.replace(/\$\{[^}]*\}/g, 'x').replace(/\$\w+/g, 'x').split('?')[0].replace(/(.)\/$/, '$1');
            if (p === '/' || p === bases.GAME_BASE || p.endsWith('.json') || p.startsWith('/play/')) continue; // site root, base constant, static files, share links
            found.set(p, path.basename(file));
        }
    }
    return found;
}

test('every backend path in the Android app is served (or listed as retired / unreachable)', { skip: !fs.existsSync(androidSrc) }, () => {
    const routes = serverRoutes();
    const calls = androidPaths();
    assert.ok(routes.length > 100, `route parser found only ${routes.length} routes`);
    assert.ok(calls.size > 30, `path parser found only ${calls.size} client paths`);
    const known = new Set([...RETIRED_PATHS, ...UNSERVED_PATHS, ...UNREACHABLE_IN_APP]);
    const missing = [...calls].filter(([p]) => !known.has(p) && !routes.some((r) => r.test(p))).map(([p, f]) => `${p} (${f})`);
    assert.deepEqual(missing, []);
});
