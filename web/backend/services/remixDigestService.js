import crypto from 'crypto';
import cron from 'node-cron';
import { supabase } from '../config/database.js';
import { admin } from '../config/firebaseAdmin.js';
import { publicName } from '../utils/publicIdentity.js';
import { reportFailure } from '../utils/failureReporter.js';

export const DIGEST_TYPE = 'remix_digest';

/** One log row per UTC day: the fixed id lets only one machine (or one restart) claim, and so send, a day's digest. */
export function digestLogId(date) {
    const h = crypto.createHash('sha256').update(`${DIGEST_TYPE}:${date.toISOString().slice(0, 10)}`).digest('hex');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

class RemixDigestService {
    constructor() {
        this.scheduledTask = null;
        this.isRunning = false;
    }

    start(cronExpression = '0 18 * * *') { // 6 PM UTC daily
        if (this.scheduledTask) {
            console.log('Remix digest service already running');
            return;
        }

        this.scheduledTask = cron.schedule(cronExpression, () => {
            this.sendDigest();
        }, { scheduled: true, timezone: 'UTC' });

        console.log('Remix digest service started');
    }

    stop() {
        if (this.scheduledTask) {
            this.scheduledTask.stop();
            this.scheduledTask = null;
        }
    }

    async sendDigest({ db = supabase, messaging = () => admin.messaging(), now = new Date(), report = reportFailure } = {}) {
        if (this.isRunning) return;
        this.isRunning = true;

        try {
            const since = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();

            const { data: newGames, error: gamesError } = await db
                .from('custom_games')
                .select('id, title, creator_name')
                .gte('created_at', since)
                .order('created_at', { ascending: false });

            if (gamesError) {
                console.error('Remix digest: failed to query games', gamesError.message);
                report('remix_digest', new Error('games query failed'));
                return;
            }

            if (!newGames || newGames.length === 0) {
                console.log('Remix digest: no new games in last 24h, skipping');
                return;
            }

            // The API returns at most 1,000 rows and this query is not paged, so only that many devices are targeted.
            // `count` records how many active tokens exist so the gap is visible in the log row.
            const { data: tokenRows, error: tokensError, count: activeTokens } = await db
                .from('user_notification_tokens')
                .select('fcm_token', { count: 'exact' })
                .eq('is_active', true);

            if (tokensError) {
                console.error('Remix digest: failed to query tokens', tokensError.message);
                report('remix_digest', new Error('token query failed'));
                return;
            }

            const tokens = (tokenRows || [])
                .map(r => r.fcm_token)
                .filter(t => t && t.trim().length > 0);

            if (tokens.length === 0) {
                console.log('Remix digest: no active tokens');
                return;
            }

            const count = newGames.length;
            const title = count === 1
                ? 'New Remix Puzzle!'
                : `${count} New Remix Puzzles!`;
            const body = count === 1
                ? `"${publicName(newGames[0].title)}" by ${publicName(newGames[0].creator_name)}. Head to Explore > Custom Games to play!`
                : `${count} new puzzles were created today. Head to Explore > Custom Games to play!`;

            const run = {
                new_games: count,
                active_tokens: activeTokens ?? null,
                targeted: tokens.length,
                machine: process.env.FLY_MACHINE_ID || null,
            };
            const id = digestLogId(now);
            const { error: claimError } = await db.from('notification_logs').insert({
                id, notification_type: DIGEST_TYPE, title, body, success: false,
                sent_at: now.toISOString(), data: { ...run, state: 'sending' },
            });
            if (claimError) {
                if (claimError.code === '23505') {
                    console.log('Remix digest: already claimed for today, skipping');
                    return;
                }
                console.error('Remix digest: cannot write the log row, nothing sent:', claimError.message);
                report('remix_digest', new Error('log insert failed; digest not sent'), { code: claimError.code || null });
                return;
            }

            let sent = 0;
            let failed = 0;
            const errorCodes = {};
            const tally = (code, n = 1) => { errorCodes[code] = (errorCodes[code] || 0) + n; };

            // FCM supports max 500 tokens per multicast
            for (let i = 0; i < tokens.length; i += 500) {
                const batch = tokens.slice(i, i + 500);
                try {
                    const response = await messaging().sendEachForMulticast({
                        notification: { title, body },
                        tokens: batch
                    });
                    sent += response.successCount;
                    failed += response.failureCount;
                    (response.responses || []).forEach(r => { if (!r.success) tally(r.error?.code || 'unknown'); });
                    console.log(`Remix digest batch: ${response.successCount} sent, ${response.failureCount} failed`);
                } catch (err) {
                    failed += batch.length;
                    tally(err.code || 'send_threw', batch.length);
                    console.error('Remix digest: FCM send error', err.message);
                }
            }

            const { error: updateError } = await db.from('notification_logs').update({
                success: sent > 0,
                error_message: sent > 0 ? null : 'no device accepted the message',
                data: { ...run, state: 'done' },
                fcm_response: { sent, failed, error_codes: errorCodes },
            }).eq('id', id);
            if (updateError) {
                console.error('Remix digest: failed to record the result', updateError.message);
                report('remix_digest', new Error('log update failed'), { code: updateError.code || null });
            }
            if (sent === 0) report('remix_digest', new Error('digest delivered to no device'), { targeted: tokens.length });

            console.log(`Remix digest done: ${count} games, ${tokens.length} targeted of ${activeTokens ?? '?'} active, ${sent} sent, ${failed} failed`);
        } catch (error) {
            console.error('Remix digest error:', error.message);
            report('remix_digest', error);
        } finally {
            this.isRunning = false;
        }
    }
}

export const remixDigestService = new RemixDigestService();
