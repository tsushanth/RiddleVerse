import express from 'express';
import { analyticsService } from '../services/analyticsService.js';

const router = express.Router();

// Get today's DAU
router.get('/dau', async (req, res) => {
    try {
        const { date } = req.query;
        const dauCount = await analyticsService.getDailyActiveUsers(date);
        
        res.json({
            success: true,
            date: date || analyticsService.getCurrentDateString(),
            dau: dauCount,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting DAU:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get this month's MAU
router.get('/mau', async (req, res) => {
    try {
        const { month } = req.query;
        const mauCount = await analyticsService.getMonthlyActiveUsers(month);
        
        res.json({
            success: true,
            month: month || analyticsService.getCurrentMonthString(),
            mau: mauCount,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting MAU:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get DAU trend (last 30 days)
router.get('/dau-trend', async (req, res) => {
    try {
        const { days = 30 } = req.query;
        const trend = await analyticsService.getDauTrend(parseInt(days));
        
        // Calculate summary statistics
        const validDataPoints = trend.filter(p => !p.error);
        const totalDau = validDataPoints.reduce((sum, p) => sum + p.count, 0);
        const avgDau = validDataPoints.length > 0 ? totalDau / validDataPoints.length : 0;
        const maxDau = validDataPoints.length > 0 ? Math.max(...validDataPoints.map(p => p.count)) : 0;
        const minDau = validDataPoints.length > 0 ? Math.min(...validDataPoints.map(p => p.count)) : 0;
        
        res.json({
            success: true,
            trend,
            summary: {
                totalDays: parseInt(days),
                validDataPoints: validDataPoints.length,
                averageDau: Math.round(avgDau),
                maxDau,
                minDau,
                totalUniqueDau: totalDau
            },
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting DAU trend:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get user retention metrics
router.get('/retention', async (req, res) => {
    try {
        const retentionMetrics = await analyticsService.getRetentionMetrics();
        
        res.json({
            success: true,
            retention: retentionMetrics,
            insights: {
                retentionQuality: retentionMetrics.sevenDayRetention >= 20 ? 'good' : 
                                 retentionMetrics.sevenDayRetention >= 10 ? 'average' : 'needs_improvement',
                monthlyGrowth: retentionMetrics.thirtyDayActiveUsers > retentionMetrics.sevenDayActiveUsers ? 'growing' : 'stable',
                userBase: retentionMetrics.thirtyDayActiveUsers >= 1000 ? 'large' :
                         retentionMetrics.thirtyDayActiveUsers >= 100 ? 'medium' : 'small'
            },
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting retention metrics:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get comprehensive analytics dashboard
router.get('/dashboard', async (req, res) => {
    try {
        console.log('Generating analytics dashboard...');
        const dashboard = await analyticsService.getAnalyticsDashboard();
        
        res.json({
            success: true,
            dashboard,
            recommendations: generateAnalyticsRecommendations(dashboard),
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error generating analytics dashboard:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get event-based analytics
router.get('/events', async (req, res) => {
    try {
        const { days = 30 } = req.query;
        const eventAnalytics = await analyticsService.getEventAnalytics(parseInt(days));
        
        res.json({
            success: true,
            period: `${days} days`,
            analytics: eventAnalytics,
            insights: {
                engagementLevel: eventAnalytics.avgEventsPerUser >= 10 ? 'high' :
                               eventAnalytics.avgEventsPerUser >= 5 ? 'medium' : 'low',
                completionHealth: eventAnalytics.completionRate >= 70 ? 'excellent' :
                                 eventAnalytics.completionRate >= 50 ? 'good' :
                                 eventAnalytics.completionRate >= 30 ? 'average' : 'poor',
                topEvent: Object.entries(eventAnalytics.eventBreakdown)
                    .sort(([,a], [,b]) => b - a)[0]?.[0] || 'none',
                topPuzzleType: Object.entries(eventAnalytics.puzzleTypeBreakdown)
                    .sort(([,a], [,b]) => b - a)[0]?.[0] || 'none'
            },
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting event analytics:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Quick analytics summary
router.get('/summary', async (req, res) => {
    try {
        console.log('Generating quick analytics summary...');
        
        const [dauToday, mauThisMonth, retentionMetrics] = await Promise.all([
            analyticsService.getDailyActiveUsers(),
            analyticsService.getMonthlyActiveUsers(),
            analyticsService.getRetentionMetrics()
        ]);

        const summary = {
            dau: dauToday,
            mau: mauThisMonth,
            sevenDayRetention: Math.round(retentionMetrics.sevenDayRetention * 10) / 10,
            thirtyDayRetention: Math.round(retentionMetrics.thirtyDayRetention * 10) / 10,
            activeUsers: retentionMetrics.currentActiveUsers,
            healthScore: analyticsService.calculateHealthScore({
                dauGrowth: 0,
                mauGrowth: 0,
                sevenDayRetention: retentionMetrics.sevenDayRetention,
                completionRate: 50
            })
        };

        res.json({
            success: true,
            summary,
            lastUpdated: new Date().toISOString(),
            note: 'This is a quick summary. Use /api/analytics/dashboard for complete analytics.'
        });
    } catch (error) {
        console.error('Error generating analytics summary:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Clear analytics cache
router.post('/clear-cache', async (req, res) => {
    try {
        analyticsService.clearCache();
        
        res.json({
            success: true,
            message: 'Analytics cache cleared successfully',
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error clearing analytics cache:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Analytics cache stats
router.get('/cache-stats', async (req, res) => {
    try {
        const stats = analyticsService.getCacheStats();
        
        res.json({
            success: true,
            cache: stats,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error getting cache stats:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Helper function for recommendations
function generateAnalyticsRecommendations(dashboard) {
    const recommendations = [];
    
    // DAU recommendations
    if (dashboard.dau.growth < -10) {
        recommendations.push({
            priority: 'high',
            category: 'user_acquisition',
            issue: 'Daily active users declining',
            action: 'Review user experience and implement retention strategies',
            metric: `DAU decreased by ${Math.abs(dashboard.dau.growth).toFixed(1)}%`
        });
    } else if (dashboard.dau.growth > 20) {
        recommendations.push({
            priority: 'info',
            category: 'growth',
            issue: 'Strong DAU growth',
            action: 'Analyze what drove this growth and replicate successful strategies',
            metric: `DAU increased by ${dashboard.dau.growth.toFixed(1)}%`
        });
    }
    
    // Retention recommendations
    if (dashboard.retention.sevenDayRetention < 15) {
        recommendations.push({
            priority: 'high',
            category: 'retention',
            issue: 'Low user retention rate',
            action: 'Improve onboarding experience and add engagement features',
            metric: `7-day retention: ${dashboard.retention.sevenDayRetention.toFixed(1)}%`
        });
    }
    
    // Engagement recommendations
    if (dashboard.engagement.completionRate < 40) {
        recommendations.push({
            priority: 'medium',
            category: 'engagement',
            issue: 'Low puzzle completion rate',
            action: 'Review puzzle difficulty and user experience in puzzle flow',
            metric: `Completion rate: ${dashboard.engagement.completionRate.toFixed(1)}%`
        });
    }
    
    // Health score recommendations
    if (dashboard.summary.healthScore < 60) {
        recommendations.push({
            priority: 'critical',
            category: 'overall_health',
            issue: 'Low overall platform health',
            action: 'Conduct comprehensive user experience audit',
            metric: `Health score: ${dashboard.summary.healthScore}/100`
        });
    }
    
    return recommendations;
}

export default router;