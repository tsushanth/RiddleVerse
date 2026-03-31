// analyticsService.js - DAU/MAU Analytics using existing puzzle_analytics data

import { admin, db } from '../config/firebaseAdmin.js';

class AnalyticsService {
    constructor() {
        this.cache = new Map();
        this.cacheTimeout = 5 * 60 * 1000; // 5 minutes
    }

    // MARK: - Daily Active Users (DAU)
    async getDailyActiveUsers(date = this.getCurrentDateString()) {
        const cacheKey = `dau_${date}`;
        
        // Check cache first
        if (this.cache.has(cacheKey)) {
            const cached = this.cache.get(cacheKey);
            if (Date.now() - cached.timestamp < this.cacheTimeout) {
                return cached.data;
            }
        }

        try {
            const { startOfDay, endOfDay } = this.getDayTimestamps(date);
            
            // Query Firebase puzzle_analytics collection
            const analyticsRef = db.collection('puzzle_analytics');
            const snapshot = await analyticsRef
                .where('timestamp', '>=', startOfDay)
                .where('timestamp', '<', endOfDay)
                .get();

            // Count unique users (excluding anonymous)
            const uniqueUsers = new Set();
            snapshot.forEach(doc => {
                const data = doc.data();
                if (data.user_id && data.user_id !== 'anonymous') {
                    uniqueUsers.add(data.user_id);
                }
            });

            const dauCount = uniqueUsers.size;
            
            // Cache result
            this.cache.set(cacheKey, {
                data: dauCount,
                timestamp: Date.now()
            });

            console.log(`📊 DAU for ${date}: ${dauCount} unique users`);
            return dauCount;

        } catch (error) {
            console.error(`❌ Error calculating DAU for ${date}:`, error);
            throw new Error(`Failed to calculate DAU: ${error.message}`);
        }
    }

    // MARK: - Monthly Active Users (MAU)
    async getMonthlyActiveUsers(month = this.getCurrentMonthString()) {
        const cacheKey = `mau_${month}`;
        
        // Check cache first
        if (this.cache.has(cacheKey)) {
            const cached = this.cache.get(cacheKey);
            if (Date.now() - cached.timestamp < this.cacheTimeout) {
                return cached.data;
            }
        }

        try {
            const { startOfMonth, endOfMonth } = this.getMonthTimestamps(month);
            
            // Query Firebase puzzle_analytics collection
            // For large datasets, we'll process in batches
            const analyticsRef = db.collection('puzzle_analytics');
            const uniqueUsers = new Set();
            
            // Firebase doesn't have built-in pagination, so we'll use cursor-based pagination
            let lastDoc = null;
            let hasMore = true;
            const batchSize = 1000;

            while (hasMore) {
                let query = analyticsRef
                    .where('timestamp', '>=', startOfMonth)
                    .where('timestamp', '<', endOfMonth)
                    .orderBy('timestamp')
                    .limit(batchSize);

                if (lastDoc) {
                    query = query.startAfter(lastDoc);
                }

                const snapshot = await query.get();
                
                if (snapshot.empty) {
                    hasMore = false;
                    break;
                }

                // Process this batch
                snapshot.forEach(doc => {
                    const data = doc.data();
                    if (data.user_id && data.user_id !== 'anonymous') {
                        uniqueUsers.add(data.user_id);
                    }
                });

                // Update cursor for next batch
                if (snapshot.size === batchSize) {
                    lastDoc = snapshot.docs[snapshot.docs.length - 1];
                } else {
                    hasMore = false;
                }
            }

            const mauCount = uniqueUsers.size;
            
            // Cache result
            this.cache.set(cacheKey, {
                data: mauCount,
                timestamp: Date.now()
            });

            console.log(`📊 MAU for ${month}: ${mauCount} unique users`);
            return mauCount;

        } catch (error) {
            console.error(`❌ Error calculating MAU for ${month}:`, error);
            throw new Error(`Failed to calculate MAU: ${error.message}`);
        }
    }

    // MARK: - DAU Trend Analysis
    async getDauTrend(days = 30) {
        try {
            const dataPoints = [];
            const today = new Date();

            // Process days in batches to avoid overwhelming the system
            const batchSize = 7;
            for (let i = days - 1; i >= 0; i -= batchSize) {
                const batchPromises = [];
                
                for (let j = 0; j < batchSize && (i - j) >= 0; j++) {
                    const date = new Date(today);
                    date.setDate(today.getDate() - (i - j));
                    const dateString = this.formatDate(date);
                    
                    batchPromises.push(
                        this.getDailyActiveUsers(dateString).then(count => ({
                            date: dateString,
                            count,
                            timestamp: date.getTime()
                        })).catch(error => {
                            console.warn(`⚠️ Failed to get DAU for ${dateString}:`, error.message);
                            return {
                                date: dateString,
                                count: 0,
                                timestamp: date.getTime(),
                                error: error.message
                            };
                        })
                    );
                }

                const batchResults = await Promise.all(batchPromises);
                dataPoints.push(...batchResults);

                // Small delay between batches
                if (i - batchSize >= 0) {
                    await new Promise(resolve => setTimeout(resolve, 100));
                }
            }

            // Sort by timestamp and return
            const sortedResults = dataPoints.sort((a, b) => a.timestamp - b.timestamp);
            
            console.log(`📈 DAU trend calculated for ${days} days: ${sortedResults.filter(p => !p.error).length} successful data points`);
            return sortedResults;

        } catch (error) {
            console.error(`❌ Error calculating DAU trend:`, error);
            throw new Error(`Failed to calculate DAU trend: ${error.message}`);
        }
    }

    // MARK: - MAU Trend Analysis
    async getMauTrend(months = 12) {
        try {
            const dataPoints = [];
            const today = new Date();

            for (let i = months - 1; i >= 0; i--) {
                const date = new Date(today.getFullYear(), today.getMonth() - i, 1);
                const monthString = this.formatMonth(date);
                
                try {
                    const count = await this.getMonthlyActiveUsers(monthString);
                    dataPoints.push({
                        date: monthString,
                        count,
                        timestamp: date.getTime()
                    });
                } catch (error) {
                    console.warn(`⚠️ Failed to get MAU for ${monthString}:`, error.message);
                    dataPoints.push({
                        date: monthString,
                        count: 0,
                        timestamp: date.getTime(),
                        error: error.message
                    });
                }

                // Small delay between months
                await new Promise(resolve => setTimeout(resolve, 200));
            }

            console.log(`📈 MAU trend calculated for ${months} months`);
            return dataPoints;

        } catch (error) {
            console.error(`❌ Error calculating MAU trend:`, error);
            throw new Error(`Failed to calculate MAU trend: ${error.message}`);
        }
    }

    // MARK: - User Retention Analysis
    async getRetentionMetrics() {
        try {
            const now = Date.now();
            const oneDayMs = 24 * 60 * 60 * 1000;
            const oneWeekMs = 7 * oneDayMs;
            const oneMonthMs = 30 * oneDayMs;

            // Define time periods
            const periods = {
                currentWeek: { start: now - oneWeekMs, end: now },
                previousWeek: { start: now - (2 * oneWeekMs), end: now - oneWeekMs },
                currentMonth: { start: now - oneMonthMs, end: now },
                previousMonth: { start: now - (2 * oneMonthMs), end: now - oneMonthMs }
            };

            // Get users for each period
            const userSets = {};
            for (const [periodName, { start, end }] of Object.entries(periods)) {
                userSets[periodName] = await this.getUsersInPeriod(start, end);
            }

            // Calculate retention rates
            const sevenDayRetention = userSets.previousWeek.size > 0 
                ? (this.setIntersection(userSets.currentWeek, userSets.previousWeek).size / userSets.previousWeek.size) * 100
                : 0;

            const thirtyDayRetention = userSets.previousMonth.size > 0 
                ? (this.setIntersection(userSets.currentMonth, userSets.previousMonth).size / userSets.previousMonth.size) * 100
                : 0;

            const metrics = {
                sevenDayRetention,
                thirtyDayRetention,
                currentActiveUsers: userSets.currentWeek.size,
                sevenDayActiveUsers: userSets.currentWeek.size,
                thirtyDayActiveUsers: userSets.currentMonth.size,
                retainedUsersWeek: this.setIntersection(userSets.currentWeek, userSets.previousWeek).size,
                retainedUsersMonth: this.setIntersection(userSets.currentMonth, userSets.previousMonth).size
            };

            console.log(`🔄 Retention metrics: 7-day: ${sevenDayRetention.toFixed(1)}%, 30-day: ${thirtyDayRetention.toFixed(1)}%`);
            return metrics;

        } catch (error) {
            console.error(`❌ Error calculating retention metrics:`, error);
            throw new Error(`Failed to calculate retention metrics: ${error.message}`);
        }
    }

    // MARK: - Comprehensive Analytics Dashboard
    async getAnalyticsDashboard() {
        try {
            console.log(`📊 Generating comprehensive analytics dashboard...`);

            const today = this.getCurrentDateString();
            const yesterday = this.getYesterdayString();
            const thisMonth = this.getCurrentMonthString();
            const lastMonth = this.getLastMonthString();

            // Run all analytics in parallel
            const [
                dauToday,
                dauYesterday,
                mauThisMonth,
                mauLastMonth,
                retentionMetrics,
                eventAnalytics
            ] = await Promise.all([
                this.getDailyActiveUsers(today),
                this.getDailyActiveUsers(yesterday),
                this.getMonthlyActiveUsers(thisMonth),
                this.getMonthlyActiveUsers(lastMonth),
                this.getRetentionMetrics(),
                this.getEventAnalytics()
            ]);

            // Calculate growth rates
            const dauGrowth = dauYesterday > 0 
                ? ((dauToday - dauYesterday) / dauYesterday) * 100 
                : 0;

            const mauGrowth = mauLastMonth > 0 
                ? ((mauThisMonth - mauLastMonth) / mauLastMonth) * 100 
                : 0;

            const dashboard = {
                dau: {
                    today: dauToday,
                    yesterday: dauYesterday,
                    growth: dauGrowth,
                    growthFormatted: `${dauGrowth >= 0 ? '+' : ''}${dauGrowth.toFixed(1)}%`
                },
                mau: {
                    thisMonth: mauThisMonth,
                    lastMonth: mauLastMonth,
                    growth: mauGrowth,
                    growthFormatted: `${mauGrowth >= 0 ? '+' : ''}${mauGrowth.toFixed(1)}%`
                },
                retention: retentionMetrics,
                engagement: eventAnalytics,
                summary: {
                    totalActiveUsers: Math.max(dauToday, retentionMetrics.currentActiveUsers),
                    healthScore: this.calculateHealthScore({
                        dauGrowth,
                        mauGrowth,
                        sevenDayRetention: retentionMetrics.sevenDayRetention,
                        completionRate: eventAnalytics.completionRate
                    })
                },
                generatedAt: new Date().toISOString()
            };

            console.log(`✅ Analytics dashboard generated successfully`);
            return dashboard;

        } catch (error) {
            console.error(`❌ Error generating analytics dashboard:`, error);
            throw new Error(`Failed to generate analytics dashboard: ${error.message}`);
        }
    }

    // MARK: - Event-Based Analytics
    async getEventAnalytics(days = 30) {
        try {
            const cutoffTime = Date.now() - (days * 24 * 60 * 60 * 1000);

            const analyticsRef = db.collection('puzzle_analytics');
            const snapshot = await analyticsRef
                .where('timestamp', '>=', cutoffTime)
                .get();

            const analytics = {
                totalEvents: 0,
                uniqueUsers: new Set(),
                eventBreakdown: {},
                puzzleTypeBreakdown: {},
                userEngagement: {},
                usersWhoStarted: new Set(),
                usersWhoCompleted: new Set()
            };

            // Process each event
            snapshot.forEach(doc => {
                const data = doc.data();
                const { event_name, user_id, puzzle_type } = data;

                analytics.totalEvents++;

                if (user_id && user_id !== 'anonymous') {
                    analytics.uniqueUsers.add(user_id);

                    // Track event breakdown
                    analytics.eventBreakdown[event_name] = (analytics.eventBreakdown[event_name] || 0) + 1;

                    // Track puzzle type breakdown
                    if (puzzle_type) {
                        analytics.puzzleTypeBreakdown[puzzle_type] = (analytics.puzzleTypeBreakdown[puzzle_type] || 0) + 1;
                    }

                    // Track user engagement
                    if (!analytics.userEngagement[user_id]) {
                        analytics.userEngagement[user_id] = 0;
                    }
                    analytics.userEngagement[user_id]++;

                    // Track funnel metrics
                    if (event_name === 'puzzle_start') {
                        analytics.usersWhoStarted.add(user_id);
                    } else if (event_name === 'puzzle_complete') {
                        analytics.usersWhoCompleted.add(user_id);
                    }
                }
            });

            // Calculate derived metrics
            const uniqueUserCount = analytics.uniqueUsers.size;
            const avgEventsPerUser = uniqueUserCount > 0 
                ? analytics.totalEvents / uniqueUserCount 
                : 0;

            const completionRate = analytics.usersWhoStarted.size > 0 
                ? (analytics.usersWhoCompleted.size / analytics.usersWhoStarted.size) * 100 
                : 0;

            return {
                totalEvents: analytics.totalEvents,
                uniqueActiveUsers: uniqueUserCount,
                avgEventsPerUser: Math.round(avgEventsPerUser * 100) / 100,
                completionRate: Math.round(completionRate * 100) / 100,
                usersWhoStartedPuzzles: analytics.usersWhoStarted.size,
                usersWhoCompletedPuzzles: analytics.usersWhoCompleted.size,
                eventBreakdown: analytics.eventBreakdown,
                puzzleTypeBreakdown: analytics.puzzleTypeBreakdown,
                engagementDistribution: this.calculateEngagementDistribution(analytics.userEngagement)
            };

        } catch (error) {
            console.error(`❌ Error calculating event analytics:`, error);
            throw new Error(`Failed to calculate event analytics: ${error.message}`);
        }
    }

    // MARK: - Helper Methods
    async getUsersInPeriod(startTime, endTime) {
        try {
            const users = new Set();
            const analyticsRef = db.collection('puzzle_analytics');
            
            let lastDoc = null;
            let hasMore = true;
            const batchSize = 1000;

            while (hasMore) {
                let query = analyticsRef
                    .where('timestamp', '>=', startTime)
                    .where('timestamp', '<', endTime)
                    .orderBy('timestamp')
                    .limit(batchSize);

                if (lastDoc) {
                    query = query.startAfter(lastDoc);
                }

                const snapshot = await query.get();
                
                if (snapshot.empty) {
                    hasMore = false;
                    break;
                }

                snapshot.forEach(doc => {
                    const data = doc.data();
                    if (data.user_id && data.user_id !== 'anonymous') {
                        users.add(data.user_id);
                    }
                });

                if (snapshot.size === batchSize) {
                    lastDoc = snapshot.docs[snapshot.docs.length - 1];
                } else {
                    hasMore = false;
                }
            }

            return users;
        } catch (error) {
            console.error(`❌ Error getting users in period:`, error);
            return new Set();
        }
    }

    setIntersection(setA, setB) {
        return new Set([...setA].filter(x => setB.has(x)));
    }

    calculateEngagementDistribution(userEngagement) {
        const engagementLevels = { low: 0, medium: 0, high: 0, power: 0 };
        
        Object.values(userEngagement).forEach(eventCount => {
            if (eventCount <= 2) engagementLevels.low++;
            else if (eventCount <= 10) engagementLevels.medium++;
            else if (eventCount <= 50) engagementLevels.high++;
            else engagementLevels.power++;
        });

        return engagementLevels;
    }

    calculateHealthScore({ dauGrowth, mauGrowth, sevenDayRetention, completionRate }) {
        let score = 50; // Base score

        // DAU growth impact (max +/-20 points)
        score += Math.max(-20, Math.min(20, dauGrowth * 2));

        // MAU growth impact (max +/-15 points)
        score += Math.max(-15, Math.min(15, mauGrowth));

        // Retention impact (max +20 points)
        score += Math.max(0, Math.min(20, sevenDayRetention - 20));

        // Completion rate impact (max +15 points)
        score += Math.max(0, Math.min(15, (completionRate - 50) / 3));

        return Math.max(0, Math.min(100, Math.round(score)));
    }

    // MARK: - Date Utility Methods
    getCurrentDateString() {
        return new Date().toISOString().split('T')[0];
    }

    getCurrentMonthString() {
        const now = new Date();
        return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    }

    getYesterdayString() {
        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        return yesterday.toISOString().split('T')[0];
    }

    getLastMonthString() {
        const lastMonth = new Date();
        lastMonth.setMonth(lastMonth.getMonth() - 1);
        return `${lastMonth.getFullYear()}-${String(lastMonth.getMonth() + 1).padStart(2, '0')}`;
    }

    formatDate(date) {
        return date.toISOString().split('T')[0];
    }

    formatMonth(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    }

    getDayTimestamps(dateString) {
        const date = new Date(dateString + 'T00:00:00.000Z');
        const startOfDay = date.getTime();
        const endOfDay = startOfDay + (24 * 60 * 60 * 1000);
        
        return { startOfDay, endOfDay };
    }

    getMonthTimestamps(monthString) {
        const [year, month] = monthString.split('-').map(Number);
        const startOfMonth = new Date(year, month - 1, 1).getTime();
        const endOfMonth = new Date(year, month, 1).getTime();
        
        return { startOfMonth, endOfMonth };
    }

    // MARK: - Cache Management
    clearCache() {
        this.cache.clear();
        console.log(`🧹 Analytics cache cleared`);
    }

    getCacheStats() {
        return {
            entries: this.cache.size,
            keys: Array.from(this.cache.keys())
        };
    }
}

// Export singleton instance
export const analyticsService = new AnalyticsService();