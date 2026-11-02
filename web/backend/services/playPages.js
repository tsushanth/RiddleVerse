// Public, no-login /play pages: per-route <head> meta injected into the SPA's index.html, plus
// robots.txt and sitemap.xml. The React app renders the actual game; this module only makes sure
// crawlers and link previews see a unique title/description/canonical/JSON-LD and some real text.
// Pure functions (no I/O except loading the catalog) so they are unit tested without a server.
import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

export const SITE_ORIGIN = 'https://puzzleverseai.com';
export const PLAY_STORE_BASE = 'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse';
export const OG_IMAGE_PATH = '/og/riddleverse-feature.jpg';
const SITE_NAME = 'RiddleVerse';

const here = path.dirname(fileURLToPath(import.meta.url));
// Local: web/backend/services -> web/shared. Docker: /backend/services -> /shared. Same relative path.
const CATALOG_PATH = path.resolve(here, '..', '..', 'shared', 'playCatalog.json');

export function loadCatalog(file = CATALOG_PATH) {
    const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
    return { categories: raw.categories, games: raw.games };
}

export function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** JSON for an inline <script type="application/ld+json">: "<" is escaped so "</script>" cannot appear. */
export function safeJsonLd(obj) {
    return JSON.stringify(obj).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

/** Google Play link with UTM tags. campaign = game slug, or "index" for the /play page. */
export function playStoreUrl(campaign) {
    const c = encodeURIComponent(campaign || 'index');
    return `${PLAY_STORE_BASE}&utm_source=web&utm_medium=play_page&utm_campaign=${c}`;
}

const canonicalFor = (p) => `${SITE_ORIGIN}${p}`;

function metaForGame(game, catalog) {
    const urlPath = `/play/${game.slug}`;
    const title = `Play ${game.name} Online Free, No Sign-Up | ${SITE_NAME}`;
    const description = `${game.summary} Free in your browser, no sign-up.`;
    const jsonLd = [
        {
            '@context': 'https://schema.org',
            '@type': 'VideoGame',
            name: game.name,
            description: game.description,
            url: canonicalFor(urlPath),
            genre: 'Puzzle',
            playMode: 'SinglePlayer',
            gamePlatform: ['Web browser', 'Android'],
            applicationCategory: 'Game',
            inLanguage: 'en',
            isAccessibleForFree: true,
            image: `${SITE_ORIGIN}${OG_IMAGE_PATH}`,
            publisher: { '@type': 'Organization', name: SITE_NAME, url: SITE_ORIGIN },
            offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
            sameAs: [PLAY_STORE_BASE],
        },
        breadcrumb([['Play', '/play'], [game.name, urlPath]]),
    ];
    return {
        kind: 'game', slug: game.slug, path: urlPath, title, description,
        canonical: canonicalFor(urlPath), jsonLd,
        bodyHtml: gameBodyHtml(game, catalog),
    };
}

function metaForIndex(catalog) {
    const n = catalog.games.length;
    const title = `Free Online Puzzle Games, No Sign-Up | ${SITE_NAME}`;
    const description = `Play ${n} free puzzle games in your browser with no account: Sudoku, word games, classic casual puzzles and spatial brain teasers.`;
    const jsonLd = [
        {
            '@context': 'https://schema.org',
            '@type': 'CollectionPage',
            name: `Free online puzzle games | ${SITE_NAME}`,
            description,
            url: canonicalFor('/play'),
            mainEntity: {
                '@type': 'ItemList',
                numberOfItems: n,
                itemListElement: catalog.games.map((g, i) => ({
                    '@type': 'ListItem', position: i + 1, name: g.name, url: canonicalFor(`/play/${g.slug}`),
                })),
            },
        },
        breadcrumb([['Play', '/play']]),
    ];
    return {
        kind: 'index', slug: null, path: '/play', title, description,
        canonical: canonicalFor('/play'), jsonLd,
        bodyHtml: indexBodyHtml(catalog),
    };
}

function breadcrumb(items) {
    return {
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: [
            { '@type': 'ListItem', position: 1, name: SITE_NAME, item: SITE_ORIGIN },
            ...items.map(([name, p], i) => ({ '@type': 'ListItem', position: i + 2, name, item: canonicalFor(p) })),
        ],
    };
}

const BODY_STYLE = 'max-width:42rem;margin:0 auto;padding:1.5rem;color:#fff;font-family:Inter,system-ui,sans-serif;line-height:1.5';
const LINK_STYLE = 'color:#fff;text-decoration:underline';

// Text shown to crawlers (and to people for a moment before the React app mounts and replaces it).
function gameBodyHtml(game, catalog) {
    const steps = game.howToPlay.map((s) => `<li>${escapeHtml(s)}</li>`).join('');
    const others = catalog.games.filter((g) => g.slug !== game.slug)
        .map((g) => `<a style="${LINK_STYLE}" href="/play/${escapeHtml(g.slug)}">${escapeHtml(g.name)}</a>`).join(' &middot; ');
    return `<main style="${BODY_STYLE}">`
        + `<h1>Play ${escapeHtml(game.name)} online, free</h1>`
        + `<p>${escapeHtml(game.description)}</p>`
        + `<h2>How to play ${escapeHtml(game.name)}</h2><ol>${steps}</ol>`
        + `<p><strong>Tip:</strong> ${escapeHtml(game.tip)}</p>`
        + `<p><a style="${LINK_STYLE}" href="${escapeHtml(playStoreUrl(game.slug))}">Get the app on Google Play</a></p>`
        + `<h2>More free puzzle games</h2><p>${others}</p>`
        + `</main>`;
}

function indexBodyHtml(catalog) {
    const byCat = catalog.categories.map((c) => {
        const items = catalog.games.filter((g) => g.category === c.id)
            .map((g) => `<li><a style="${LINK_STYLE}" href="/play/${escapeHtml(g.slug)}">${escapeHtml(g.name)}</a>: ${escapeHtml(g.summary)}</li>`).join('');
        return `<h2>${escapeHtml(c.name)}</h2><ul>${items}</ul>`;
    }).join('');
    return `<main style="${BODY_STYLE}">`
        + `<h1>Free online puzzle games</h1>`
        + `<p>Pick a game and start playing right away. No account, no download, no sign-up.</p>`
        + byCat
        + `<p><a style="${LINK_STYLE}" href="${escapeHtml(playStoreUrl('index'))}">Get the app on Google Play</a></p>`
        + `</main>`;
}

/**
 * Resolve a request path to page meta, or null when this module does not own the path.
 * Accepts a trailing slash. Slug lookup is exact; the router handles case-folding redirects.
 */
export function resolvePlayPage(reqPath, catalog) {
    const p = reqPath.length > 1 ? reqPath.replace(/\/+$/, '') : reqPath;
    if (p === '/play') return metaForIndex(catalog);
    const m = /^\/play\/([^/]+)$/.exec(p);
    if (!m) return null;
    const game = catalog.games.find((g) => g.slug === m[1]);
    return game ? metaForGame(game, catalog) : null;
}

/** Inject meta into the built index.html. Replacers are functions so "$&" etc. in copy stay literal. */
export function injectMeta(template, meta) {
    const t = escapeHtml(meta.title);
    const d = escapeHtml(meta.description);
    const img = `${SITE_ORIGIN}${OG_IMAGE_PATH}`;
    const extra = [
        `<link rel="canonical" href="${escapeHtml(meta.canonical)}" />`,
        `<meta name="robots" content="index, follow, max-image-preview:large" />`,
        `<meta property="og:type" content="website" />`,
        `<meta property="og:site_name" content="${SITE_NAME}" />`,
        `<meta property="og:title" content="${t}" />`,
        `<meta property="og:description" content="${d}" />`,
        `<meta property="og:url" content="${escapeHtml(meta.canonical)}" />`,
        `<meta property="og:image" content="${img}" />`,
        `<meta property="og:image:width" content="1024" />`,
        `<meta property="og:image:height" content="500" />`,
        `<meta name="twitter:card" content="summary_large_image" />`,
        `<meta name="twitter:title" content="${t}" />`,
        `<meta name="twitter:description" content="${d}" />`,
        `<meta name="twitter:image" content="${img}" />`,
        ...meta.jsonLd.map((o) => `<script type="application/ld+json">${safeJsonLd(o)}</script>`),
    ].join('\n    ');

    let html = template;
    html = html.replace(/<title>[\s\S]*?<\/title>/i, () => `<title>${t}</title>`);
    html = html.replace(/<meta\s+name=["']description["'][^>]*>/i, () => `<meta name="description" content="${d}" />`);
    html = html.replace(/<\/head>/i, () => `    ${extra}\n  </head>`);
    html = html.replace(/<div id="root"><\/div>/i, () => `<div id="root">${meta.bodyHtml}</div>`);
    return html;
}

export function buildSitemap(catalog, origin = SITE_ORIGIN) {
    const urls = ['/play', ...catalog.games.map((g) => `/play/${g.slug}`)];
    const body = urls.map((u) => `  <url><loc>${escapeHtml(origin + u)}</loc></url>`).join('\n');
    return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${body}\n</urlset>\n`;
}

export function buildRobots(origin = SITE_ORIGIN) {
    return [
        'User-agent: *',
        'Allow: /',
        'Disallow: /api/',
        'Disallow: /auth/',
        'Disallow: /admin',
        '',
        `Sitemap: ${origin}/sitemap.xml`,
        '',
    ].join('\n');
}

/**
 * Express router. deps are injectable for tests:
 *   getTemplate(): Promise<string> (built index.html), catalog.
 * Anything it does not own calls next(), so the existing /play/:gameId custom-game routes and the
 * /play?session_id=... SPA route keep working unchanged.
 */
export function createPlayPagesRouter(deps = {}) {
    const catalog = deps.catalog || loadCatalog();
    let cached = null;
    const getTemplate = deps.getTemplate || (async () => {
        if (!cached) cached = await fs.promises.readFile(path.join(path.resolve(), 'public', 'index.html'), 'utf8');
        return cached;
    });
    const router = express.Router();

    router.get('/robots.txt', (req, res) => {
        res.type('text/plain').set('Cache-Control', 'public, max-age=3600').send(buildRobots());
    });
    router.get('/sitemap.xml', (req, res) => {
        res.type('application/xml').set('Cache-Control', 'public, max-age=3600').send(buildSitemap(catalog));
    });

    router.get(['/play', '/play/:slug'], async (req, res, next) => {
        try {
            // /play?session_id=... is the host-controlled AI game session wrapper, not a landing page.
            if (req.path.replace(/\/+$/, '') === '/play' && req.query.session_id !== undefined) return next();

            const slug = req.params.slug;
            if (slug && slug !== slug.toLowerCase() && catalog.games.some((g) => g.slug === slug.toLowerCase())) {
                return res.redirect(301, `/play/${slug.toLowerCase()}`);
            }
            const meta = resolvePlayPage(req.path, catalog);
            if (!meta) return next();

            const html = injectMeta(await getTemplate(), meta);
            res.set('Content-Type', 'text/html; charset=utf-8');
            res.set('Cache-Control', 'public, max-age=300');
            res.send(html);
        } catch (e) {
            next(e);
        }
    });
    return router;
}
