import cron from 'node-cron';
import { supabase } from '../config/database.js';
import { admin } from '../config/firebaseAdmin.js';

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

    async sendDigest() {
        if (this.isRunning) return;
        this.isRunning = true;

        try {
            const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

            const { data: newGames, error: gamesError } = await supabase
                .from('custom_games')
                .select('id, title, creator_name')
                .gte('created_at', since)
                .order('created_at', { ascending: false });

            if (gamesError) {
                console.error('Remix digest: failed to query games', gamesError.message);
                return;
            }

            if (!newGames || newGames.length === 0) {
                console.log('Remix digest: no new games in last 24h, skipping');
                return;
            }

            const { data: tokenRows, error: tokensError } = await supabase
                .from('user_notification_tokens')
                .select('fcm_token')
                .eq('is_active', true);

            if (tokensError) {
                console.error('Remix digest: failed to query tokens', tokensError.message);
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
                ? `"${newGames[0].title}" by ${newGames[0].creator_name}. Head to Explore > Custom Games to play!`
                : `${count} new puzzles were created today. Head to Explore > Custom Games to play!`;

            // FCM supports max 500 tokens per multicast
            for (let i = 0; i < tokens.length; i += 500) {
                const batch = tokens.slice(i, i + 500);
                try {
                    const response = await admin.messaging().sendEachForMulticast({
                        notification: { title, body },
                        tokens: batch
                    });
                    console.log(`Remix digest batch: ${response.successCount} sent, ${response.failureCount} failed`);
                } catch (err) {
                    console.error('Remix digest: FCM send error', err.message);
                }
            }

            try {
                await supabase.from('notification_logs').insert({
                    title,
                    message: body,
                    sent_count: tokens.length,
                    failed_count: 0,
                    total_tokens: tokens.length,
                    created_at: new Date().toISOString()
                });
            } catch (logErr) {
                console.error('Remix digest: failed to log notification', logErr.message);
            }

            console.log(`Remix digest sent: ${count} games, ${tokens.length} users`);
        } catch (error) {
            console.error('Remix digest error:', error.message);
        } finally {
            this.isRunning = false;
        }
    }
}

export const remixDigestService = new RemixDigestService();
