// services/notificationService.js
import { supabase } from '../config/database.js';
import { admin, db } from '../config/firebaseAdmin.js';

class NotificationService {
    constructor() {
        this.timezoneJobsRunning = new Map();
        this.activeJobs = new Map();
    }

    /**
     * Create a notification configuration
     */
    async createNotificationConfig(config) {
        try {
            const {
                name,
                message,
                title = 'Puzzle Notification',
                targetTime = '12:00', // Default noon
                rules = { selectAllUsers: true },
                isActive = true,
                scheduleType = 'daily',
                createdBy = 'system',
                priority = 'normal'
            } = config;

            if (!name || !message) {
                return {
                    success: false,
                    error: 'Name and message are required'
                };
            }

            // Validate time format (HH:MM)
            const timeRegex = /^([01]\d|2[0-3]):([0-5]\d)$/;
            if (!timeRegex.test(targetTime)) {
                return {
                    success: false,
                    error: 'Invalid time format. Use HH:MM (24-hour format)'
                };
            }

            const notificationConfig = {
                name,
                title,
                message,
                target_time: targetTime,
                rules: JSON.stringify(rules),
                is_active: isActive,
                schedule_type: scheduleType,
                priority: priority,
                created_by: createdBy,
                created_at: new Date().toISOString(),
                updated_at: new Date().toISOString(),
                last_sent_at: null,
                next_scheduled_at: null,
                total_sent: 0,
                success_count: 0,
                failure_count: 0
            };

            const { data, error } = await supabase
                .from('notification_configs')
                .insert([notificationConfig])
                .select()
                .single();

            if (error) {
                throw error;
            }

            console.log(`✅ Created notification config: ${name}`);
            
            return {
                success: true,
                config: data,
                message: `Notification config '${name}' created successfully`
            };

        } catch (error) {
            console.error('❌ Error creating notification config:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Get all active notification configurations
     */
    async getActiveConfigs() {
        try {
            const { data: configs, error } = await supabase
                .from('notification_configs')
                .select('*')
                .eq('is_active', true)
                .order('created_at', { ascending: false });

            if (error) {
                throw error;
            }

            return {
                success: true,
                configs: configs || []
            };

        } catch (error) {
            console.error('❌ Error fetching active configs:', error);
            return {
                success: false,
                error: error.message,
                configs: []
            };
        }
    }

    /**
     * Get users based on notification rules
     */
    async getUsersForNotification(rules) {
        try {
            console.log('📋 Getting users for notification with rules:', rules);

            let eligibleUsers = [];

            if (rules.selectAllUsers) {
                // Get all users with active FCM tokens
                const { data: tokens, error } = await supabase
                    .from('user_notification_tokens')
                    .select('user_email, platform, fcm_token, last_used')
                    .eq('is_active', true);

                if (error) {
                    throw new Error(`Failed to fetch user tokens: ${error.message}`);
                }

                // Group by user email and include timezone info if available
                const userMap = new Map();
                for (const token of tokens) {
                    if (!userMap.has(token.user_email)) {
                        // Try to get user timezone from Firebase
                        let userTimezone = 'UTC';
                        try {
                            const userDoc = await db.collection('users').doc(token.user_email).get();
                            if (userDoc.exists && userDoc.data().timezone) {
                                userTimezone = userDoc.data().timezone;
                            }
                        } catch (timezoneError) {
                            console.warn(`⚠️ Could not get timezone for ${token.user_email}:`, timezoneError.message);
                        }

                        userMap.set(token.user_email, {
                            email: token.user_email,
                            tokens: [],
                            timezone: userTimezone,
                            platforms: new Set()
                        });
                    }

                    const user = userMap.get(token.user_email);
                    user.tokens.push({
                        fcm_token: token.fcm_token,
                        platform: token.platform,
                        last_used: token.last_used
                    });
                    user.platforms.add(token.platform);
                }

                eligibleUsers = Array.from(userMap.values()).map(user => ({
                    ...user,
                    platforms: Array.from(user.platforms)
                }));

            } else {
                // Handle other rule types (future implementation)
                if (rules.userEmails && rules.userEmails.length > 0) {
                    // Specific users
                    const { data: tokens, error } = await supabase
                        .from('user_notification_tokens')
                        .select('user_email, platform, fcm_token, last_used')
                        .eq('is_active', true)
                        .in('user_email', rules.userEmails);

                    if (error) {
                        throw new Error(`Failed to fetch specific user tokens: ${error.message}`);
                    }

                    // Process similar to selectAllUsers but filtered
                    eligibleUsers = rules.userEmails.map(email => {
                        const userTokens = tokens.filter(t => t.user_email === email);
                        return {
                            email,
                            tokens: userTokens,
                            timezone: 'UTC', // Default, could be enhanced
                            platforms: [...new Set(userTokens.map(t => t.platform))]
                        };
                    }).filter(user => user.tokens.length > 0);
                }

                // Add more rule types here (premium users, specific topics, etc.)
            }

            console.log(`📋 Found ${eligibleUsers.length} eligible users for notification`);
            return {
                success: true,
                users: eligibleUsers,
                totalUsers: eligibleUsers.length,
                totalTokens: eligibleUsers.reduce((sum, user) => sum + user.tokens.length, 0)
            };

        } catch (error) {
            console.error('❌ Error getting users for notification:', error);
            return {
                success: false,
                error: error.message,
                users: []
            };
        }
    }

    async debugFCMTokens(userEmail) {
        try {
            console.log(`🔍 Debugging FCM tokens for user: ${userEmail}`);
            
            const { data: tokens, error: tokenError } = await supabase
                .from('user_notification_tokens')
                .select('*')
                .eq('user_email', userEmail);
    
            if (tokenError) {
                console.error('❌ Error fetching tokens:', tokenError);
                return { error: tokenError.message };
            }
    
            console.log(`📱 Found ${tokens?.length || 0} total tokens for ${userEmail}`);
            
            if (tokens && tokens.length > 0) {
                tokens.forEach((token, index) => {
                    console.log(`Token ${index + 1}:`, {
                        platform: token.platform,
                        is_active: token.is_active,
                        created_at: token.created_at,
                        token_preview: token.fcm_token ? token.fcm_token.substring(0, 20) + '...' : 'null'
                    });
                });
    
                const activeTokens = tokens.filter(token => token.is_active && token.fcm_token);
                console.log(`✅ Active tokens: ${activeTokens.length}`);
                
                return {
                    success: true,
                    totalTokens: tokens.length,
                    activeTokens: activeTokens.length,
                    tokens: activeTokens
                };
            } else {
                console.warn('⚠️ No tokens found for user');
                return {
                    success: false,
                    error: 'No FCM tokens found for user'
                };
            }
    
        } catch (error) {
            console.error('💥 Error in debugFCMTokens:', error);
            return { error: error.message };
        }
    }
    
    async testNotificationSystem(userEmail, testMessage = "Test notification from puzzle system") {
        console.log(`🧪 Testing notification system for user: ${userEmail}`);
        
        try {
            // Step 1: Check FCM tokens
            console.log(`📱 Step 1: Checking FCM tokens...`);
            const tokenResult = await debugFCMTokens(userEmail);
            
            if (!tokenResult.success) {
                return {
                    success: false,
                    step: 'token_check',
                    error: tokenResult.error,
                    recommendation: 'User needs to enable notifications in the app'
                };
            }
    
            if (tokenResult.activeTokens === 0) {
                return {
                    success: false,
                    step: 'token_validation',
                    error: 'No active FCM tokens found',
                    recommendation: 'User needs to enable notifications and ensure app is updated'
                };
            }
    
            // Step 2: Send test notification using your existing service
            console.log(`🚀 Step 2: Sending test notification via NotificationService...`);
            
            const testResult = await notificationService.sendTestNotification(
                [userEmail], 
                '🧪 Test Notification', 
                testMessage
            );
    
            if (!testResult.success) {
                return {
                    success: false,
                    step: 'notification_send',
                    error: 'NotificationService test failed',
                    details: testResult,
                    recommendation: 'Check NotificationService logs and Firebase config'
                };
            }
    
            const userResult = testResult.results.results.find(r => r.email === userEmail);
            
            return {
                success: userResult?.success || false,
                message: userResult?.success ? 'Test notification sent successfully!' : 'Test notification failed',
                results: {
                    tokensFound: tokenResult.totalTokens,
                    activeTokens: tokenResult.activeTokens,
                    notificationResult: userResult,
                    serviceResults: testResult.results
                },
                recommendation: userResult?.success ? 
                    'Notification system is working! Check your phone.' : 
                    `Notification failed: ${userResult?.error || 'Unknown error'}`
            };
    
        } catch (error) {
            console.error('💥 Error in testNotificationSystem:', error);
            return {
                success: false,
                step: 'system_error',
                error: error.message,
                recommendation: 'Check server logs and Firebase configuration'
            };
        }
    }

    /**
     * Send notification to users in their local timezone
     */
    async sendTimedNotification(configId) {
        const jobId = `timed_notification_${configId}_${Date.now()}`;
        
        try {
            console.log(`🕐 Starting timed notification job: ${jobId}`);
            this.activeJobs.set(jobId, { status: 'running', startTime: Date.now() });

            // Get notification config
            const { data: config, error: configError } = await supabase
                .from('notification_configs')
                .select('*')
                .eq('id', configId)
                .eq('is_active', true)
                .single();

            if (configError || !config) {
                throw new Error(`Notification config not found or inactive: ${configError?.message}`);
            }

            const rules = JSON.parse(config.rules);
            const targetTime = config.target_time; // e.g., "12:00"

            // Get eligible users
            const usersResult = await this.getUsersForNotification(rules);
            if (!usersResult.success || usersResult.users.length === 0) {
                throw new Error('No eligible users found for notification');
            }

            console.log(`📋 Processing ${usersResult.users.length} users for timed notification`);

            // Group users by timezone for batch sending
            const timezoneGroups = new Map();
            for (const user of usersResult.users) {
                const tz = user.timezone || 'UTC';
                if (!timezoneGroups.has(tz)) {
                    timezoneGroups.set(tz, []);
                }
                timezoneGroups.get(tz).push(user);
            }

            console.log(`🌍 Grouped users into ${timezoneGroups.size} timezone groups`);

            // Process each timezone group
            const results = {
                totalUsers: usersResult.users.length,
                processedTimezones: 0,
                sent: 0,
                failed: 0,
                errors: [],
                timezoneResults: new Map()
            };

            for (const [timezone, users] of timezoneGroups) {
                try {
                    console.log(`🌍 Processing timezone ${timezone} with ${users.length} users`);

                    const localTimeResult = await this.sendNotificationAtLocalTime(
                        users, 
                        config, 
                        targetTime, 
                        timezone,
                        jobId
                    );

                    results.timezoneResults.set(timezone, localTimeResult);
                    results.processedTimezones++;
                    results.sent += localTimeResult.sent;
                    results.failed += localTimeResult.failed;
                    
                    if (localTimeResult.errors) {
                        results.errors.push(...localTimeResult.errors);
                    }

                } catch (timezoneError) {
                    console.error(`❌ Error processing timezone ${timezone}:`, timezoneError);
                    results.failed += users.length;
                    results.errors.push({
                        timezone,
                        error: timezoneError.message,
                        affectedUsers: users.length
                    });
                }
            }

            // Update notification config stats
            await this.updateNotificationStats(configId, results);

            // Log the job completion
            await this.logNotificationJob(jobId, configId, config.name, results);

            this.activeJobs.delete(jobId);

            console.log(`✅ Timed notification completed: ${results.sent} sent, ${results.failed} failed across ${results.processedTimezones} timezones`);

            return {
                success: true,
                jobId,
                configName: config.name,
                results
            };

        } catch (error) {
            console.error(`❌ Timed notification job failed:`, error);
            this.activeJobs.delete(jobId);

            // Log the failed job
            await this.logNotificationJob(jobId, configId, 'unknown', {
                totalUsers: 0,
                sent: 0,
                failed: 0,
                error: error.message
            });

            return {
                success: false,
                jobId,
                error: error.message
            };
        }
    }

    /**
     * Send notification at local time for a specific timezone group
     */
    async sendNotificationAtLocalTime(users, config, targetTime, timezone, jobId) {
        console.log(`🕐 Sending notification at local time ${targetTime} for timezone ${timezone}`);

        try {
            // Check if it's the right time in this timezone
            const now = new Date();
            const localTime = now.toLocaleTimeString('en-US', { 
                timeZone: timezone, 
                hour12: false, 
                hour: '2-digit', 
                minute: '2-digit' 
            });

            console.log(`🕐 Current time in ${timezone}: ${localTime}, target: ${targetTime}`);

            // For immediate sending (can be enhanced with scheduling logic)
            const isTimeToSend = true; // Or implement actual time checking

            if (!isTimeToSend) {
                console.log(`⏰ Not time to send yet for ${timezone}. Current: ${localTime}, Target: ${targetTime}`);
                return {
                    sent: 0,
                    failed: 0,
                    scheduled: users.length,
                    message: 'Scheduled for later'
                };
            }

            // Collect all FCM tokens for this timezone group
            const allTokens = [];
            for (const user of users) {
                allTokens.push(...user.tokens.map(t => t.fcm_token));
            }

            // Create notification content
            const notificationData = {
                title: config.title,
                body: config.message,
                data: {
                    type: 'scheduled_notification',
                    configId: config.id.toString(),
                    configName: config.name,
                    timezone: timezone,
                    targetTime: targetTime,
                    sentAt: new Date().toISOString(),
                    jobId: jobId
                }
            };

            console.log(`📱 Sending to ${allTokens.length} tokens in ${timezone}`);

            // Send the notification
            const sendResult = await this.sendMulticastNotification(allTokens, notificationData);

            // Log individual user notifications
            for (const user of users) {
                await this.logUserNotification(
                    user.email, 
                    config, 
                    sendResult.success, 
                    timezone, 
                    sendResult.error
                );
            }

            return {
                sent: sendResult.success ? users.length : 0,
                failed: sendResult.success ? 0 : users.length,
                tokens: allTokens.length,
                fcmResult: sendResult
            };

        } catch (error) {
            console.error(`❌ Error sending at local time for ${timezone}:`, error);
            return {
                sent: 0,
                failed: users.length,
                error: error.message
            };
        }
    }

    /**
     * Send multicast notification using Firebase Admin
     */
    async sendMulticastNotification(tokens, notificationData) {
        try {
            if (tokens.length === 0) {
                return { success: false, error: 'No tokens provided' };
            }

            const message = {
                notification: {
                    title: notificationData.title,
                    body: notificationData.body
                },
                data: {
                    // Convert all data to strings (FCM requirement)
                    ...Object.fromEntries(
                        Object.entries(notificationData.data).map(([key, value]) => [
                            key, 
                            typeof value === 'string' ? value : JSON.stringify(value)
                        ])
                    )
                },
                tokens: tokens
            };

            console.log(`📱 Sending FCM notification to ${tokens.length} tokens`);

            const response = await admin.messaging().sendEachForMulticast(message);
            
            console.log(`📱 FCM Result: ${response.successCount} success, ${response.failureCount} failures`);

            // Handle token cleanup for invalid tokens
            if (response.failureCount > 0) {
                await this.handleFailedTokens(tokens, response.responses);
            }

            return {
                success: response.successCount > 0,
                successCount: response.successCount,
                failureCount: response.failureCount,
                responses: response.responses,
                error: response.failureCount === tokens.length ? 'All tokens failed' : null
            };

        } catch (error) {
            console.error(`❌ FCM send error:`, error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Handle failed FCM tokens (cleanup invalid ones)
     */
    async handleFailedTokens(tokens, responses) {
        const invalidTokens = [];
        
        responses.forEach((response, index) => {
            if (!response.success) {
                const errorCode = response.error?.code;
                if (errorCode === 'messaging/registration-token-not-registered' || 
                    errorCode === 'messaging/invalid-registration-token') {
                    invalidTokens.push(tokens[index]);
                }
            }
        });

        if (invalidTokens.length > 0) {
            console.log(`🧹 Cleaning up ${invalidTokens.length} invalid FCM tokens`);
            
            try {
                const { error } = await supabase
                    .from('user_notification_tokens')
                    .update({ is_active: false })
                    .in('fcm_token', invalidTokens);

                if (error) {
                    console.error('❌ Error cleaning up invalid tokens:', error);
                } else {
                    console.log(`✅ Deactivated ${invalidTokens.length} invalid tokens`);
                }
            } catch (cleanupError) {
                console.error('❌ Token cleanup failed:', cleanupError);
            }
        }
    }

    /**
     * Update notification config statistics
     */
    async updateNotificationStats(configId, results) {
        try {
            const { error } = await supabase
                .from('notification_configs')
                .update({
                    last_sent_at: new Date().toISOString(),
                    total_sent: results.sent,
                    success_count: results.sent,
                    failure_count: results.failed,
                    updated_at: new Date().toISOString()
                })
                .eq('id', configId);

            if (error) {
                console.error('❌ Error updating notification stats:', error);
            }

        } catch (error) {
            console.error('❌ Error in updateNotificationStats:', error);
        }
    }

    /**
     * Log notification job
     */
    async logNotificationJob(jobId, configId, configName, results) {
        try {
            const jobLog = {
                job_id: jobId,
                config_id: configId,
                config_name: configName,
                status: results.error ? 'failed' : 'completed',
                total_users: results.totalUsers || 0,
                successful_sends: results.sent || 0,
                failed_sends: results.failed || 0,
                processed_timezones: results.processedTimezones || 0,
                error_message: results.error || null,
                started_at: new Date().toISOString(),
                completed_at: new Date().toISOString()
            };

            await supabase
                .from('notification_jobs')
                .insert([jobLog]);

            console.log(`📊 Logged notification job: ${jobId}`);

        } catch (error) {
            console.error('❌ Error logging notification job:', error);
        }
    }

    /**
     * Log individual user notifications
     */
    async logUserNotification(userEmail, config, success, timezone, error = null) {
        try {
            const notificationLog = {
                user_email: userEmail,
                config_id: config.id,
                config_name: config.name,
                notification_type: 'scheduled',
                title: config.title,
                body: config.message,
                timezone: timezone,
                success: success,
                error_message: error,
                sent_at: new Date().toISOString()
            };

            await supabase
                .from('notification_logs')
                .insert([notificationLog]);

        } catch (logError) {
            console.error('❌ Error logging user notification:', logError);
        }
    }

    /**
     * Get notification statistics
     */
    async getNotificationStats(configId = null, days = 30) {
        try {
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - days);

            let query = supabase
                .from('notification_logs')
                .select('*')
                .gte('sent_at', cutoffDate.toISOString());

            if (configId) {
                query = query.eq('config_id', configId);
            }

            const { data: logs, error } = await query;

            if (error) {
                throw error;
            }

            const stats = {
                totalNotifications: logs.length,
                successfulNotifications: logs.filter(l => l.success).length,
                failedNotifications: logs.filter(l => !l.success).length,
                uniqueUsers: new Set(logs.map(l => l.user_email)).size,
                configBreakdown: {},
                timezoneBreakdown: {},
                dailyBreakdown: {},
                recentFailures: logs.filter(l => !l.success).slice(0, 10)
            };

            // Analyze by config
            logs.forEach(log => {
                if (!stats.configBreakdown[log.config_name]) {
                    stats.configBreakdown[log.config_name] = { sent: 0, failed: 0 };
                }
                if (log.success) {
                    stats.configBreakdown[log.config_name].sent++;
                } else {
                    stats.configBreakdown[log.config_name].failed++;
                }
            });

            // Analyze by timezone
            logs.forEach(log => {
                const tz = log.timezone || 'Unknown';
                if (!stats.timezoneBreakdown[tz]) {
                    stats.timezoneBreakdown[tz] = { sent: 0, failed: 0 };
                }
                if (log.success) {
                    stats.timezoneBreakdown[tz].sent++;
                } else {
                    stats.timezoneBreakdown[tz].failed++;
                }
            });

            // Analyze by day
            logs.forEach(log => {
                const date = log.sent_at.split('T')[0];
                if (!stats.dailyBreakdown[date]) {
                    stats.dailyBreakdown[date] = { sent: 0, failed: 0 };
                }
                if (log.success) {
                    stats.dailyBreakdown[date].sent++;
                } else {
                    stats.dailyBreakdown[date].failed++;
                }
            });

            return {
                success: true,
                stats,
                period: `${days} days`
            };

        } catch (error) {
            console.error('❌ Error getting notification stats:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Test notification to specific users
     */
    async sendTestNotification(userEmails, title, message) {
        try {
            console.log(`🧪 Sending test notification to ${userEmails.length} users`);

            const testResults = {
                sent: 0,
                failed: 0,
                results: []
            };

            for (const email of userEmails) {
                try {
                    // Get user's tokens
                    const { data: tokens, error: tokenError } = await supabase
                        .from('user_notification_tokens')
                        .select('fcm_token, platform')
                        .eq('user_email', email)
                        .eq('is_active', true);

                    if (tokenError || !tokens || tokens.length === 0) {
                        testResults.failed++;
                        testResults.results.push({
                            email,
                            success: false,
                            error: 'No active tokens found'
                        });
                        continue;
                    }

                    const notificationData = {
                        title: title || '🧪 Test Notification',
                        body: message || 'This is a test notification from your puzzle app!',
                        data: {
                            type: 'test',
                            userEmail: email,
                            sentAt: new Date().toISOString()
                        }
                    };

                    const userTokens = tokens.map(t => t.fcm_token);
                    const sendResult = await this.sendMulticastNotification(userTokens, notificationData);

                    if (sendResult.success) {
                        testResults.sent++;
                        testResults.results.push({
                            email,
                            success: true,
                            tokens: userTokens.length,
                            platforms: tokens.map(t => t.platform)
                        });
                    } else {
                        testResults.failed++;
                        testResults.results.push({
                            email,
                            success: false,
                            error: sendResult.error
                        });
                    }

                } catch (userError) {
                    testResults.failed++;
                    testResults.results.push({
                        email,
                        success: false,
                        error: userError.message
                    });
                }
            }

            console.log(`🧪 Test notification complete: ${testResults.sent} sent, ${testResults.failed} failed`);

            return {
                success: true,
                results: testResults
            };

        } catch (error) {
            console.error('❌ Error sending test notification:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Get active jobs status
     */
    getActiveJobs() {
        const jobs = [];
        for (const [jobId, jobInfo] of this.activeJobs) {
            jobs.push({
                jobId,
                status: jobInfo.status,
                startTime: jobInfo.startTime,
                duration: Date.now() - jobInfo.startTime
            });
        }
        return jobs;
    }
}

// Export singleton instance
export const notificationService = new NotificationService();