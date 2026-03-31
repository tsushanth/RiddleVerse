import { supabase } from '../config/database.js';

class UserLimitService {
    constructor() {
        this.debugMode = true;
        this.sessionRequests = new Map();
        this.configCache = new Map();
        this.cacheExpiry = new Map();
        this.cacheDuration = 5 * 60 * 1000; // 5 minutes
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const prefix = type === 'error' ? '[ERROR]' : type === 'warn' ? '[WARN]' : '[INFO]';
            console.log(`${prefix} [${timestamp}] USER_LIMITS: ${message}`);
        }
    }

    /**
     * Check if user can generate puzzles
     */
    async checkUserLimits(userEmail, puzzleType, ipAddress, userAgent) {
        try {
            const sessionId = this.generateSessionId(userEmail, ipAddress);
            const now = new Date();
            const today = now.toISOString().split('T')[0];
            const thisMonth = now.toISOString().slice(0, 7) + '-01';

            this.debugLog(`Checking limits for ${userEmail}, puzzle: ${puzzleType}`);

            // Check for rapid-fire requests
            if (await this.isRapidFireAbuse(sessionId)) {
                this.debugLog(`Rapid fire abuse detected for ${userEmail}`, 'warn');
                return {
                    allowed: false,
                    reason: 'rapid_fire_detected',
                    message: 'Too many requests in short time. Please slow down.',
                    retryAfter: 60
                };
            }

            // Check system-wide emergency brake
            const emergencyBrake = await this.checkEmergencyBrake();
            if (!emergencyBrake.allowed) {
                this.debugLog('Emergency brake activated', 'error');
                return emergencyBrake;
            }

            // Get or create user limits
            let userLimits = await this.getUserLimits(userEmail);
            if (!userLimits) {
                userLimits = await this.createUserLimits(userEmail);
            }

            // Reset counters if needed
            userLimits = await this.resetCountersIfNeeded(userLimits, today, thisMonth);

            // Check daily limit
            if (userLimits.daily_used >= userLimits.daily_limit) {
                this.debugLog(`Daily limit exceeded for ${userEmail}: ${userLimits.daily_used}/${userLimits.daily_limit}`);
                await this.logUsageAttempt(userEmail, puzzleType, false, 'daily_limit_exceeded', ipAddress, userAgent, sessionId);
                return {
                    allowed: false,
                    reason: 'daily_limit_exceeded',
                    limits: this.formatLimits(userLimits),
                    resetTime: this.getNextResetTime('daily'),
                    upgradeOptions: await this.getUpgradeOptions(userLimits.tier)
                };
            }

            // Check monthly limit
            if (userLimits.monthly_used >= userLimits.monthly_limit) {
                this.debugLog(`Monthly limit exceeded for ${userEmail}: ${userLimits.monthly_used}/${userLimits.monthly_limit}`);
                await this.logUsageAttempt(userEmail, puzzleType, false, 'monthly_limit_exceeded', ipAddress, userAgent, sessionId);
                return {
                    allowed: false,
                    reason: 'monthly_limit_exceeded',
                    limits: this.formatLimits(userLimits),
                    resetTime: this.getNextResetTime('monthly'),
                    upgradeOptions: await this.getUpgradeOptions(userLimits.tier)
                };
            }

            // Calculate progressive delay for heavy usage
            const delay = await this.calculateProgressiveDelay(userLimits);

            this.debugLog(`Limits check passed for ${userEmail}. Daily: ${userLimits.daily_used}/${userLimits.daily_limit}, Monthly: ${userLimits.monthly_used}/${userLimits.monthly_limit}`);
            
            return {
                allowed: true,
                limits: this.formatLimits(userLimits),
                progressiveDelay: delay,
                sessionId
            };

        } catch (error) {
            this.debugLog(`Error checking limits for ${userEmail}: ${error.message}`, 'error');
            // Fail open - allow the request but log the error
            return {
                allowed: true,
                error: error.message,
                fallback: true
            };
        }
    }

    /**
     * Record successful generation
     */
    async recordGeneration(userEmail, puzzleType, difficulty, success, ipAddress, userAgent, sessionId, generationMethod = 'ai') {
        try {
            const costConfig = await this.getConfig('cost_estimates');
            const estimatedCost = success ? (costConfig[puzzleType] || costConfig.default || 0.02) : 0;
    
            this.debugLog(`Recording generation for ${userEmail}: ${puzzleType} (${difficulty}) - ${success ? 'SUCCESS' : 'FAILED'} - Cost: $${estimatedCost}`);
    
            // Update user usage counters only on success
            if (success) {
                const { error: updateError } = await supabase.rpc('increment_user_limits', {
                    p_user_email: userEmail
                });
    
                if (updateError) {
                    this.debugLog(`Error updating user counters: ${updateError.message}`, 'error');
                }
            }
    
            // Log the generation attempt
            const { error: logError } = await supabase
                .from('generation_usage_log')
                .insert({
                    user_email: userEmail,
                    puzzle_type: puzzleType,
                    difficulty,
                    success,
                    cost_estimate: estimatedCost,
                    generation_method: generationMethod,
                    ip_address: ipAddress,
                    user_agent: userAgent,
                    session_id: sessionId
                });
    
            if (logError) {
                this.debugLog(`Error logging generation attempt: ${logError.message}`, 'error');
            }
    
            return { success: true, estimatedCost };
    
        } catch (error) {
            this.debugLog(`Error recording generation: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    /**
     * Get user analytics
     */
    async getUserAnalytics(userEmail, days = 30) {
        try {
            const startDate = new Date();
            startDate.setDate(startDate.getDate() - days);

            const { data: usageData, error } = await supabase
                .from('generation_usage_log')
                .select('*')
                .eq('user_email', userEmail)
                .gte('created_at', startDate.toISOString())
                .order('created_at', { ascending: false });

            if (error) throw error;

            // Analyze usage patterns
            const analytics = {
                totalAttempts: usageData.length,
                successfulGenerations: usageData.filter(u => u.success).length,
                failedGenerations: usageData.filter(u => !u.success).length,
                totalEstimatedCost: usageData.reduce((sum, u) => sum + (u.cost_estimate || 0), 0),
                aiUsage: usageData.filter(u => u.generation_method === 'ai').length,
                randomizedUsage: usageData.filter(u => u.generation_method === 'randomized').length,
                puzzleTypeBreakdown: {},
                dailyUsage: {},
                hourlyPattern: new Array(24).fill(0),
                successRate: 0
            };

            // Calculate success rate
            if (analytics.totalAttempts > 0) {
                analytics.successRate = Math.round((analytics.successfulGenerations / analytics.totalAttempts) * 100);
            }

            // Breakdown by puzzle type
            usageData.forEach(usage => {
                const type = usage.puzzle_type || 'unknown';
                analytics.puzzleTypeBreakdown[type] = (analytics.puzzleTypeBreakdown[type] || 0) + 1;

                // Daily usage
                const day = usage.created_at.split('T')[0];
                analytics.dailyUsage[day] = (analytics.dailyUsage[day] || 0) + 1;

                // Hourly pattern
                const hour = new Date(usage.created_at).getHours();
                analytics.hourlyPattern[hour]++;
            });

            return { success: true, analytics };

        } catch (error) {
            this.debugLog(`Error getting user analytics: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    /**
     * Get system-wide analytics
     */
    async getSystemAnalytics(days = 30) {
        try {
            const startDate = new Date();
            startDate.setDate(startDate.getDate() - days);

            const { data: usageData, error } = await supabase
                .from('generation_usage_log')
                .select('user_email, puzzle_type, success, cost_estimate, generation_method, created_at')
                .gte('created_at', startDate.toISOString());

            if (error) throw error;

            const analytics = {
                totalUsers: new Set(usageData.map(u => u.user_email)).size,
                totalAttempts: usageData.length,
                successfulGenerations: usageData.filter(u => u.success).length,
                totalEstimatedCost: usageData.reduce((sum, u) => sum + (u.cost_estimate || 0), 0),
                aiCost: usageData.filter(u => u.generation_method === 'ai' && u.success).reduce((sum, u) => sum + (u.cost_estimate || 0), 0),
                randomizedSavings: usageData.filter(u => u.generation_method === 'randomized').length * 0.02,
                averagePerUser: 0,
                successRate: 0,
                topUsers: [],
                heavyUsers: [],
                powerUserAnalysis: {}
            };

            // Calculate rates
            if (analytics.totalUsers > 0) {
                analytics.averagePerUser = Math.round(analytics.totalAttempts / analytics.totalUsers);
            }
            if (analytics.totalAttempts > 0) {
                analytics.successRate = Math.round((analytics.successfulGenerations / analytics.totalAttempts) * 100);
            }

            // User breakdown
            const userStats = {};
            usageData.forEach(usage => {
                const email = usage.user_email;
                if (!userStats[email]) {
                    userStats[email] = { 
                        attempts: 0, 
                        successful: 0, 
                        cost: 0, 
                        aiUsage: 0, 
                        randomizedUsage: 0 
                    };
                }
                userStats[email].attempts++;
                if (usage.success) userStats[email].successful++;
                userStats[email].cost += usage.cost_estimate || 0;
                
                if (usage.generation_method === 'ai') userStats[email].aiUsage++;
                else userStats[email].randomizedUsage++;
            });

            // Sort users by usage
            const sortedUsers = Object.entries(userStats)
                .sort(([,a], [,b]) => b.attempts - a.attempts);

            analytics.topUsers = sortedUsers.slice(0, 20);
            analytics.heavyUsers = sortedUsers.filter(([, stats]) => stats.attempts > 50);
            
            // Power user analysis
            const totalCost = analytics.totalEstimatedCost;
            const userCount = sortedUsers.length;
            const top5PercentCount = Math.ceil(userCount * 0.05);
            const top5Percent = sortedUsers.slice(0, top5PercentCount);
            const top5PercentCost = top5Percent.reduce((sum, [, stats]) => sum + stats.cost, 0);
            
            analytics.powerUserAnalysis = {
                top5PercentUsers: top5Percent.length,
                top5PercentCostShare: totalCost > 0 ? Math.round((top5PercentCost / totalCost) * 100) : 0,
                isPowerUserProblem: (top5PercentCost / totalCost) > 0.8,
                recommendation: (top5PercentCost / totalCost) > 0.8 ? 
                    'Power user problem - consider tier upgrades or limits' : 
                    'Distributed usage - consider system-wide optimization'
            };

            return { success: true, analytics };

        } catch (error) {
            this.debugLog(`Error getting system analytics: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    /**
     * Get user limits from database
     */
    async getUserLimits(userEmail) {
        try {
            const { data, error } = await supabase
                .from('user_generation_limits')
                .select('*')
                .eq('user_email', userEmail)
                .single();

            if (error && error.code !== 'PGRST116') throw error; // PGRST116 = no rows
            return data;
        } catch (error) {
            this.debugLog(`Error getting user limits: ${error.message}`, 'error');
            return null;
        }
    }

    /**
     * Create default user limits
     */
    async createUserLimits(userEmail, tier = 'free') {
        try {
            const { data: tierData } = await supabase
                .from('user_tiers')
                .select('*')
                .eq('tier_name', tier)
                .single();

            const limits = {
                user_email: userEmail,
                tier: tier,
                daily_limit: tierData?.daily_limit || 50,
                monthly_limit: tierData?.monthly_limit || 500,
                daily_used: 0,
                monthly_used: 0
            };

            const { data, error } = await supabase
                .from('user_generation_limits')
                .insert(limits)
                .select()
                .single();

            if (error) throw error;

            this.debugLog(`Created limits for ${userEmail}: ${tier} tier`);
            return data;

        } catch (error) {
            this.debugLog(`Error creating user limits: ${error.message}`, 'error');
            throw error;
        }
    }

    /**
     * Reset daily/monthly counters if needed
     */
    async resetCountersIfNeeded(userLimits, today, thisMonth) {
        try {
            let needsUpdate = false;
            const updates = {};

            // Reset daily counter
            if (userLimits.last_daily_reset !== today) {
                updates.daily_used = 0;
                updates.last_daily_reset = today;
                needsUpdate = true;
                this.debugLog(`Resetting daily counter for ${userLimits.user_email}`);
            }

            // Reset monthly counter
            if (userLimits.last_monthly_reset !== thisMonth) {
                updates.monthly_used = 0;
                updates.last_monthly_reset = thisMonth;
                needsUpdate = true;
                this.debugLog(`Resetting monthly counter for ${userLimits.user_email}`);
            }

            if (needsUpdate) {
                const { data, error } = await supabase
                    .from('user_generation_limits')
                    .update(updates)
                    .eq('user_email', userLimits.user_email)
                    .select()
                    .single();

                if (error) throw error;
                return data;
            }

            return userLimits;

        } catch (error) {
            this.debugLog(`Error resetting counters: ${error.message}`, 'error');
            return userLimits; // Return original on error
        }
    }

    /**
     * Log usage attempt
     */
    async logUsageAttempt(userEmail, puzzleType, success, reason, ipAddress, userAgent, sessionId, difficulty = null) {
        try {
            await supabase
                .from('generation_usage_log')
                .insert({
                    user_email: userEmail,
                    puzzle_type: puzzleType,
                    difficulty,
                    success,
                    cost_estimate: 0,
                    generation_method: reason,
                    ip_address: ipAddress,
                    user_agent: userAgent,
                    session_id: sessionId
                });
        } catch (error) {
            this.debugLog(`Error logging usage attempt: ${error.message}`, 'error');
        }
    }

    /**
     * Check for rapid-fire abuse
     */
    async isRapidFireAbuse(sessionId) {
        try {
            const abuseConfig = await this.getConfig('abuse_detection');
            const threshold = abuseConfig?.suspicious_patterns?.rapid_fire_threshold || 5;
            
            const requests = this.sessionRequests.get(sessionId) || [];
            const now = Date.now();
            
            // Clean old requests (older than 1 minute)
            const recentRequests = requests.filter(time => now - time < 60000);
            
            // Check if too many requests in short time
            if (recentRequests.length >= threshold) {
                return true;
            }
            
            // Add current request
            recentRequests.push(now);
            this.sessionRequests.set(sessionId, recentRequests);
            
            return false;
        } catch (error) {
            this.debugLog(`Error checking rapid fire: ${error.message}`, 'error');
            return false;
        }
    }

    /**
     * Check system-wide emergency brake
     */
    async checkEmergencyBrake() {
        try {
            const rateLimitConfig = await this.getConfig('rate_limiting');
            const emergencyLimits = rateLimitConfig?.emergency_brake || {};
            
            const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
            
            const { data: recentUsage, error } = await supabase
                .from('generation_usage_log')
                .select('success, cost_estimate')
                .gte('created_at', oneHourAgo.toISOString());

            if (error) throw error;

            const hourlyGenerations = recentUsage.length;
            const hourlyCost = recentUsage.reduce((sum, u) => sum + (u.cost_estimate || 0), 0);

            const maxGenerations = emergencyLimits.max_generations_per_hour || 5000;
            const maxCost = emergencyLimits.max_cost_per_hour || 100;

            if (hourlyGenerations > maxGenerations || hourlyCost > maxCost) {
                this.debugLog(`Emergency brake activated: ${hourlyGenerations} generations, $${hourlyCost} cost`, 'error');
                return {
                    allowed: false,
                    reason: 'system_overload',
                    message: 'System is under high load. Please try again later.',
                    retryAfter: 300,
                    stats: {
                        hourlyGenerations,
                        hourlyCost,
                        limits: { maxGenerations, maxCost }
                    }
                };
            }

            return { allowed: true };

        } catch (error) {
            this.debugLog(`Error checking emergency brake: ${error.message}`, 'error');
            return { allowed: true }; // Fail open
        }
    }

    /**
     * Calculate progressive delay based on usage
     */
    async calculateProgressiveDelay(userLimits) {
        try {
            const rateLimitConfig = await this.getConfig('rate_limiting');
            const delays = rateLimitConfig?.progressive_delays || {};
            
            const dailyUsed = userLimits.daily_used;
            
            if (dailyUsed >= 75) return delays.after_75_requests || 5000;
            if (dailyUsed >= 50) return delays.after_50_requests || 2000;
            if (dailyUsed >= 25) return delays.after_25_requests || 1000;
            
            return 0;
        } catch (error) {
            this.debugLog(`Error calculating delay: ${error.message}`, 'error');
            return 0;
        }
    }

    /**
     * Get upgrade options for user
     */
    async getUpgradeOptions(currentTier) {
        try {
            const { data: tiers, error } = await supabase
                .from('user_tiers')
                .select('*')
                .eq('is_active', true)
                .order('daily_limit', { ascending: true });

            if (error) return [];

            const currentLimits = {
                'free': 10,
                'premium': 50,
                'unlimited': 1000
            };

            return tiers.filter(tier => 
                tier.daily_limit > (currentLimits[currentTier] || 0)
            );
        } catch (error) {
            this.debugLog(`Error getting upgrade options: ${error.message}`, 'error');
            return [];
        }
    }

    /**
     * Upgrade user tier
     */
    async upgradeUserTier(userEmail, newTier) {
        try {
            const { data: tierData, error: tierError } = await supabase
                .from('user_tiers')
                .select('*')
                .eq('tier_name', newTier)
                .single();

            if (tierError) throw new Error(`Invalid tier: ${newTier}`);

            const { error: updateError } = await supabase
                .from('user_generation_limits')
                .update({
                    tier: newTier,
                    daily_limit: tierData.daily_limit,
                    monthly_limit: tierData.monthly_limit,
                    updated_at: new Date().toISOString()
                })
                .eq('user_email', userEmail);

            if (updateError) throw updateError;

            this.debugLog(`Upgraded ${userEmail} to ${newTier} tier`);
            return { success: true, newLimits: tierData };

        } catch (error) {
            this.debugLog(`Error upgrading user tier: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    /**
     * Get configuration from database with caching
     */
    async getConfig(key) {
        try {
            // Check cache first
            if (this.configCache.has(key) && this.cacheExpiry.get(key) > Date.now()) {
                return this.configCache.get(key);
            }

            const { data, error } = await supabase
                .from('system_config')
                .select('value')
                .eq('key', key)
                .single();

            if (error) {
                this.debugLog(`Config key '${key}' not found`, 'warn');
                return {};
            }

            // Cache the result
            this.configCache.set(key, data.value);
            this.cacheExpiry.set(key, Date.now() + this.cacheDuration);

            return data.value;

        } catch (error) {
            this.debugLog(`Error fetching config '${key}': ${error.message}`, 'error');
            return {};
        }
    }

    /**
     * Helper methods
     */
    generateSessionId(userEmail, ipAddress) {
        return `${userEmail}_${ipAddress}_${Math.floor(Date.now() / 60000)}`;
    }

    getNextResetTime(type) {
        const now = new Date();
        if (type === 'daily') {
            const tomorrow = new Date(now);
            tomorrow.setDate(tomorrow.getDate() + 1);
            tomorrow.setHours(0, 0, 0, 0);
            return tomorrow.toISOString();
        } else {
            const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
            return nextMonth.toISOString();
        }
    }

    formatLimits(userLimits) {
        return {
            tier: userLimits.tier,
            dailyUsed: userLimits.daily_used,
            dailyLimit: userLimits.daily_limit,
            monthlyUsed: userLimits.monthly_used,
            monthlyLimit: userLimits.monthly_limit,
            dailyRemaining: Math.max(0, userLimits.daily_limit - userLimits.daily_used),
            monthlyRemaining: Math.max(0, userLimits.monthly_limit - userLimits.monthly_used)
        };
    }

    /**
     * Clear all caches
     */
    clearCache() {
        this.configCache.clear();
        this.cacheExpiry.clear();
        this.sessionRequests.clear();
        this.debugLog('All caches cleared');
    }

    /**
     * Set debug mode
     */
    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }
}

export const userLimitService = new UserLimitService();