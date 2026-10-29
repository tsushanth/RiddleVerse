// services/firebaseStreakNotificationService.js
import { admin, db } from '../config/firebaseAdmin.js';
import { supabase } from '../config/database.js';
import { createHash } from 'node:crypto';

/**
 * Operating limits. All come from env vars so they can be changed with
 * `flyctl secrets set` / `fly.toml [env]` without a code change.
 *
 *   STREAK_NOTIFICATIONS_ENABLED   "true" or "1" turns the job on. Anything else (or unset) = OFF.
 *   STREAK_MAX_USERS_PER_RUN       default 25. Max users that get a Firestore query per run.
 *   STREAK_TOKEN_MAX_AGE_DAYS      default 90. Tokens not updated within this window are skipped.
 *   STREAK_MIN_RUN_GAP_MINUTES     default 60. A new run is skipped if one started more recently
 *                                  (checked in Supabase, so machine restarts do not multiply runs).
 *
 * Worst-case Firestore reads: 25 users x 30 docs = 750 per run, <= 24 runs/day = 18,000/day.
 */
export function isStreakNotificationsEnabled(env = process.env) {
    const v = String(env.STREAK_NOTIFICATIONS_ENABLED || '').trim().toLowerCase();
    return v === 'true' || v === '1';
}

function intEnv(name, fallback, env = process.env) {
    const n = parseInt(env[name], 10);
    return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function getStreakLimits(env = process.env) {
    return {
        maxUsersPerRun: intEnv('STREAK_MAX_USERS_PER_RUN', 25, env),
        tokenMaxAgeDays: intEnv('STREAK_TOKEN_MAX_AGE_DAYS', 90, env),
        minRunGapMinutes: intEnv('STREAK_MIN_RUN_GAP_MINUTES', 60, env)
    };
}

/** Short stable id for logs and stored error details. Never log the email itself. */
export function hashUser(userEmail) {
    return createHash('sha256').update(String(userEmail || '')).digest('hex').slice(0, 8);
}

function isQuotaError(error) {
    return !!error && (error.code === 8 || /RESOURCE_EXHAUSTED/.test(String(error.message || '')));
}

export class FirebaseStreakNotificationService {
    constructor(deps = {}) {
        // Injectable for tests; production uses the real Supabase client, Firestore and admin SDK.
        this.supabase = deps.supabase ?? supabase;
        this.db = deps.db ?? db;
        this.admin = deps.admin ?? admin;
        this.processedUsers = new Set(); // Prevent duplicate processing in same run
        this.isRunning = false;
        this.intervalId = null;
        this.jobHistory = [];
        this.disabledLogged = false;
    }

    /** Log once per process that the job is off, so the log shows it is deliberate. */
    logDisabledOnce() {
        if (!this.disabledLogged) {
            this.disabledLogged = true;
            console.log('ℹ️ Streak notification job is OFF (set STREAK_NOTIFICATIONS_ENABLED=true to enable)');
        }
    }

    /**
     * Start the periodic streak notification service
     */
    startPeriodicService(intervalMinutes = 60) {
        if (!isStreakNotificationsEnabled()) {
            this.logDisabledOnce();
            return;
        }
        if (this.intervalId) {
            console.log('🔥 Streak notification service already running');
            return;
        }

        const intervalMs = intervalMinutes * 60 * 1000;
        console.log(`🔥 Starting streak notification service (every ${intervalMinutes} minutes)`);

        // Run immediately on startup
        setTimeout(() => this.runPeriodicCheck(), 5000); // 5 second delay after startup

        // Then run on interval
        this.intervalId = setInterval(() => {
            this.runPeriodicCheck();
        }, intervalMs);

        console.log('✅ Streak notification service started');
    }

    /**
     * Stop the periodic service
     */
    stopPeriodicService() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
            console.log('ℹ️ Streak notification service stopped');
        }
    }

    /**
     * Run a single periodic check with comprehensive error handling
     */
    async runPeriodicCheck() {
        if (!isStreakNotificationsEnabled()) {
            this.logDisabledOnce();
            return;
        }
        if (this.isRunning) {
            console.log('⏭️ Skipping streak check - already running');
            return;
        }

        // The Fly machine stops when idle and every wake used to start a run, so the
        // in-process interval does not bound the run rate. Bound it in Supabase instead.
        if (await this.ranRecently()) {
            console.log('⏭️ Skipping streak check - a run started within the minimum gap');
            return;
        }

        const jobId = `streak_job_${Date.now()}`;
        const startTime = new Date();
        
        console.log(`🔥 Starting periodic streak check: ${jobId}`);
        this.isRunning = true;

        let jobResult = {
            jobId,
            startTime: startTime.toISOString(),
            endTime: null,
            status: 'running',
            totalUsers: 0,
            notificationsSent: 0,
            errors: [],
            duration: 0
        };

        try {
            // Log job start
            await this.logJobStart(jobId, startTime);

            // Run the actual check
            const results = await this.checkAllUsersForStreakNotifications();
            
            // Clear processed users
            this.clearProcessedUsers();

            if (results.quotaExhausted) {
                // Close the job row as failed instead of reporting a fake "completed" run.
                throw new Error(`Firestore quota exhausted (${results.quotaErrorCount} quota errors)`);
            }

            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);

            jobResult = {
                ...jobResult,
                endTime: endTime.toISOString(),
                status: 'completed',
                totalUsers: results.totalUsers,
                atRiskUsers: results.atRiskUsers,
                comebackUsers: results.comebackUsers,
                celebrationUsers: results.celebrationUsers,
                notificationsSent: results.notificationsSent,
                errors: results.errors,
                duration
            };

            console.log(`✅ Streak check completed: ${results.notificationsSent} notifications sent to ${results.totalUsers} users (${duration}s)`);

            // Log successful completion
            await this.logJobCompletion(jobResult);

        } catch (error) {
            const endTime = new Date();
            const duration = Math.round((endTime - startTime) / 1000);

            jobResult = {
                ...jobResult,
                endTime: endTime.toISOString(),
                status: 'failed',
                duration,
                errors: [{ error: error.message, stack: error.stack }]
            };

            console.error(`❌ Streak check failed: ${error.message}`);

            // Log job failure
            await this.logJobFailure(jobResult, error);

        } finally {
            this.clearProcessedUsers();
            this.isRunning = false;
            this.addToHistory(jobResult);
        }
    }

    /**
     * True if a job row started within the minimum gap. Fails closed: if Supabase
     * cannot answer, skip the run rather than risk an unbounded Firestore burst.
     */
    async ranRecently() {
        try {
            const { minRunGapMinutes } = getStreakLimits();
            const since = new Date(Date.now() - minRunGapMinutes * 60 * 1000).toISOString();
            const { data, error } = await this.supabase
                .from('streak_notification_jobs')
                .select('job_id')
                .gte('started_at', since)
                .limit(1);
            if (error) {
                console.error('❌ Could not check recent streak runs, skipping this run:', error.message);
                return true;
            }
            return Array.isArray(data) && data.length > 0;
        } catch (error) {
            console.error('❌ Could not check recent streak runs, skipping this run:', error.message);
            return true;
        }
    }

    /**
     * Main function: Check all users for streak-based notifications
     * Uses Supabase for tokens, Firebase for analytics.
     * Work per run is capped (see getStreakLimits) and users with no usable,
     * recently refreshed FCM token are never queried in Firestore.
     */
    async checkAllUsersForStreakNotifications() {
        try {
            const results = {
                totalUsers: 0,
                validUsers: 0,
                atRiskUsers: 0,
                comebackUsers: 0,
                celebrationUsers: 0,
                notificationsSent: 0,
                quotaExhausted: false,
                quotaErrorCount: 0,
                errors: []
            };

            const { maxUsersPerRun, tokenMaxAgeDays } = getStreakLimits();
            const freshSince = new Date(Date.now() - tokenMaxAgeDays * 24 * 60 * 60 * 1000).toISOString();

            // Only active tokens that are non-empty and recently refreshed; freshest first.
            // The explicit limit also replaces the silent 1000-row PostgREST default.
            // Fetch a few extra rows because several tokens can belong to one user.
            const { data: tokenUsers, error } = await this.supabase
                .from('user_notification_tokens')
                .select('user_email, firebase_uid, fcm_token')
                .eq('is_active', true)
                .not('fcm_token', 'is', null)
                .neq('fcm_token', '')
                .gte('updated_at', freshSince)
                .order('updated_at', { ascending: false })
                .limit(maxUsersPerRun * 3);

            if (error) {
                console.error('❌ Error fetching tokens from Supabase:', error);
                throw error;
            }
    
            if (!tokenUsers || tokenUsers.length === 0) {
                console.log('⚠️ No active notification tokens found in Supabase');
                return results;
            }
    
            // Get unique users (some might have multiple tokens/platforms)
            const uniqueUsers = new Map();
            tokenUsers.forEach(user => {
                if (user.user_email && !uniqueUsers.has(user.user_email)) {
                    uniqueUsers.set(user.user_email, {
                        email: user.user_email,
                        firebase_uid: user.firebase_uid
                    });
                }
            });
    
            const userList = Array.from(uniqueUsers.values()).slice(0, maxUsersPerRun);
            results.totalUsers = userList.length;
            results.validUsers = userList.length;

            console.log(`📊 Processing ${results.totalUsers} unique users for streak notifications (cap ${maxUsersPerRun})`);

            // Process users in small batches to avoid overwhelming Firebase
            const batchSize = 5;
            for (let i = 0; i < userList.length; i += batchSize) {
                // Quota circuit breaker: once Firestore says RESOURCE_EXHAUSTED, stop the run.
                if (results.quotaExhausted) {
                    console.error(`❌ Firestore quota exhausted - aborting run after ${results.quotaErrorCount} quota errors`);
                    break;
                }
                const batch = userList.slice(i, i + batchSize);

                await Promise.all(
                    batch.map(async (user) => {
                        try {
                            await this.checkUserStreakStatusWithUid(user.email, user.firebase_uid, results);
                        } catch (error) {
                            results.errors.push({
                                user: hashUser(user.email),
                                error: error.message,
                                type: 'user_processing_failed'
                            });
                        }
                    })
                );

                // Small delay between batches
                if (i + batchSize < userList.length) {
                    await new Promise(resolve => setTimeout(resolve, 200));
                }
            }

            console.log(`📊 Streak check complete: ${results.notificationsSent} notifications sent`);
            return results;
    
        } catch (error) {
            console.error('❌ Streak notification check failed:', error);
            throw error;
        }
    }

    /**
     * Enhanced user streak checking with Firebase UID (no lookup needed)
     */
    async checkUserStreakStatusWithUid(userEmail, firebaseUid, results) {
        if (this.processedUsers.has(userEmail)) {
            return;
        }
        this.processedUsers.add(userEmail);
    
        try {
            // If no firebase_uid in Supabase, try to get it from Firebase Auth
            let uid = firebaseUid;
            if (!uid) {
                try {
                    const userRecord = await this.admin.auth().getUserByEmail(userEmail);
                    uid = userRecord.uid;
                } catch (authError) {
                    console.log(`⚠️ User ${hashUser(userEmail)} not found in Firebase Auth - skipping`);
                    return;
                }
            }
    
            const streakData = await this.calculateUserStreakFromFirebaseWithUid(userEmail, uid, results);
            if (!streakData) return;
    
            const hoursFromLastOpen = streakData.hoursFromLastOpen;
            const currentStreak = streakData.currentStreak;
            const daysSinceLastOpen = Math.floor(hoursFromLastOpen / 24);
    
            const shouldSendAtRisk = await this.shouldSendStreakAtRiskNotification(userEmail, currentStreak, hoursFromLastOpen);
            const shouldSendComeback = await this.shouldSendComebackNotification(userEmail, daysSinceLastOpen, streakData.bestStreak);
            const shouldSendCelebration = await this.shouldSendStreakCelebration(userEmail, currentStreak);
    
            if (shouldSendAtRisk) {
                await this.sendStreakAtRiskNotification(userEmail, currentStreak);
                results.atRiskUsers++;
                results.notificationsSent++;
            } else if (shouldSendComeback) {
                await this.sendComebackNotification(userEmail, streakData.bestStreak);
                results.comebackUsers++;
                results.notificationsSent++;
            } else if (shouldSendCelebration) {
                await this.sendStreakCelebrationNotification(userEmail, currentStreak);
                results.celebrationUsers++;
                results.notificationsSent++;
            }
    
        } catch (error) {
            results.errors.push({
                user: hashUser(userEmail),
                error: error.message,
                type: 'user_processing_failed'
            });
        }
    }

    /**
     * Enhanced Firebase data calculation with better error handling
     */
    async calculateUserStreakFromFirebaseWithUid(userEmail, firebaseUid, results = null) {
        try {
            const now = new Date();
            const thirtyDaysAgo = new Date(now.getTime() - (30 * 24 * 60 * 60 * 1000));
    
            const analyticsSnapshot = await this.db.collection('puzzle_analytics')
                .where('user_id', '==', firebaseUid)
                .where('event_name', '==', 'app_open')
                .where('timestamp', '>=', thirtyDaysAgo.getTime())
                .orderBy('timestamp', 'desc')
                .limit(30)
                .get();
    
            if (analyticsSnapshot.empty) {
                return null; // No app opens found
            }
    
            const appOpenDates = [];
            let lastOpenTimestamp = 0;
    
            analyticsSnapshot.forEach(doc => {
                const data = doc.data();
                const date = new Date(data.timestamp).toISOString().split('T')[0];
                
                if (!appOpenDates.includes(date)) {
                    appOpenDates.push(date);
                }
                
                if (data.timestamp > lastOpenTimestamp) {
                    lastOpenTimestamp = data.timestamp;
                }
            });
    
            appOpenDates.sort((a, b) => new Date(b) - new Date(a));
    
            const currentStreak = this.calculateStreakFromDates(appOpenDates);
            const bestStreak = Math.max(currentStreak, this.calculateMaxStreakFromDates(appOpenDates));
            const hoursFromLastOpen = (Date.now() - lastOpenTimestamp) / (1000 * 60 * 60);
    
            return {
                currentStreak,
                bestStreak,
                hoursFromLastOpen,
                lastOpenDate: new Date(lastOpenTimestamp).toISOString().split('T')[0],
                totalActiveDays: appOpenDates.length
            };
    
        } catch (error) {
            if (isQuotaError(error) && results) {
                // Log once per run, not once per user; the batch loop aborts on this flag.
                results.quotaErrorCount = (results.quotaErrorCount || 0) + 1;
                if (!results.quotaExhausted) {
                    results.quotaExhausted = true;
                    console.error('❌ Firestore RESOURCE_EXHAUSTED while calculating streaks (first occurrence this run)');
                }
                return null;
            }
            console.error(`❌ Error calculating streak for user ${hashUser(userEmail)}:`, error.message);
            return null;
        }
    }

    /**
     * Calculate current streak from array of dates
     */
    calculateStreakFromDates(sortedDates) {
        if (sortedDates.length === 0) return 0;

        let streak = 1;
        const today = new Date().toISOString().split('T')[0];
        const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().split('T')[0];

        // Check if user played today or yesterday to maintain streak
        if (sortedDates[0] !== today && sortedDates[0] !== yesterday) {
            return 0; // Streak is broken
        }

        // Count consecutive days
        for (let i = 1; i < sortedDates.length; i++) {
            const currentDate = new Date(sortedDates[i-1]);
            const nextDate = new Date(sortedDates[i]);
            const daysDiff = (currentDate - nextDate) / (1000 * 60 * 60 * 24);
            
            if (daysDiff === 1) {
                streak++;
            } else {
                break;
            }
        }

        return streak;
    }

    /**
     * Calculate maximum streak from historical dates
     */
    calculateMaxStreakFromDates(sortedDates) {
        if (sortedDates.length === 0) return 0;

        let maxStreak = 1;
        let currentStreak = 1;

        for (let i = 1; i < sortedDates.length; i++) {
            const currentDate = new Date(sortedDates[i-1]);
            const nextDate = new Date(sortedDates[i]);
            const daysDiff = (currentDate - nextDate) / (1000 * 60 * 60 * 24);
            
            if (daysDiff === 1) {
                currentStreak++;
            } else {
                maxStreak = Math.max(maxStreak, currentStreak);
                currentStreak = 1;
            }
        }

        return Math.max(maxStreak, currentStreak);
    }

    // ================================
    // NOTIFICATION DECISION LOGIC
    // ================================

    /**
     * Check if user should get "streak at risk" notification
     */
    async shouldSendStreakAtRiskNotification(userEmail, currentStreak, hoursFromLastOpen) {
        // Only send if user has a meaningful streak (3+ days)
        if (currentStreak < 3) return false;

        // Send if 20-26 hours since last open (before streak breaks)
        if (hoursFromLastOpen < 20 || hoursFromLastOpen > 26) return false;

        // Check if we already sent this notification recently
        const lastSent = await this.getLastNotificationTime(userEmail, 'streak_at_risk');
        if (lastSent && (Date.now() - lastSent) < 20 * 60 * 60 * 1000) { // 20 hours
            return false;
        }

        return true;
    }

    /**
     * Check if user should get "comeback" notification
     */
    async shouldSendComebackNotification(userEmail, daysSinceLastOpen, bestStreak) {
        // Send 1-2 days after streak breaks
        if (daysSinceLastOpen < 1 || daysSinceLastOpen > 2) return false;

        // Only for users who had a decent streak
        if (bestStreak < 3) return false;

        // Check if we already sent comeback notification recently
        const lastSent = await this.getLastNotificationTime(userEmail, 'comeback');
        if (lastSent && (Date.now() - lastSent) < 3 * 24 * 60 * 60 * 1000) { // 3 days
            return false;
        }

        return true;
    }

    /**
     * Check if user should get streak celebration
     */
    async shouldSendStreakCelebration(userEmail, currentStreak) {
        // Celebrate specific milestones
        const milestones = [3, 7, 14, 30, 50, 100];
        if (!milestones.includes(currentStreak)) return false;

        // Check if we already celebrated this milestone
        const lastCelebrated = await this.getLastCelebrationStreak(userEmail);
        if (lastCelebrated >= currentStreak) return false;

        return true;
    }

    // ================================
    // NOTIFICATION SENDING (FIXED)
    // ================================

    /**
     * Send notification using FCM directly (FIXED)
     */
    async sendNotificationToUser(userEmail, title, message, notificationType, streakValue) {
        try {
            // Get user's FCM tokens from Supabase
            const { data: tokens, error } = await this.supabase
                .from('user_notification_tokens')
                .select('fcm_token')
                .eq('user_email', userEmail)
                .eq('is_active', true);

            if (error || !tokens || tokens.length === 0) {
                console.log(`⚠️ No active FCM tokens for user ${hashUser(userEmail)}`);
                return false;
            }

            const fcmTokens = tokens.map(t => t.fcm_token).filter(t => t);
            
            if (fcmTokens.length === 0) {
                console.log(`⚠️ No valid FCM tokens for user ${hashUser(userEmail)}`);
                return false;
            }

            // Send via Firebase Cloud Messaging
            const fcmMessage = {
                notification: { title, body: message },
                tokens: fcmTokens
            };

            const response = await this.admin.messaging().sendMulticast(fcmMessage);
            
            if (response.successCount > 0) {
                await this.logNotificationSent(userEmail, notificationType, streakValue);
                console.log(`✅ Sent ${notificationType} notification to user ${hashUser(userEmail)} (${response.successCount}/${fcmTokens.length} tokens)`);
                return true;
            } else {
                console.error(`❌ Failed to send notification to user ${hashUser(userEmail)}: all tokens failed`);
                return false;
            }

        } catch (error) {
            console.error(`❌ Error sending notification to user ${hashUser(userEmail)}:`, error.message);
            return false;
        }
    }

    /**
     * Send "streak at risk" notification
     */
    async sendStreakAtRiskNotification(userEmail, streakDays) {
        const messages = {
            3: "Don't break your 3-day streak! 🔥",
            5: "Your 5-day streak is amazing! Don't stop now! 🚀",
            7: "You've got a week-long streak! Keep it alive! 🏆",
            default: `Don't break your ${streakDays}-day streak! 🔥`
        };

        const title = "🔥 Don't Break Your Streak!";
        const message = messages[streakDays] || messages.default;

        return await this.sendNotificationToUser(userEmail, title, message, 'streak_at_risk', streakDays);
    }

    /**
     * Send "comeback" notification
     */
    async sendComebackNotification(userEmail, bestStreak) {
        const title = "💪 Ready for a Comeback?";
        const message = bestStreak > 7 
            ? `Your best streak was ${bestStreak} days. Time to beat that record!`
            : "Every expert was once a beginner. Start a new streak today!";

        return await this.sendNotificationToUser(userEmail, title, message, 'comeback', bestStreak);
    }

    /**
     * Send streak celebration notification
     */
    async sendStreakCelebrationNotification(userEmail, streakDays) {
        const celebrations = {
            3: { title: "🎉 3-Day Streak!", message: "Amazing! You're building a great puzzle habit." },
            7: { title: "🏆 Week Warrior!", message: "Incredible! 7 days straight of brain training." },
            14: { title: "💎 Two Week Champion!", message: "You're on fire! 14 days of consistent play." },
            30: { title: "👑 Puzzle Master!", message: "30 days! You've built an incredible habit." },
            50: { title: "🌟 Legend Status!", message: "50 days! You're truly dedicated." },
            100: { title: "🏅 Century Club!", message: "100 days! You're an inspiration!" }
        };

        const celebration = celebrations[streakDays] || {
            title: `🎉 ${streakDays}-Day Streak!`,
            message: `Incredible! ${streakDays} days of consistent puzzle solving.`
        };

        const success = await this.sendNotificationToUser(userEmail, celebration.title, celebration.message, 'celebration', streakDays);
        
        if (success) {
            await this.updateLastCelebrationStreak(userEmail, streakDays);
        }
        
        return success;
    }

    // ================================
    // TRACKING AND DEDUPLICATION
    // ================================

    /**
     * Log notification sent to prevent duplicates
     */
    async logNotificationSent(userEmail, type, streakValue) {
        try {
            const { error } = await this.supabase
                .from('streak_notification_logs')
                .insert({
                    user_email: userEmail,
                    notification_type: type,
                    streak_value: streakValue,
                    sent_at: new Date().toISOString()
                });
            if (error) {
                console.error(`❌ Error logging notification for user ${hashUser(userEmail)}:`, error.message);
            }
        } catch (error) {
            console.error(`❌ Error logging notification for user ${hashUser(userEmail)}:`, error.message);
        }
    }

    /**
     * Get last notification time for type
     */
    async getLastNotificationTime(userEmail, type) {
        // Throws when the dedup table cannot be read: the caller records an error and sends
        // nothing. Returning null here used to mean "never sent", i.e. an hourly re-send.
        const { data, error } = await this.supabase
            .from('streak_notification_logs')
            .select('sent_at')
            .eq('user_email', userEmail)
            .eq('notification_type', type)
            .order('sent_at', { ascending: false })
            .limit(1);
        if (error) throw new Error(`dedup lookup failed: ${error.message}`);

        return data?.[0] ? new Date(data[0].sent_at).getTime() : null;
    }

    /**
     * Get last celebrated streak level
     */
    async getLastCelebrationStreak(userEmail) {
        const { data, error } = await this.supabase
            .from('streak_notification_logs')
            .select('streak_value')
            .eq('user_email', userEmail)
            .eq('notification_type', 'celebration')
            .order('streak_value', { ascending: false })
            .limit(1);
        if (error) throw new Error(`dedup lookup failed: ${error.message}`);

        return data?.[0]?.streak_value || 0;
    }

    /**
     * Update last celebration streak
     */
    async updateLastCelebrationStreak(userEmail, streakDays) {
        // This is handled by logNotificationSent, but could add specific tracking if needed
    }

    /**
     * Get service status and recent job history
     */
    getServiceStatus() {
        return {
            isRunning: this.isRunning,
            hasInterval: !!this.intervalId,
            lastRun: this.jobHistory.length > 0 ? this.jobHistory[0] : null,
            recentJobs: this.jobHistory.slice(0, 10),
            totalJobsRun: this.jobHistory.length
        };
    }

    /**
     * Add job result to in-memory history (keep last 50)
     */
    addToHistory(jobResult) {
        this.jobHistory.unshift(jobResult);
        if (this.jobHistory.length > 50) {
            this.jobHistory = this.jobHistory.slice(0, 50);
        }
    }

    /**
     * Clear processed users set (call after each run)
     */
    clearProcessedUsers() {
        this.processedUsers.clear();
    }

    // ================================
    // ERROR LOGGING TO SUPABASE
    // ================================

    /**
     * Log job start to Supabase
     */
    async logJobStart(jobId, startTime) {
        try {
            const { error } = await this.supabase
                .from('streak_notification_jobs')
                .insert({
                    job_id: jobId,
                    status: 'running',
                    started_at: startTime.toISOString(),
                    total_users_checked: 0,
                    notifications_sent: 0,
                    errors_count: 0
                });
            if (error) console.error('❌ Failed to log job start:', error.message);
        } catch (error) {
            console.error('❌ Failed to log job start:', error.message);
        }
    }

    /**
     * Log successful job completion
     */
    async logJobCompletion(jobResult) {
        try {
            // supabase-js does not throw on failure; it returns { error }. Ignoring it left rows 'running'.
            const { error } = await this.supabase
                .from('streak_notification_jobs')
                .update({
                    status: 'completed',
                    completed_at: jobResult.endTime,
                    duration_seconds: jobResult.duration,
                    total_users_checked: jobResult.totalUsers,
                    notifications_sent: jobResult.notificationsSent,
                    at_risk_users: jobResult.atRiskUsers || 0,
                    comeback_users: jobResult.comebackUsers || 0,
                    celebration_users: jobResult.celebrationUsers || 0,
                    errors_count: jobResult.errors.length,
                    error_details: jobResult.errors.length > 0 ? JSON.stringify(jobResult.errors) : null
                })
                .eq('job_id', jobResult.jobId);
            if (error) console.error('❌ Failed to close job row (completion):', error.message);
        } catch (error) {
            console.error('❌ Failed to log job completion:', error.message);
        }
    }

    /**
     * Log job failure with detailed error information
     */
    async logJobFailure(jobResult, error) {
        try {
            const { error: updateError } = await this.supabase
                .from('streak_notification_jobs')
                .update({
                    status: 'failed',
                    completed_at: jobResult.endTime,
                    duration_seconds: jobResult.duration,
                    total_users_checked: jobResult.totalUsers,
                    notifications_sent: jobResult.notificationsSent,
                    errors_count: 1,
                    error_details: JSON.stringify({
                        message: error.message,
                        stack: error.stack,
                        timestamp: new Date().toISOString()
                    })
                })
                .eq('job_id', jobResult.jobId);
            if (updateError) console.error('❌ Failed to close job row (failure):', updateError.message);
        } catch (logError) {
            console.error('❌ Failed to log job failure:', logError.message);
        }
    }

    /**
     * Log individual user processing errors
     */
    async logUserError(userEmail, errorType, errorMessage, jobId) {
        try {
            await this.supabase
                .from('streak_notification_errors')
                .insert({
                    job_id: jobId,
                    user_email: userEmail,
                    error_type: errorType,
                    error_message: errorMessage.substring(0, 500), // Limit message length
                    timestamp: new Date().toISOString()
                });
        } catch (error) {
            console.error('❌ Failed to log user error:', error);
        }
    }
}

export const firebaseStreakNotificationService = new FirebaseStreakNotificationService();