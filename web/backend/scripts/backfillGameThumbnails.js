// One-time backfill: walk custom_games where initial_screenshot_url IS NULL,
// call the screenshot service, upload the JPEG to Supabase Storage
// (game-bundles/thumbnails/{id}.jpg), and update the DB row.
//
// Usage:  node scripts/backfillGameThumbnails.js [--dry] [--limit=N]
//
// Mirrors captureGameThumbnail() in routes/gameCreation.routes.js. Run from a
// shell with SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SERVICE_KEY)
// exported. SCREENSHOT_SERVICE_URL defaults to the Hetzner box used in prod.

import dotenv from 'dotenv';
import { createClient } from '@supabase/supabase-js';

dotenv.config();

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_KEY;
const SCREENSHOT_SERVICE_URL = process.env.SCREENSHOT_SERVICE_URL || 'http://178.156.231.255:3465';
const PLAY_BASE = 'https://puzzleverseai.com';

if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
    console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_KEY in env.');
    process.exit(1);
}
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

const args = process.argv.slice(2);
const DRY = args.includes('--dry');
const limitArg = args.find(a => a.startsWith('--limit='));
const LIMIT = limitArg ? parseInt(limitArg.split('=')[1], 10) : 0;

async function captureOne(gameId) {
    const playUrl = `${PLAY_BASE}/api/game-creation/${gameId}?platform=web&chatId=preview`;
    const shotUrl = `${SCREENSHOT_SERVICE_URL}/screenshot?url=${encodeURIComponent(playUrl)}&width=390&height=700&delay=4000`;

    const res = await fetch(shotUrl, { signal: AbortSignal.timeout(60000) });
    if (!res.ok) throw new Error(`screenshot HTTP ${res.status}`);
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.length < 1000) throw new Error(`screenshot too small (${buf.length}b)`);

    const filename = `thumbnails/${gameId}.jpg`;
    const { error: upErr } = await supabase.storage
        .from('game-bundles')
        .upload(filename, buf, { contentType: 'image/jpeg', upsert: true });
    if (upErr) throw new Error(`upload: ${upErr.message}`);

    const { data: urlData } = supabase.storage.from('game-bundles').getPublicUrl(filename);
    const thumbnailUrl = urlData.publicUrl;

    const { error: dbErr } = await supabase
        .from('custom_games')
        .update({ initial_screenshot_url: thumbnailUrl })
        .eq('id', gameId);
    if (dbErr) throw new Error(`db update: ${dbErr.message}`);

    return { thumbnailUrl, size: buf.length };
}

async function main() {
    console.log(`Mode: ${DRY ? 'DRY (no writes)' : 'LIVE'}${LIMIT ? `  limit=${LIMIT}` : ''}`);
    console.log(`Screenshot service: ${SCREENSHOT_SERVICE_URL}`);

    const { data, count, error } = await supabase
        .from('custom_games')
        .select('id, title, created_at', { count: 'exact' })
        .eq('game_type', 'ai_generated')
        .is('initial_screenshot_url', null)
        .order('created_at', { ascending: false });

    if (error) { console.error('query:', error.message); process.exit(1); }
    let rows = data || [];
    if (LIMIT) rows = rows.slice(0, LIMIT);

    console.log(`Found ${count} candidates; processing ${rows.length}\n`);

    let ok = 0, fail = 0;
    for (const row of rows) {
        const t = (row.title || '').slice(0, 40);
        if (DRY) {
            console.log(`  [dry] ${row.id.slice(0,8)}  "${t}"`);
            ok++;
            continue;
        }
        try {
            const res = await captureOne(row.id);
            ok++;
            console.log(`  ✓ ${row.id.slice(0,8)}  "${t}"  (${(res.size/1024).toFixed(1)}kb)`);
        } catch (e) {
            fail++;
            console.log(`  ✗ ${row.id.slice(0,8)}  "${t}"  → ${e.message}`);
        }
        // Be gentle on the screenshot service
        await new Promise(r => setTimeout(r, 500));
    }

    console.log(`\nDone. ${ok} captured, ${fail} failed.`);
    if (!DRY && ok > 0) {
        console.log('Refresh browse cache: hit /api/internal/refresh-browse-cache or restart API.');
    }
}

main().catch(e => { console.error(e); process.exit(1); });
