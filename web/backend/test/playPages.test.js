import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';
import http from 'http';
import express from 'express';
import { fileURLToPath } from 'url';
import {
    loadCatalog, resolvePlayPage, injectMeta, buildSitemap, buildRobots, createPlayPagesRouter,
    playStoreUrl, escapeHtml, safeJsonLd, PLAY_STORE_BASE, SITE_ORIGIN,
} from '../services/playPages.js';
import { playStoreUrl as frontendPlayStoreUrl, PLAY_STORE_BASE as FRONTEND_BASE } from '../../frontend/src/utils/playLinks.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const catalog = loadCatalog();
const TEMPLATE = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="description" content="RiddleVerse - Challenge your mind with AI-powered puzzles" />
    <title>RiddleVerse - AI Puzzle Game</title>
    <script type="module" src="/assets/index-abc.js"></script>
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>`;

test('catalog: 19 games, unique slugs, every field present, ASCII, summary short', () => {
    assert.equal(catalog.games.length, 19);
    assert.equal(new Set(catalog.games.map((g) => g.slug)).size, 19);
    const cats = new Set(catalog.categories.map((c) => c.id));
    for (const g of catalog.games) {
        assert.match(g.slug, /^[a-z]+$/);
        assert.ok(cats.has(g.category), g.slug);
        assert.ok(g.name && g.summary && g.description && g.tip, g.slug);
        assert.ok(g.howToPlay.length >= 3, g.slug);
        assert.ok(g.summary.length < 120, g.slug);
        assert.ok(g.description.split(/(?<=[.!?])\s+/).length <= 4, `${g.slug} description should be 2-3 sentences`);
        assert.ok(!/[^\x20-\x7E]/.test(JSON.stringify(g)), `${g.slug} must be ASCII`);
    }
});

test('catalog slugs match the web ports registered in the frontend', () => {
    const root = path.join(here, '..', '..', 'frontend', 'src', 'components', 'puzzles');
    const arr = (file, re) => [...fs.readFileSync(path.join(root, file), 'utf8').match(re)[1].matchAll(/'([a-z]+)'/g)].map((m) => m[1]);
    const expected = [
        ...arr('classic/common.js', /CLASSIC_TYPES = \[([^\]]*)\]/),
        ...arr('spatial/engine/common.js', /SPATIAL_TYPES = \[([^\]]*)\]/),
        ...arr('word/wordPuzzleRegistry.js', /WORD_PUZZLE_TYPES = \[([^\]]*)\]/),
        ...[...fs.readFileSync(path.join(root, 'grid/gridRegistry.jsx'), 'utf8').match(/gridRenderers = \{([^}]*)\}/)[1].matchAll(/^\s*([a-z]+):/gm)].map((m) => m[1]),
    ];
    assert.equal(expected.length, 19);
    assert.deepEqual([...catalog.games.map((g) => g.slug)].sort(), [...expected].sort());
});

test('playStoreUrl: UTM-tagged, encoded, same as the frontend helper', () => {
    assert.equal(PLAY_STORE_BASE, FRONTEND_BASE);
    assert.equal(playStoreUrl('sudoku'),
        'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=web&utm_medium=play_page&utm_campaign=sudoku');
    assert.equal(playStoreUrl(), playStoreUrl('index'));
    assert.equal(playStoreUrl('a b&c'), `${PLAY_STORE_BASE}&utm_source=web&utm_medium=play_page&utm_campaign=a%20b%26c`);
    for (const c of ['sudoku', 'index', 'x y']) assert.equal(playStoreUrl(c), frontendPlayStoreUrl(c));
});

test('resolvePlayPage: index, every game, trailing slash; unknown paths are not owned', () => {
    assert.equal(resolvePlayPage('/play', catalog).kind, 'index');
    assert.equal(resolvePlayPage('/play/', catalog).kind, 'index');
    for (const g of catalog.games) {
        const m = resolvePlayPage(`/play/${g.slug}`, catalog);
        assert.equal(m.kind, 'game');
        assert.equal(m.canonical, `${SITE_ORIGIN}/play/${g.slug}`);
        assert.equal(resolvePlayPage(`/play/${g.slug}/`, catalog).canonical, m.canonical);
    }
    assert.equal(resolvePlayPage('/play/not-a-game', catalog), null);
    assert.equal(resolvePlayPage('/play/3f2c9a54-1111-2222-3333-444455556666', catalog), null); // custom-game ids fall through
    assert.equal(resolvePlayPage('/play/sudoku/extra.js', catalog), null);
    assert.equal(resolvePlayPage('/puzzle/sudoku', catalog), null);
});

test('every page has a unique title, description and canonical; lengths are search-friendly', () => {
    const metas = ['/play', ...catalog.games.map((g) => `/play/${g.slug}`)].map((p) => resolvePlayPage(p, catalog));
    for (const key of ['title', 'description', 'canonical']) {
        assert.equal(new Set(metas.map((m) => m[key])).size, metas.length, `${key} must be unique`);
    }
    for (const m of metas) {
        assert.ok(m.title.length <= 65, `${m.title} (${m.title.length})`);
        assert.ok(m.description.length <= 165, `${m.description} (${m.description.length})`);
    }
});

test('injectMeta: replaces title/description, adds canonical, OG, twitter, JSON-LD and crawlable body', () => {
    const meta = resolvePlayPage('/play/sudoku', catalog);
    const html = injectMeta(TEMPLATE, meta);
    assert.equal((html.match(/<title>/g) || []).length, 1);
    assert.match(html, /<title>Play Sudoku Online Free, No Sign-Up \| RiddleVerse<\/title>/);
    assert.equal((html.match(/name="description"/g) || []).length, 1);
    assert.ok(!html.includes('AI-powered puzzles'));
    assert.match(html, /<link rel="canonical" href="https:\/\/puzzleverseai\.com\/play\/sudoku" \/>/);
    assert.match(html, /<meta property="og:title" content="Play Sudoku Online Free/);
    assert.match(html, /<meta property="og:url" content="https:\/\/puzzleverseai\.com\/play\/sudoku"/);
    assert.match(html, /<meta property="og:image" content="https:\/\/puzzleverseai\.com\/og\/riddleverse-feature\.jpg"/);
    assert.match(html, /<meta name="twitter:card" content="summary_large_image"/);
    assert.match(html, /<div id="root"><main[^>]*><h1>Play Sudoku online, free<\/h1>/);
    assert.match(html, /How to play Sudoku/);
    assert.ok(html.includes(escapeHtml(playStoreUrl('sudoku'))));
    assert.ok(html.indexOf('application/ld+json') < html.indexOf('</head>'));
    // SPA bundle tag survives untouched
    assert.ok(html.includes('<script type="module" src="/assets/index-abc.js"></script>'));
    // JSON-LD blocks parse
    const blocks = [...html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)].map((m) => JSON.parse(m[1]));
    assert.equal(blocks.length, 2);
    assert.equal(blocks[0]['@type'], 'VideoGame');
    assert.equal(blocks[0].url, 'https://puzzleverseai.com/play/sudoku');
    assert.equal(blocks[0].aggregateRating, undefined, 'no invented ratings');
    assert.equal(blocks[1]['@type'], 'BreadcrumbList');
});

test('injectMeta: index page lists every game as a crawlable link and ItemList entry', () => {
    const html = injectMeta(TEMPLATE, resolvePlayPage('/play', catalog));
    for (const g of catalog.games) assert.ok(html.includes(`href="/play/${g.slug}"`), g.slug);
    const ld = [...html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)].map((m) => JSON.parse(m[1]));
    assert.equal(ld[0]['@type'], 'CollectionPage');
    assert.equal(ld[0].mainEntity.itemListElement.length, 19);
});

test('injectMeta: copy with HTML, quotes, "$&" and </script> cannot break out of the page', () => {
    const evil = {
        kind: 'game', title: 'A "quoted" <b>$& title</b>', description: 'd </head><script>alert(1)</script> $1 $`',
        canonical: 'https://puzzleverseai.com/play/x', bodyHtml: '<main>$&</main>',
        jsonLd: [{ name: '</script><script>alert(2)</script>' }],
    };
    const html = injectMeta(TEMPLATE, evil);
    assert.ok(!html.includes('<script>alert'));
    assert.ok(html.includes('<title>A &quot;quoted&quot; &lt;b&gt;$&amp; title&lt;/b&gt;</title>'));
    assert.ok(html.includes('<main>$&</main>'));
    assert.ok(!safeJsonLd({ a: '</script>' }).includes('</script>'));
    assert.equal(JSON.parse(safeJsonLd({ a: '</script>' })).a, '</script>');
});

test('sitemap: index + one URL per game (20), absolute, unique, valid shell', () => {
    const xml = buildSitemap(catalog);
    const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
    assert.equal(locs.length, 20);
    assert.equal(new Set(locs).size, 20);
    assert.equal(locs[0], 'https://puzzleverseai.com/play');
    for (const g of catalog.games) assert.ok(locs.includes(`https://puzzleverseai.com/play/${g.slug}`));
    assert.ok(locs.every((l) => l.startsWith('https://puzzleverseai.com/') && !l.includes('?')));
    assert.match(xml, /^<\?xml version="1.0" encoding="UTF-8"\?>\n<urlset xmlns="http:\/\/www\.sitemaps\.org\/schemas\/sitemap\/0\.9">/);
    assert.ok(xml.trimEnd().endsWith('</urlset>'));
});

test('robots: allows play pages, blocks api/auth/admin, points at the sitemap', () => {
    const r = buildRobots();
    assert.match(r, /^User-agent: \*$/m);
    assert.match(r, /^Allow: \/$/m);
    assert.match(r, /^Disallow: \/api\/$/m);
    assert.ok(!/Disallow: \/play/.test(r));
    assert.match(r, /^Sitemap: https:\/\/puzzleverseai\.com\/sitemap\.xml$/m);
});

// ---- router, over real HTTP ----
async function withServer(fn, templateFn = async () => TEMPLATE) {
    const app = express();
    app.use(createPlayPagesRouter({ catalog, getTemplate: templateFn }));
    // stand-ins for the existing routes the new router must not shadow
    app.get('/play/:gameId', (req, res) => res.send(`custom-game:${req.params.gameId}`));
    app.get('*', (req, res) => res.send('spa-fallback'));
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, '127.0.0.1', r));
    const base = `http://127.0.0.1:${server.address().port}`;
    try { await fn(base); } finally { await new Promise((r) => server.close(r)); }
}

test('router: serves injected HTML for /play and each game with 200 and html content type', async () => {
    await withServer(async (base) => {
        for (const p of ['/play', ...catalog.games.map((g) => `/play/${g.slug}`)]) {
            const res = await fetch(base + p);
            assert.equal(res.status, 200, p);
            assert.match(res.headers.get('content-type'), /text\/html/);
            const body = await res.text();
            assert.match(body, /<link rel="canonical" href="https:\/\/puzzleverseai\.com\/play/);
            assert.ok(!body.includes('AI-powered puzzles'));
        }
    });
});

test('router: does not shadow custom games, the session wrapper, or unknown paths', async () => {
    await withServer(async (base) => {
        assert.equal(await (await fetch(`${base}/play/3f2c9a54-1111-2222-3333-444455556666`)).text(), 'custom-game:3f2c9a54-1111-2222-3333-444455556666');
        assert.equal(await (await fetch(`${base}/play?session_id=abc`)).text(), 'spa-fallback');
        assert.equal(await (await fetch(`${base}/puzzle/sudoku`)).text(), 'spa-fallback');
        assert.equal(await (await fetch(`${base}/play/nope`)).text(), 'custom-game:nope');
    });
});

test('router: mixed-case slug 301s to the canonical lowercase URL; trailing slash is served', async () => {
    await withServer(async (base) => {
        const r = await fetch(`${base}/play/Sudoku`, { redirect: 'manual' });
        assert.equal(r.status, 301);
        assert.equal(r.headers.get('location'), '/play/sudoku');
        assert.equal((await fetch(`${base}/play/sudoku/`)).status, 200);
    });
});

test('router: robots.txt and sitemap.xml', async () => {
    await withServer(async (base) => {
        const robots = await fetch(`${base}/robots.txt`);
        assert.match(robots.headers.get('content-type'), /text\/plain/);
        assert.match(await robots.text(), /Sitemap:/);
        const sm = await fetch(`${base}/sitemap.xml`);
        assert.match(sm.headers.get('content-type'), /xml/);
        assert.equal((await sm.text()).match(/<loc>/g).length, 20);
    });
});

test('router: template read failure goes to the error handler (500), not a hang', async () => {
    await withServer(async (base) => {
        const r = await fetch(`${base}/play/sudoku`);
        assert.equal(r.status, 500);
    }, async () => { throw new Error('no index.html'); });
});
