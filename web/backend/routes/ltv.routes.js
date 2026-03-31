// routes/ltv.routes.js
import express from 'express';
import rateLimit from 'express-rate-limit';
import { supabase } from '../config/database.js';

const router = express.Router();

// Rate limiters
const publicLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 100,
    message: { 
        error: "Too many requests. Please try again later.",
        retryAfter: "15 minutes"
    }
});

const adminLimiter = rateLimit({
    windowMs: 60 * 60 * 1000,
    max: 20,
    message: { 
        error: "Too many admin requests. Limit: 20 per hour.",
        retryAfter: "1 hour"
    }
});

// Admin validation middleware
const validateAdmin = (req, res, next) => {
    const adminKey = req.body?.adminKey || req.query?.adminKey || req.headers['x-admin-key'];
    
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({
            success: false,
            message: 'Unauthorized',
            error: 'invalid_admin_key'
        });
    }
    
    next();
};

// ============================================================================
// LTV CALCULATION FUNCTIONS (Read from Firebase Analytics)
// ============================================================================

/**
 * Calculate User Lifetime Value from Firebase Analytics
 */
async function calculateUserLTV(userId) {
    const { db } = await import('../config/firebaseAdmin.js');
    
    try {
        // Get all user analytics events from Firebase
        const userEvents = await db.collection('puzzle_analytics')
            .where('user_id', '==', userId)
            .orderBy('timestamp', 'asc')
            .get();
        
        if (userEvents.empty) {
            return {
                userId,
                ltv: 0,
                tier: 'new_user',
                signals: {},
                metrics: {
                    engagement: { score: 0, totalSessions: 0, totalPuzzlesSolved: 0 },
                    retention: { score: 0, isChurned: false, isActive: false, daysSinceLastActivity: 999 },
                    progression: { score: 0 },
                    purchase: { totalRevenue: 0, isPaying: false, conversionIntent: 'low' }
                }
            };
        }
        
        // Calculate all metrics
        const engagementMetrics = calculateEngagementScore(userEvents);
        const retentionMetrics = calculateRetentionScore(userEvents);
        const progressionMetrics = calculateProgressionScore(userEvents);
        const purchaseMetrics = await calculatePurchaseValue(userId, userEvents);
        
        const predictedLTV = calculatePredictedLTV({
            engagement: engagementMetrics,
            retention: retentionMetrics,
            progression: progressionMetrics,
            purchase: purchaseMetrics
        });
        
        const userTier = determineUserTier(predictedLTV, engagementMetrics, retentionMetrics);
        
        return {
            userId,
            ltv: predictedLTV.totalLTV,
            actualRevenue: purchaseMetrics.totalRevenue,
            predictedRevenue: predictedLTV.predictedRevenue,
            tier: userTier.tier,
            segments: userTier.segments,
            isActive: retentionMetrics.isActive,
            isChurned: retentionMetrics.isChurned,
            isDormant: retentionMetrics.daysSinceLastActivity > 7 && retentionMetrics.daysSinceLastActivity <= 30,
            daysSinceLastActivity: retentionMetrics.daysSinceLastActivity,
            metrics: {
                engagement: engagementMetrics,
                retention: retentionMetrics,
                progression: progressionMetrics,
                purchase: purchaseMetrics
            },
            adTargeting: {
                shouldTarget: userTier.shouldTarget,
                maxBid: userTier.recommendedBid,
                lookalikeTier: userTier.tier
            },
            calculatedAt: new Date().toISOString()
        };
        
    } catch (error) {
        console.error(`Error calculating LTV for user ${userId}:`, error);
        return null;
    }
}

// [Keep all the calculation functions: calculateEngagementScore, calculateRetentionScore, 
//  calculateProgressionScore, calculatePurchaseValue, calculatePredictedLTV, 
//  calculateConversionProbability, determineUserTier - they stay the same]

function calculateEngagementScore(userEvents) {
    const docs = userEvents.docs;
    const sessionStarts = docs.filter(d => d.data().event_name === 'session_start_event');
    const sessionEnds = docs.filter(d => d.data().event_name === 'session_end');
    
    let totalSessionTime = 0;
    let totalPuzzlesSolved = 0;
    let totalScore = 0;
    
    sessionEnds.forEach(doc => {
        const data = doc.data();
        totalSessionTime += (data.session_duration_seconds || 0);
        totalPuzzlesSolved += (data.puzzles_solved || 0);
        totalScore += (data.total_score || 0);
    });
    
    const puzzleCompletes = docs.filter(d => d.data().event_name === 'puzzle_complete');
    const puzzleAbandoned = docs.filter(d => d.data().event_name === 'puzzle_abandoned');
    
    let correctAnswers = 0;
    puzzleCompletes.forEach(doc => {
        if (doc.data().is_correct) correctAnswers++;
    });
    
    const totalPuzzles = puzzleCompletes.length;
    const completionRate = totalPuzzles > 0 
        ? (totalPuzzles / (totalPuzzles + puzzleAbandoned.length)) * 100 
        : 0;
    
    const accuracy = totalPuzzles > 0 ? (correctAnswers / totalPuzzles) * 100 : 0;
    const avgSessionTime = sessionEnds.length > 0 ? totalSessionTime / sessionEnds.length : 0;
    const avgPuzzlesPerSession = sessionStarts.length > 0 ? totalPuzzlesSolved / sessionStarts.length : 0;
    
    const engagementScore = Math.min(100,
        (sessionStarts.length * 2) +
        (totalPuzzlesSolved * 1) +
        (completionRate * 0.3) +
        (accuracy * 0.2) +
        (Math.min(avgSessionTime / 60, 10) * 2)
    );
    
    return {
        score: Math.round(engagementScore),
        totalSessions: sessionStarts.length,
        totalPuzzlesSolved,
        totalScore,
        avgSessionTimeMinutes: (avgSessionTime / 60).toFixed(1),
        avgPuzzlesPerSession: avgPuzzlesPerSession.toFixed(1),
        completionRate: completionRate.toFixed(1),
        accuracy: accuracy.toFixed(1),
        correctAnswers,
        abandonedPuzzles: puzzleAbandoned.length
    };
}

function calculateRetentionScore(userEvents) {
    const docs = userEvents.docs;
    
    if (docs.length === 0) return { 
        score: 0, 
        daysSinceInstall: 0, 
        isChurned: false, 
        isActive: false,
        daysSinceLastActivity: 999
    };
    
    const firstActivity = docs[0].data().timestamp;
    const lastActivity = docs[docs.length - 1].data().timestamp;
    
    const daysSinceInstall = Math.floor((Date.now() - firstActivity) / (24 * 60 * 60 * 1000));
    const daysSinceLastActivity = Math.floor((Date.now() - lastActivity) / (24 * 60 * 60 * 1000));
    
    const activeDays = new Set();
    docs.forEach(doc => {
        const date = new Date(doc.data().timestamp).toISOString().split('T')[0];
        activeDays.add(date);
    });
    
    const uniqueActiveDays = activeDays.size;
    const streakEvents = docs.filter(d => d.data().event_name === 'streak_update');
    const currentStreak = streakEvents.length > 0 ? streakEvents[streakEvents.length - 1].data().current_streak : 0;
    const bestStreak = streakEvents.length > 0 ? Math.max(...streakEvents.map(d => d.data().current_streak || 0)) : 0;
    
    const retentionRate = daysSinceInstall > 0 ? (uniqueActiveDays / daysSinceInstall) * 100 : 100;
    
    let recencyScore = 0;
    if (daysSinceLastActivity === 0) recencyScore = 25;
    else if (daysSinceLastActivity <= 1) recencyScore = 20;
    else if (daysSinceLastActivity <= 3) recencyScore = 15;
    else if (daysSinceLastActivity <= 7) recencyScore = 10;
    else if (daysSinceLastActivity <= 14) recencyScore = 5;
    else recencyScore = 0;
    
    const retentionScore = Math.min(100,
        recencyScore +
        (Math.min(retentionRate, 50)) +
        (Math.min(currentStreak * 2, 15)) +
        (Math.min(uniqueActiveDays, 10))
    );
    
    return {
        score: Math.round(retentionScore),
        daysSinceInstall,
        daysSinceLastActivity,
        uniqueActiveDays,
        retentionRate: retentionRate.toFixed(1),
        currentStreak,
        bestStreak,
        isActive: daysSinceLastActivity <= 7,
        isChurned: daysSinceLastActivity > 30
    };
}

function calculateProgressionScore(userEvents) {
    const docs = userEvents.docs;
    const levelEvents = docs.filter(d => d.data().event_name === 'level_up');
    const currentLevel = levelEvents.length > 0 ? Math.max(...levelEvents.map(d => d.data().level || 1)) : 1;
    const achievements = docs.filter(d => d.data().event_name === 'unlock_achievement');
    const tutorialComplete = docs.some(d => d.data().event_name === 'tutorial_complete');
    const tutorialSkipped = docs.some(d => d.data().event_name === 'tutorial_skip');
    const progressEvents = docs.filter(d => d.data().event_name === 'user_progress_update');
    const latestProgress = progressEvents.length > 0 ? progressEvents[progressEvents.length - 1].data() : null;
    
    const totalXP = latestProgress?.total_xp || 0;
    const totalPuzzlesSolved = latestProgress?.total_puzzles_solved || 0;
    
    const progressionScore = Math.min(100,
        (currentLevel * 5) +
        (achievements.length * 3) +
        (tutorialComplete ? 10 : 0) +
        (Math.min(totalXP / 100, 20)) +
        (Math.min(totalPuzzlesSolved / 10, 30))
    );
    
    return {
        score: Math.round(progressionScore),
        currentLevel,
        totalXP,
        totalAchievements: achievements.length,
        totalPuzzlesSolved,
        tutorialCompleted: tutorialComplete,
        tutorialSkipped
    };
}

async function calculatePurchaseValue(userId, userEvents) {
    const docs = userEvents.docs;
    const purchases = docs.filter(d => d.data().event_name === 'purchase');
    
    let totalRevenue = 0;
    let subscriptionTier = 'free';
    let lastPurchaseDate = null;
    
    purchases.forEach(doc => {
        const data = doc.data();
        totalRevenue += (data.value || 0);
        if (data.items && data.items.length > 0) {
            subscriptionTier = data.items[0].item_id || 'free';
        }
        if (!lastPurchaseDate || data.timestamp > lastPurchaseDate) {
            lastPurchaseDate = data.timestamp;
        }
    });
    
    const subscriptionViews = docs.filter(d => 
        d.data().event_name === 'subscription_view' || 
        d.data().event_name === 'subscription_dialog_opened'
    );
    
    const subscriptionClicks = docs.filter(d => 
        d.data().event_name === 'subscription_tier_clicked'
    );
    
    return {
        totalRevenue,
        isPaying: totalRevenue > 0,
        subscriptionTier,
        purchaseCount: purchases.length,
        lastPurchaseDate,
        daysSinceLastPurchase: lastPurchaseDate 
            ? Math.floor((Date.now() - lastPurchaseDate) / (24 * 60 * 60 * 1000))
            : null,
        subscriptionViews: subscriptionViews.length,
        subscriptionClicks: subscriptionClicks.length,
        conversionIntent: subscriptionClicks.length > 0 ? 'high' : subscriptionViews.length > 0 ? 'medium' : 'low'
    };
}

function calculatePredictedLTV(metrics) {
    const { engagement, retention, progression, purchase } = metrics;
    
    if (purchase.isPaying) {
        const monthlyValue = purchase.subscriptionTier === 'premium' ? 9.99 : 4.99;
        const predictedMonths = retention.isActive ? 12 : 3;
        
        return {
            totalLTV: purchase.totalRevenue + (monthlyValue * predictedMonths),
            actualRevenue: purchase.totalRevenue,
            predictedRevenue: monthlyValue * predictedMonths,
            confidence: 'high'
        };
    }
    
    const conversionProbability = calculateConversionProbability(
        engagement.score,
        retention.score,
        progression.score,
        purchase.subscriptionViews,
        purchase.subscriptionClicks
    );
    
    const avgSubscriptionValue = 7.50;
    const predictedLifetimeMonths = retention.isActive 
        ? Math.min(retention.retentionRate / 10, 12)
        : 3;
    
    const predictedRevenue = conversionProbability * avgSubscriptionValue * predictedLifetimeMonths;
    const adRevenuePerSession = 0.05;
    const predictedAdRevenue = engagement.totalSessions * adRevenuePerSession;
    
    return {
        totalLTV: predictedRevenue + predictedAdRevenue,
        actualRevenue: 0,
        predictedRevenue,
        predictedAdRevenue,
        conversionProbability: (conversionProbability * 100).toFixed(1),
        confidence: retention.isActive ? 'medium' : 'low'
    };
}

function calculateConversionProbability(engagementScore, retentionScore, progressionScore, subscriptionViews, subscriptionClicks) {
    let probability = 0.02;
    
    if (engagementScore > 80) probability *= 3;
    else if (engagementScore > 60) probability *= 2;
    else if (engagementScore > 40) probability *= 1.5;
    
    if (retentionScore > 70) probability *= 2.5;
    else if (retentionScore > 50) probability *= 1.8;
    else if (retentionScore > 30) probability *= 1.3;
    
    if (progressionScore > 60) probability *= 1.5;
    else if (progressionScore > 40) probability *= 1.2;
    
    if (subscriptionClicks > 2) probability *= 5;
    else if (subscriptionClicks > 0) probability *= 3;
    else if (subscriptionViews > 3) probability *= 2;
    else if (subscriptionViews > 0) probability *= 1.5;
    
    return Math.min(probability, 0.50);
}

function determineUserTier(predictedLTV, engagementMetrics, retentionMetrics) {
    const ltvValue = predictedLTV.totalLTV;
    const isPaying = predictedLTV.actualRevenue > 0;
    
    let tier, shouldTarget, recommendedBid, segments = [];
    
    if (isPaying) {
        tier = 'whale';
        shouldTarget = true;
        recommendedBid = Math.min(ltvValue * 0.3, 50);
        segments = ['paying_user', 'high_ltv', 'retain'];
    } else if (ltvValue > 20) {
        tier = 'high_value';
        shouldTarget = true;
        recommendedBid = Math.min(ltvValue * 0.4, 15);
        segments = ['high_potential', 'convert'];
        if (engagementMetrics.score > 70) segments.push('highly_engaged');
        if (retentionMetrics.isActive) segments.push('active');
    } else if (ltvValue > 10) {
        tier = 'medium_value';
        shouldTarget = true;
        recommendedBid = Math.min(ltvValue * 0.5, 8);
        segments = ['medium_potential', 'nurture'];
        if (engagementMetrics.score > 50) segments.push('engaged');
    } else if (ltvValue > 5) {
        tier = 'low_value';
        shouldTarget = retentionMetrics.isActive;
        recommendedBid = Math.min(ltvValue * 0.6, 4);
        segments = ['low_potential', 'activate'];
    } else {
        tier = 'very_low';
        shouldTarget = false;
        recommendedBid = 0;
        segments = ['low_engagement', 'at_risk'];
    }
    
    if (retentionMetrics.isChurned) {
        segments.push('churned');
    } else if (retentionMetrics.daysSinceLastActivity > 30) {
        segments.push('dormant');
    } else if (retentionMetrics.daysSinceLastActivity > 7) {
        segments.push('at_risk');
    }
    
    return {
        tier,
        shouldTarget,
        recommendedBid: recommendedBid.toFixed(2),
        segments
    };
}

// ============================================================================
// MAIN GENERATION FUNCTION (Store in Supabase)
// ============================================================================

async function generateAdTargetingAudiences() {
    const { db } = await import('../config/firebaseAdmin.js');
    
    try {
        console.log('🎯 Generating ad targeting audiences...');
        
        // Get all unique users from Firebase Analytics
        const snapshot = await db.collection('puzzle_analytics')
            .select('user_id')
            .get();
        
        const ids = new Set();
        snapshot.forEach(doc => {
            const userId = doc.data().user_id;
            if (userId && userId !== 'anonymous') {
                ids.add(userId);
            }
        });
        
        const userIds = Array.from(ids);
        console.log(`📊 Found ${userIds.length} total users`);
        
        // Calculate LTV for all users
        const batchSize = 50;
        const allUserData = [];
        
        for (let i = 0; i < userIds.length; i += batchSize) {
            const batch = userIds.slice(i, i + batchSize);
            const ltvPromises = batch.map(userId => calculateUserLTV(userId));
            const results = await Promise.all(ltvPromises);
            
            allUserData.push(...results.filter(r => r !== null));
            console.log(`Processed ${Math.min(i + batchSize, userIds.length)}/${userIds.length} users`);
        }
        
        console.log(`✅ Calculated LTV for ${allUserData.length} users`);
        
        // Save to Supabase in batches
        console.log('💾 Saving to Supabase...');
        
        const supabaseBatchSize = 1000;
        for (let i = 0; i < allUserData.length; i += supabaseBatchSize) {
            const batch = allUserData.slice(i, i + supabaseBatchSize);
            
            const records = batch.map(user => ({
                user_id: user.userId,
                ltv: user.ltv,
                actual_revenue: user.actualRevenue,
                predicted_revenue: user.predictedRevenue,
                tier: user.tier,
                segments: user.segments,
                is_active: user.isActive,
                is_churned: user.isChurned,
                is_dormant: user.isDormant,
                days_since_last_activity: user.daysSinceLastActivity,
                engagement_score: user.metrics.engagement.score,
                retention_score: user.metrics.retention.score,
                progression_score: user.metrics.progression.score,
                total_sessions: user.metrics.engagement.totalSessions,
                total_puzzles_solved: user.metrics.engagement.totalPuzzlesSolved,
                conversion_intent: user.metrics.purchase.conversionIntent,
                should_target: user.adTargeting.shouldTarget,
                recommended_bid: parseFloat(user.adTargeting.maxBid),
                metrics: user.metrics,
                calculated_at: user.calculatedAt
            }));
            
            const { error } = await supabase
                .from('ltv_user_data')
                .upsert(records, { onConflict: 'user_id' });
            
            if (error) {
                console.error('Error saving batch to Supabase:', error);
            } else {
                console.log(`Saved batch ${i / supabaseBatchSize + 1} (${records.length} users)`);
            }
        }
        
        // Calculate summary statistics
        const tierCounts = {};
        allUserData.forEach(user => {
            tierCounts[user.tier] = (tierCounts[user.tier] || 0) + 1;
        });
        
        const summary = {
            total_users: allUserData.length,
            tier_distribution: tierCounts,
            metadata: {
                whales: tierCounts.whale || 0,
                high_value: tierCounts.high_value || 0,
                medium_value: tierCounts.medium_value || 0,
                low_value: tierCounts.low_value || 0,
                very_low: tierCounts.very_low || 0,
                active: allUserData.filter(u => u.isActive).length,
                dormant: allUserData.filter(u => u.isDormant).length,
                churned: allUserData.filter(u => u.isChurned).length
            }
        };
        
        // Save summary
        await supabase
            .from('ltv_audience_summary')
            .insert({
                total_users: summary.total_users,
                tier_distribution: summary.tier_distribution,
                metadata: summary.metadata
            });
        
        console.log('✅ Ad targeting audiences generated successfully');
        console.log('📊 Summary:', JSON.stringify(summary, null, 2));
        
        return summary;
        
    } catch (error) {
        console.error('❌ Error generating ad audiences:', error);
        throw error;
    }
}

// ============================================================================
// API ENDPOINTS
// ============================================================================

// Generate audiences
router.post('/generate-audiences', adminLimiter, validateAdmin, async (req, res) => {
    try {
        const summary = await generateAdTargetingAudiences();
        
        res.json({
            success: true,
            summary,
            message: 'Ad targeting audiences generated successfully'
        });
    } catch (error) {
        console.error('Error generating audiences:', error);
        res.status(500).json({
            success: false,
            message: 'Error generating audiences',
            error: error.message
        });
    }
});

// Get audience summary
router.get('/audiences', publicLimiter, async (req, res) => {
    try {
        // Get latest summary from Supabase
        const { data: summary, error } = await supabase
            .from('ltv_audience_summary')
            .select('*')
            .order('generated_at', { ascending: false })
            .limit(1)
            .single();
        
        if (error || !summary) {
            return res.status(404).json({
                success: false,
                message: 'No audiences found. Please generate audiences first.'
            });
        }
        
        // Get counts using SQL aggregation (much faster and no limit issues)
        const { data: tierStats, error: tierError } = await supabase
            .rpc('get_audience_counts');
        
        if (tierError) {
            console.error('Error getting tier counts:', tierError);
            // Fallback to summary data if RPC fails
            return res.json({
                success: true,
                data: {
                    audiences: {
                        whales: { count: summary.metadata?.whales || 0, userIds: [] },
                        high_value: { count: summary.metadata?.high_value || 0, userIds: [] },
                        medium_value: { count: summary.metadata?.medium_value || 0, userIds: [] },
                        low_value: { count: summary.metadata?.low_value || 0, userIds: [] },
                        very_low: { count: summary.metadata?.very_low || 0, userIds: [] },
                        active: { count: summary.metadata?.active || 0, userIds: [] },
                        dormant: { count: summary.metadata?.dormant || 0, userIds: [] },
                        churned: { count: summary.metadata?.churned || 0, userIds: [] }
                    },
                    generatedAt: summary.generated_at,
                    totalUsers: summary.total_users,
                    metadata: summary.metadata,
                    note: 'Using cached summary counts'
                }
            });
        }
        
        // Build audiences object from RPC results
        const audiences = {
            whales: { count: 0, userIds: [] },
            high_value: { count: 0, userIds: [] },
            medium_value: { count: 0, userIds: [] },
            low_value: { count: 0, userIds: [] },
            very_low: { count: 0, userIds: [] },
            active: { count: 0, userIds: [] },
            dormant: { count: 0, userIds: [] },
            churned: { count: 0, userIds: [] }
        };
        
        if (tierStats && tierStats.length > 0) {
            tierStats.forEach(stat => {
                if (stat.tier && audiences[stat.tier]) {
                    audiences[stat.tier].count = stat.count;
                }
                audiences.active.count = stat.active_count || 0;
                audiences.dormant.count = stat.dormant_count || 0;
                audiences.churned.count = stat.churned_count || 0;
            });
        }
        
        res.json({
            success: true,
            data: {
                audiences,
                generatedAt: summary.generated_at,
                totalUsers: summary.total_users,
                metadata: summary.metadata
            }
        });
        
    } catch (error) {
        console.error('Error fetching audiences:', error);
        res.status(500).json({
            success: false,
            message: 'Error fetching audiences',
            error: error.message
        });
    }
});

// Get individual user LTV
router.get('/user/:userId', publicLimiter, async (req, res) => {
    try {
        const { userId } = req.params;
        
        if (!userId || userId === 'undefined' || userId === 'null') {
            return res.status(400).json({
                success: false,
                message: 'Valid userId is required'
            });
        }
        
        // Try to get from Supabase first
        const { data: cached, error } = await supabase
            .from('ltv_user_data')
            .select('*')
            .eq('user_id', userId)
            .single();
        
        if (cached && !error) {
            return res.json({
                success: true,
                data: {
                    userId: cached.user_id,
                    ltv: parseFloat(cached.ltv),
                    actualRevenue: parseFloat(cached.actual_revenue),
                    predictedRevenue: parseFloat(cached.predicted_revenue),
                    tier: cached.tier,
                    segments: cached.segments,
                    metrics: cached.metrics,
                    adTargeting: {
                        shouldTarget: cached.should_target,
                        maxBid: cached.recommended_bid,
                        lookalikeTier: cached.tier
                    },
                    calculatedAt: cached.calculated_at
                },
                source: 'cache'
            });
        }
        
        // Calculate fresh if not in Supabase
        const ltv = await calculateUserLTV(userId);
        
        res.json({
            success: true,
            data: ltv,
            source: 'fresh'
        });
    } catch (error) {
        console.error('Error calculating user LTV:', error);
        res.status(500).json({
            success: false,
            message: 'Error calculating LTV',
            error: error.message
        });
    }
});

router.get('/export-audience/:tier', adminLimiter, validateAdmin, async (req, res) => {
    try {
        const { tier } = req.params;
        const { format } = req.query; // Add format parameter
        
        console.log(`📤 Export request for tier: ${tier}, format: ${format || 'user_id'}`);
        
        // Build query
        let query;
        
        if (tier === 'active') {
            query = supabase.from('ltv_user_data').select('user_id').eq('is_active', true);
        } else if (tier === 'dormant') {
            query = supabase.from('ltv_user_data').select('user_id').eq('is_dormant', true);
        } else if (tier === 'churned') {
            query = supabase.from('ltv_user_data').select('user_id').eq('is_churned', true);
        } else {
            query = supabase.from('ltv_user_data').select('user_id').eq('tier', tier);
        }
        
        // Fetch ALL users with pagination
        const allUsers = [];
        const pageSize = 1000;
        let page = 0;
        let hasMore = true;
        
        while (hasMore) {
            const { data: users, error } = await query.range(page * pageSize, (page + 1) * pageSize - 1);
            
            if (error) throw error;
            
            if (!users || users.length === 0) {
                hasMore = false;
            } else {
                allUsers.push(...users);
                console.log(`  Page ${page + 1}: ${users.length} users (total: ${allUsers.length})`);
                
                if (users.length < pageSize) {
                    hasMore = false;
                } else {
                    page++;
                }
            }
        }
        
        console.log(`✅ Total: ${allUsers.length} users in ${tier}`);
        
        if (allUsers.length === 0) {
            return res.status(404).json({
                success: false,
                message: `No users found in '${tier}' tier.`
            });
        }
        
        // If format=email, fetch emails from Firebase Auth
        let csv;
        
        if (format === 'email') {
            console.log('🔍 Fetching emails from Firebase Auth...');
            const { db, admin } = await import('../config/firebaseAdmin.js');
            
            const emails = [];
            const batchSize = 100;
            
            for (let i = 0; i < allUsers.length; i += batchSize) {
                const batch = allUsers.slice(i, i + batchSize);
                
                for (const user of batch) {
                    try {
                        const userRecord = await admin.auth().getUser(user.user_id);
                        if (userRecord.email) {
                            emails.push(userRecord.email);
                        }
                    } catch (error) {
                        console.error(`Failed to get email for ${user.user_id}`);
                    }
                }
                
                console.log(`  Fetched emails: ${emails.length}/${allUsers.length}`);
            }
            
            csv = emails.join('\n');
            console.log(`✅ Exported ${emails.length} emails`);
        } else {
            // Default: just user IDs
            csv = allUsers.map(u => u.user_id).join('\n');
        }
        
        res.setHeader('Content-Type', 'text/csv');
        res.setHeader('Content-Disposition', `attachment; filename="${tier}_audience_${new Date().toISOString().split('T')[0]}.csv"`);
        res.send(csv);
        
    } catch (error) {
        console.error('❌ Error exporting audience:', error);
        res.status(500).json({
            success: false,
            message: 'Error exporting audience',
            error: error.message
        });
    }
});

export default router;