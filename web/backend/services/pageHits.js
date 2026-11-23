// Server-side counting of public /play page opens. Independent of the page's JavaScript, so it answers "is anyone arriving at
// all?" when the client telemetry shows nothing, and it is the only way to see opens of shared links (src=share).
//
// Privacy: honours Global Privacy Control (Sec-GPC: 1) and Do Not Track (DNT: 1) request headers (nothing is written),
// ignores crawlers and link-preview fetchers, stores no IP, no user agent, no referrer and no query string beyond a short
// whitelisted channel label, and uses the fixed install id 'server' (not a person).
const BOT_UA = /bot|crawl|spider|slurp|preview|facebookexternalhit|embedly|telegram|whatsapp|discord|slack|curl|wget|python|go-http|headless|lighthouse|monitor|uptime/i;
const CHANNEL_RE = /^[a-z0-9_]{1,24}$/;

export function pageHitRow(req, meta) {
    const h = req.headers || {};
    if (req.method && req.method !== 'GET') return null;
    if (h['sec-gpc'] === '1' || h['dnt'] === '1') return null;
    if (!h['user-agent'] || BOT_UA.test(String(h['user-agent']))) return null;
    const kind = meta?.kind;
    if (kind !== 'game' && kind !== 'index' && kind !== 'daily') return null;
    const q = req.query || {};
    const shared = q.src === 'share';
    const medium = typeof q.utm_source === 'string' && CHANNEL_RE.test(q.utm_source) ? q.utm_source
        : (typeof q.src === 'string' && CHANNEL_RE.test(q.src) ? q.src : null);
    return {
        install_id: 'server', platform: 'web', app_version: 'server',
        event_type: shared ? 'share_open' : 'web_play_hit',
        puzzle_type: kind === 'game' ? meta.slug : kind,
        ...(medium ? { channel: medium } : {}),
    };
}

export function createPageHitRecorder({ getClient, limiter, log = console }) {
    return function record(req, meta) {
        try {
            const row = pageHitRow(req, meta);
            if (!row) return;
            if (!limiter.take(`hit:${req.ip || 'x'}`, 1, 30, 60e3)) return;
            Promise.resolve().then(async () => {
                const c = getClient();
                if (!c) return;
                let { error } = await c.from('puzzle_events').insert(row);
                if (error && /channel/i.test(error.message || '') && 'channel' in row) {
                    const { channel, ...rest } = row;
                    ({ error } = await c.from('puzzle_events').insert(rest));
                }
                if (error) log.warn?.('[page-hit] insert failed:', error.message);
            }).catch((e) => log.warn?.('[page-hit] failed:', e?.message));
        } catch { /* counting must never break a page */ }
    };
}
