// routes/streakNotifications.routes.js - Streak notification management endpoints
import express from 'express';
import { firebaseStreakNotificationService } from '../services/firebaseStreakNotificationService.js';
import { supabase } from '../config/database.js';

const router = express.Router();

/**
 * Get streak notification service status
 */
router.get('/status', async (req, res) => {
    try {
        const status = firebaseStreakNotificationService.getServiceStatus();

        const { data: recentJobs, error } = await supabase
            .from('streak_notification_jobs')
            .select('*')
            .order('started_at', { ascending: false })
            .limit(10);

        if (error) {
            console.warn('⚠️ Could not fetch recent jobs from database:', error);
        }

        res.json({
            success: true,
            serviceStatus: status,
            recentJobsFromDB: recentJobs || [],
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ Error getting streak service status:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Manually trigger a streak check (for testing)
 */
router.post('/run-now', async (req, res) => {
    try {
        if (firebaseStreakNotificationService.isRunning) {
            return res.status(409).json({
                success: false,
                error: 'Streak notification check is already running'
            });
        }

        console.log('🔥 Manually triggering streak notification check...');
        firebaseStreakNotificationService.runPeriodicCheck();

        res.json({
            success: true,
            message: 'Streak notification check started',
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ Error manually triggering streak check:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Stop the periodic streak service
 */
router.post('/stop', async (req, res) => {
    try {
        firebaseStreakNotificationService.stopPeriodicService();

        res.json({
            success: true,
            message: 'Streak notification service stopped',
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ Error stopping streak service:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Start the periodic streak service
 */
router.post('/start', async (req, res) => {
    try {
        const { intervalMinutes = 60 } = req.body;

        firebaseStreakNotificationService.startPeriodicService(intervalMinutes);

        res.json({
            success: true,
            message: `Streak notification service started (${intervalMinutes} minute intervals)`,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ Error starting streak service:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Test specific user's streak status
 */
router.post('/test-user', async (req, res) => {
    try {
        const { userEmail } = req.body;

        if (!userEmail) {
            return res.status(400).json({
                success: false,
                error: 'userEmail is required'
            });
        }

        const results = {
            totalUsers: 1,
            atRiskUsers: 0,
            comebackUsers: 0,
            celebrationUsers: 0,
            notificationsSent: 0,
            errors: []
        };

        await firebaseStreakNotificationService.checkUserStreakStatus(userEmail, results, `test_${Date.now()}`);

        res.json({
            success: true,
            message: `Tested streak notifications for ${userEmail}`,
            results,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ Error testing user streak:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;
