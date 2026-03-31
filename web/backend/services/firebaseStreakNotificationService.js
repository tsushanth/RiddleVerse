// services/firebaseStreakNotificationService.js
import { admin, db } from '../config/firebaseAdmin.js';
import { supabase } from '../config/database.js';

class FirebaseStreakNotificationService {
    constructor() {
        this.processedUsers = new Set(); // Prevent duplicate processing in same run
        this.isRunning = false;
        this.intervalId = null;
        this.jobHistory = [];
    }

    /**
     * Start the periodic streak notification service
     */
    startPeriodicService(intervalMinutes = 60) {
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
        if (this.isRunning) {
            console.log('⏭️ Skipping streak check - already running');
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
            this.isRunning = false;
            this.addToHistory(jobResult);
        }
    }

    /**
     * Main function: Check all users for streak-based notifications
     * FIXED: Now uses Supabase for tokens, Firebase for analytics
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
                errors: []
            };
    
            // FIXED: Get users from Supabase notification tokens (where the data actually is)
            const { data: tokenUsers, error } = await supabase
                .from('user_notification_tokens')
                .select('user_email, firebase_uid')
                .eq('is_active', true);
    
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
    
            const userList = Array.from(uniqueUsers.values());
            results.totalUsers = userList.length;
            results.validUsers = userList.length;
    
            console.log(`📊 Processing ${results.totalUsers} unique users for streak notifications`);
    
            // Process users in batches to avoid overwhelming Firebase
            const batchSize = 10;
            for (let i = 0; i < userList.length; i += batchSize) {
                const batch = userList.slice(i, i + batchSize);
                
                await Promise.all(
                    batch.map(async (user) => {
                        try {
                            await this.checkUserStreakStatusWithUid(user.email, user.firebase_uid, results);
                        } catch (error) {
                            results.errors.push({
                                userEmail: user.email,
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
                    const userRecord = await admin.auth().getUserByEmail(userEmail);
                    uid = userRecord.uid;
                } catch (authError) {
                    console.log(`⚠️ User ${userEmail} not found in Firebase Auth - skipping`);
                    return;
                }
            }
    
            const streakData = await this.calculateUserStreakFromFirebaseWithUid(userEmail, uid);
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
                userEmail,
                error: error.message,
                type: 'user_processing_failed'
            });
        }
    }

    /**
     * Enhanced Firebase data calculation with better error handling
     */
    async calculateUserStreakFromFirebaseWithUid(userEmail, firebaseUid) {
        try {
            const now = new Date();
            const thirtyDaysAgo = new Date(now.getTime() - (30 * 24 * 60 * 60 * 1000));
    
            const analyticsSnapshot = await db.collection('puzzle_analytics')
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
            console.error(`❌ Error calculating streak for ${userEmail}:`, error.message);
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
            const { data: tokens, error } = await supabase
                .from('user_notification_tokens')
                .select('fcm_token')
                .eq('user_email', userEmail)
                .eq('is_active', true);

            if (error || !tokens || tokens.length === 0) {
                console.log(`⚠️ No active FCM tokens for ${userEmail}`);
                return false;
            }

            const fcmTokens = tokens.map(t => t.fcm_token).filter(t => t);
            
            if (fcmTokens.length === 0) {
                console.log(`⚠️ No valid FCM tokens for ${userEmail}`);
                return false;
            }

            // Send via Firebase Cloud Messaging
            const fcmMessage = {
                notification: { title, body: message },
                tokens: fcmTokens
            };

            const response = await admin.messaging().sendMulticast(fcmMessage);
            
            if (response.successCount > 0) {
                await this.logNotificationSent(userEmail, notificationType, streakValue);
                console.log(`✅ Sent ${notificationType} notification to ${userEmail} (${response.successCount}/${fcmTokens.length} tokens)`);
                return true;
            } else {
                console.error(`❌ Failed to send notification to ${userEmail}: all tokens failed`);
                return false;
            }

        } catch (error) {
            console.error(`❌ Error sending notification to ${userEmail}:`, error.message);
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
            await supabase
                .from('streak_notification_logs')
                .insert({
                    user_email: userEmail,
                    notification_type: type,
                    streak_value: streakValue,
                    sent_at: new Date().toISOString()
                });
        } catch (error) {
            console.error(`❌ Error logging notification:`, error);
        }
    }

    /**
     * Get last notification time for type
     */
    async getLastNotificationTime(userEmail, type) {
        try {
            const { data } = await supabase
                .from('streak_notification_logs')
                .select('sent_at')
                .eq('user_email', userEmail)
                .eq('notification_type', type)
                .order('sent_at', { ascending: false })
                .limit(1);

            return data?.[0] ? new Date(data[0].sent_at).getTime() : null;
        } catch (error) {
            return null;
        }
    }

    /**
     * Get last celebrated streak level
     */
    async getLastCelebrationStreak(userEmail) {
        try {
            const { data } = await supabase
                .from('streak_notification_logs')
                .select('streak_value')
                .eq('user_email', userEmail)
                .eq('notification_type', 'celebration')
                .order('streak_value', { ascending: false })
                .limit(1);

            return data?.[0]?.streak_value || 0;
        } catch (error) {
            return 0;
        }
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
            await supabase
                .from('streak_notification_jobs')
                .insert({
                    job_id: jobId,
                    status: 'running',
                    started_at: startTime.toISOString(),
                    total_users_checked: 0,
                    notifications_sent: 0,
                    errors_count: 0
                });
        } catch (error) {
            console.error('❌ Failed to log job start:', error);
        }
    }

    /**
     * Log successful job completion
     */
    async logJobCompletion(jobResult) {
        try {
            await supabase
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
        } catch (error) {
            console.error('❌ Failed to log job completion:', error);
        }
    }

    /**
     * Log job failure with detailed error information
     */
    async logJobFailure(jobResult, error) {
        try {
            await supabase
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
        } catch (logError) {
            console.error('❌ Failed to log job failure:', logError);
        }
    }

    /**
     * Log individual user processing errors
     */
    async logUserError(userEmail, errorType, errorMessage, jobId) {
        try {
            await supabase
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