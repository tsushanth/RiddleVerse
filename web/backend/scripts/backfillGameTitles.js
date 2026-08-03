// One-time backfill: walk custom_games where the title looks prompt-derived
// (title equals or is a prefix of description), extract <title> from the
// stored bundle, and update.
//
// Usage:  node scripts/backfillGameTitles.js [--dry] [--limit=200]
//
// Reads SUPABASE_URL and SUPABASE_SERVICE_KEY from env. Safe to re-run.

import dotenv from 'dotenv';
import zlib from 'zlib';
import { createClient } from '@supabase/supabase-js';

dotenv.config();

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_KEY;
if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
    console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_KEY in env.');
    process.exit(1);
}
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

const args = process.argv.slice(2);
const DRY = args.includes('--dry');
const limitArg = args.find(a => a.startsWith('--limit='));
const LIMIT = limitArg ? parseInt(limitArg.split('=')[1], 10) : 0; // 0 = no cap
const BATCH = 50;

// --- Inline helpers (copied from routes/gameCreation.routes.js so this script
// can run standalone). Keep these two functions in sync if logic changes there.
function extractHtmlFromBundle(base64Content) {
    try {
        const buffer = Buffer.from(base64Content, 'base64');
        const str = buffer.toString('utf-8');
        if (str.trim().startsWith('<!DOCTYPE') || str.trim().startsWith('<html') || str.trim().startsWith('<HTML')) {
            return str;
        }
        const files = {};
        let offset = 0;
        while (offset < buffer.length - 30) {
            if (buffer.readUInt32LE(offset) !== 0x04034b50) break;
            const compMethod = buffer.readUInt16LE(offset + 8);
            const compSize = buffer.readUInt32LE(offset + 18);
            const nameLen = buffer.readUInt16LE(offset + 26);
            const extraLen = buffer.readUInt16LE(offset + 28);
            const fileName = buffer.slice(offset + 30, offset + 30 + nameLen).toString('utf-8');
            const dataStart = offset + 30 + nameLen + extraLen;
            const compData = buffer.slice(dataStart, dataStart + compSize);
            if (!fileName.endsWith('/')) {
                let fileData;
                if (compMethod === 8) {
                    try { fileData = zlib.inflateRawSync(compData); } catch { fileData = compData; }
                } else {
                    fileData = compData;
                }
                const normalizedName = fileName.replace(/^[^/]+\//, '');
                if (normalizedName) files[normalizedName] = fileData.toString('utf-8');
            }
            offset = dataStart + compSize;
        }
        let html = files['index.html'];
        if (!html) {
            const indexKey = Object.keys(files).find(k => k.endsWith('index.html'));
            if (indexKey) html = files[indexKey];
        }
        if (!html) {
            if (str.includes('<body') || str.includes('<div')) return str;
            return null;
        }
        return html;
    } catch {
        return null;
    }
}

function extractGameTitleFromBundle(base64Content) {
    try {
        const html = extractHtmlFromBundle(base64Content);
        if (!html) return null;
        const match = html.match(/<title[^>]*>([^<]+)<\/title>/i);
        if (!match) return null;
        const raw = match[1]
            .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
            .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
            .replace(/\s+/g, ' ').trim();
        if (!raw || raw.length < 2 || raw.length > 100) return null;
        const lower = raw.toLowerCase();
        const blacklist = ['document', 'untitled', 'game', 'index', 'title', 'html', 'page', 'default'];
        if (blacklist.includes(lower)) return null;
        return raw;
    } catch {
        return null;
    }
}

// --- Backfill driver
async function main() {
    console.log(`Mode: ${DRY ? 'DRY RUN (no writes)' : 'LIVE'}${LIMIT ? `, limit=${LIMIT}` : ''}`);

    let offset = 0;
    let scanned = 0, candidates = 0, updated = 0, skipped = 0, noBundle = 0, noTitle = 0;
    const samples = [];

    while (true) {
        const { data: rows, error } = await supabase
            .from('custom_games')
            .select('id, title, description, html_content')
            .eq('game_type', 'ai_generated')
            .order('created_at', { ascending: false })
            .range(offset, offset + BATCH - 1);

        if (error) { console.error('Query error:', error.message); break; }
        if (!rows || rows.length === 0) break;

        for (const row of rows) {
            scanned++;
            if (LIMIT && updated >= LIMIT) break;

            const t = (row.title || '').trim();
            const d = (row.description || '').trim();

            // Only touch rows where title looks prompt-derived:
            //   title == description, or description startsWith title, or title is >=80 chars
            const promptish = t && d && (
                t.toLowerCase() === d.toLowerCase() ||
                d.toLowerCase().startsWith(t.toLowerCase()) ||
                t.length >= 80
            );
            if (!promptish) { skipped++; continue; }

            candidates++;
            if (!row.html_content) { noBundle++; continue; }

            const extracted = extractGameTitleFromBundle(row.html_content);
            if (!extracted) { noTitle++; continue; }

            // Don't downgrade — only update if extracted is different and shorter
            if (extracted.toLowerCase() === t.toLowerCase()) { skipped++; continue; }

            samples.push({ id: row.id, before: t.substring(0, 60), after: extracted });

            if (!DRY) {
                const { error: upErr } = await supabase
                    .from('custom_games')
                    .update({ title: extracted })
                    .eq('id', row.id);
                if (upErr) { console.error(`  ✗ ${row.id}: ${upErr.message}`); continue; }
            }
            updated++;
            if (updated % 20 === 0) console.log(`  …updated ${updated}`);
        }

        if (LIMIT && updated >= LIMIT) break;
        offset += rows.length;
        if (rows.length < BATCH) break;
    }

    console.log('');
    console.log('─── Summary ───');
    console.log(`Scanned:    ${scanned}`);
    console.log(`Candidates: ${candidates}  (title looks prompt-derived)`);
    console.log(`  → no bundle:        ${noBundle}`);
    console.log(`  → no <title> found: ${noTitle}`);
    console.log(`  → skipped (same):   ${skipped}`);
    console.log(`Updated:    ${updated}${DRY ? ' (would update; DRY)' : ''}`);
    console.log('');
    console.log('First 10 samples:');
    samples.slice(0, 10).forEach(s => {
        console.log(`  ${s.id.slice(0, 8)}  "${s.before}…"  →  "${s.after}"`);
    });
    if (!DRY && updated > 0) {
        console.log('');
        console.log('Now refresh the browse cache: hit GET /api/internal/refresh-browse-cache or restart the API.');
    }
}

main().catch(e => { console.error(e); process.exit(1); });
