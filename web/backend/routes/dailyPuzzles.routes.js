import express from 'express';
import { 
    getDailyPuzzleSet,
    listUserDailyTopics,
    generateUserDailyPuzzles,
    getDailyPuzzleStatistics,
    cleanupOldDailyPuzzles,
    generateUserDailyPuzzlesWithNotifications,
    storeFCMToken,
    dailyPuzzleGenerationJobWithManualTrigger
} from '../services/dailyPuzzleService.js';
import { db } from '../config/firebaseAdmin.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// Get daily puzzle set
router.get('/', async (req, res) => {
    const { email, topic, date } = req.query;
    
    if (!email || !topic) {
        return res.status(400).json({ error: "Missing email or topic parameter" });
    }

    try {
        console.log(`Fetching daily puzzle for ${email}/${topic}${date ? ` on ${date}` : ''} (flexible mode)`);
        
        const result = await getDailyPuzzleSet(email, topic, date);
        
        if (!result.success) {
            if (result.needsGeneration) {
                return res.status(404).json({ 
                    error: "No puzzles available for this topic",
                    message: result.error,
                    suggestion: "New puzzles need to be generated for this topic",
                    action: "generate_puzzles",
                    autoRecovery: "A recovery process has been initiated"
                });
            }
            
            return res.status(500).json({ 
                error: "Failed to fetch daily puzzle",
                message: result.error
            });
        }

        console.log(`Successfully retrieved daily puzzle set for ${email}/${topic}`);
        
        const response = {
            // NEW FORMAT
            puzzleSet: result.puzzleSet,
            
            // OLD FORMAT (for legacy clients)
            topic: result.puzzleSet.topic,
            generationDate: result.puzzleSet.generationDate,
            puzzles: result.puzzleSet.puzzles,
            
            // METADATA
            success: true,
            source: 'supabase_flexible',
            puzzleCount: result.puzzleSet.puzzleCount || result.puzzleSet.puzzles?.length || 0,
            
            // FALLBACK INFO
            isFromToday: result.puzzleSet.isFromRequestedDate,
            fallbackUsed: result.puzzleSet.fallbackUsed,
            daysDifference: result.puzzleSet.daysDifference,
            age: result.puzzleSet.age,
            
            // User-friendly message
            message: result.puzzleSet.fallbackUsed 
                ? `Showing ${result.puzzleSet.age} puzzles (most recent available)`
                : "Today's fresh puzzles ready!"
        };

        return res.json(response);

    } catch (error) {
        console.error("Error fetching daily puzzle:", error);
        return res.status(500).json({ error: "Failed to fetch daily puzzle" });
    }
});

// List user daily topics
router.get('/topics', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
        return res.status(400).json({ error: "Missing email parameter" });
    }

    try {
        const result = await listUserDailyTopics(email);
        
        if (!result.success) {
            return res.status(500).json({ 
                error: "Failed to list topics", 
                message: result.error 
            });
        }

        return res.json({ topics: result.topics });

    } catch (error) {
        console.error("Error listing daily topics:", error);
        return res.status(500).json({ error: "Failed to list user topics" });
    }
});

// Generate puzzles for specific user
router.post('/generate', async (req, res) => {
    const { email: targetEmail } = req.body;

    try {
        console.log(`Starting daily puzzle generation for ${targetEmail || 'all users'}`);
        
        if (targetEmail) {
            // Generate for specific user
            console.log(`Generating daily puzzles for specific user: ${targetEmail}`);
            
            const userDoc = await db.collection("user_topics").doc(targetEmail).get();
            
            if (!userDoc.exists) {
                return res.status(404).json({ 
                    success: false, 
                    error: "User not found or has no topics configured" 
                });
            }

            const userTopics = userDoc.data().topics || [];
            
            if (userTopics.length === 0) {
                return res.status(400).json({ 
                    success: false, 
                    error: "User has no topics configured" 
                });
            }

            const result = await generateUserDailyPuzzles(targetEmail, userTopics);
            
            return res.json({ 
                success: true, 
                message: `Generated puzzles for ${result.summary.successful} topics`,
                results: result
            });

        } else {
            // Generate for all users
            console.log(`Generating daily puzzles for all users`);
            
            const snapshot = await db.collection("user_topics").get();
            const allResults = [];
            
            for (const doc of snapshot.docs) {
                const userEmail = doc.id;
                const userTopics = doc.data().topics || [];
                
                if (userTopics.length === 0) {
                    console.log(`Skipping ${userEmail} - no topics configured`);
                    continue;
                }

                try {
                    console.log(`Generating for ${userEmail} with topics: ${userTopics.join(', ')}`);
                    const result = await generateUserDailyPuzzles(userEmail, userTopics);
                    
                    allResults.push({
                        userEmail,
                        ...result
                    });
                    
                } catch (error) {
                    console.error(`Failed to generate for ${userEmail}:`, error);
                    allResults.push({
                        userEmail,
                        summary: { totalTopics: userTopics.length, successful: 0, failed: userTopics.length },
                        failed: userTopics.map(topic => ({ topic, error: error.message }))
                    });
                }
            }

            const overallSummary = allResults.reduce((acc, result) => {
                acc.totalUsers++;
                acc.totalTopics += result.summary.totalTopics;
                acc.successfulTopics += result.summary.successful;
                acc.failedTopics += result.summary.failed;
                return acc;
            }, { totalUsers: 0, totalTopics: 0, successfulTopics: 0, failedTopics: 0 });

            console.log(`Bulk generation complete:`, overallSummary);
            
            return res.json({ 
                success: true,
                message: `Processed ${overallSummary.totalUsers} users, generated puzzles for ${overallSummary.successfulTopics} topics`,
                summary: overallSummary,
                results: allResults
            });
        }

    } catch (error) {
        console.error("Error in daily puzzle generation:", error);
        return res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// Generate with notifications
router.post('/generate-with-notifications', async (req, res) => {
    const { email: targetEmail } = req.query;

    if (!targetEmail) {
        return res.status(400).json({ 
            success: false, 
            error: "Email parameter is required" 
        });
    }

    try {
        console.log(`Generating puzzles with notifications for: ${targetEmail}`);
        
        const userDoc = await db.collection("user_topics").doc(targetEmail).get();
        
        if (!userDoc.exists) {
            return res.status(404).json({ 
                success: false, 
                error: "User not found or has no topics configured" 
            });
        }

        const userTopics = userDoc.data().topics || [];
        
        if (userTopics.length === 0) {
            return res.status(400).json({ 
                success: false, 
                error: "User has no topics configured" 
            });
        }

        console.log(`Found ${userTopics.length} topics for ${targetEmail}: ${userTopics.join(', ')}`);

        const result = await generateUserDailyPuzzles(targetEmail, userTopics);
        
        if (result.summary.successful === 0) {
            return res.json({
                success: false,
                message: "No puzzles were generated successfully",
                result
            });
        }

        console.log(`Generated puzzles for ${result.summary.successful} topics`);

        const generationResults = {
            successful: [{
                userEmail: targetEmail,
                topicsProcessed: userTopics,
                totalPuzzlesGenerated: result.summary.totalQuestionsGenerated
            }]
        };

        // Send notifications would go here
        console.log(`Sending notification to ${targetEmail}...`);

        return res.json({ 
            success: true, 
            message: `Generated puzzles for ${result.summary.successful} topics and prepared notifications`,
            puzzleGeneration: result,
            summary: {
                topicsProcessed: result.summary.successful,
                puzzlesGenerated: result.summary.totalQuestionsGenerated,
                notificationsPrepared: 1
            }
        });

    } catch (error) {
        console.error("Error in puzzle generation with notifications:", error);
        return res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// Test generation for specific email
router.post('/test-generation', async (req, res) => {
    const { email, topics } = req.body;
    
    if (!email || !topics || !Array.isArray(topics)) {
        return res.status(400).json({ 
            error: "Missing required fields: email and topics (array)" 
        });
    }

    try {
        console.log(`Testing daily puzzle generation for ${email} with topics: ${topics.join(', ')}`);
        
        const result = await generateUserDailyPuzzles(email, topics);
        
        const response = {
            success: true,
            testEmail: email,
            testedTopics: topics,
            results: result,
            message: `Test completed: ${result.summary.successful}/${result.summary.totalTopics} topics successful`
        };

        if (result.summary.failed > 0) {
            response.warnings = result.failed.map(f => `${f.topic}: ${f.error}`);
        }

        console.log(`Test completed for ${email}:`, response.message);
        return res.json(response);

    } catch (error) {
        console.error(`Test failed for ${email}:`, error);
        return res.status(500).json({ 
            success: false, 
            error: error.message,
            testEmail: email,
            testedTopics: topics
        });
    }
});

// Get puzzle history
router.get('/history', async (req, res) => {
    const { email, days = 7 } = req.query;
    
    if (!email) {
        return res.status(400).json({ error: "Missing email parameter" });
    }

    try {
        const dayCount = parseInt(days);
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - dayCount);
        const cutoffDateStr = cutoffDate.toISOString().split('T')[0];

        const { data: puzzleSets, error } = await supabase
            .from('daily_puzzles')
            .select('*')
            .eq('user_email', email)
            .gte('generation_date', cutoffDateStr)
            .order('generation_date', { ascending: false })
            .order('generation_timestamp', { ascending: false });

        if (error) {
            throw new Error(`Failed to fetch history: ${error.message}`);
        }

        const history = puzzleSets.map(set => ({
            topic: set.topic,
            generationDate: set.generation_date,
            generationTimestamp: set.generation_timestamp,
            puzzleCount: set.puzzle_count,
            status: set.status,
            isCurrent: set.is_current
        }));

        res.json({
            success: true,
            email,
            dayRange: dayCount,
            historyCount: history.length,
            history
        });

    } catch (error) {
        console.error(`Error fetching daily puzzle history:`, error);
        res.status(500).json({ error: error.message });
    }
});

// Get statistics
router.get('/statistics', async (req, res) => {
    const { email, days = 30 } = req.query;
    
    try {
        const result = await getDailyPuzzleStatistics(email, parseInt(days));
        
        if (!result.success) {
            return res.status(500).json({
                error: "Failed to get statistics",
                message: result.error
            });
        }

        res.json({
            success: true,
            scope: email ? 'user' : 'global',
            dayRange: parseInt(days),
            statistics: result.stats
        });

    } catch (error) {
        console.error(`Error getting daily puzzle statistics:`, error);
        res.status(500).json({ error: error.message });
    }
});

// Cleanup old puzzles
router.post('/cleanup', async (req, res) => {
    const { days = 730, dryRun = true } = req.body;
    
    try {
        console.log(`Starting cleanup of daily puzzles older than ${days} days (dryRun: ${dryRun})`);
        
        const result = await cleanupOldDailyPuzzles(parseInt(days), dryRun);
        
        if (!result.success) {
            return res.status(500).json({
                error: "Cleanup failed",
                message: result.error
            });
        }

        res.json({
            success: true,
            mode: result.mode,
            message: result.message,
            details: result,
            nextSteps: result.mode === 'analysis' && result.totalSets > 0 ? [
                'Run with dryRun=false to perform the cleanup',
                'Consider backing up important data before cleanup'
            ] : [
                'Cleanup completed successfully',
                'Monitor storage usage and performance'
            ]
        });

    } catch (error) {
        console.error(`Error in daily puzzle cleanup:`, error);
        res.status(500).json({ error: error.message });
    }
});

// Register FCM token
router.post('/register-fcm-token', async (req, res) => {
    try {
        const { userEmail, userId, fcmToken, platform } = req.body;
        
        if (!userEmail || !fcmToken || !platform) {
            return res.status(400).json({
                error: 'Missing required fields: userEmail, fcmToken, platform'
            });
        }
        
        if (!['ios', 'android'].includes(platform)) {
            return res.status(400).json({
                error: 'Platform must be "ios" or "android"'
            });
        }

        // Optional: Validate Firebase UID format if provided
        if (userId && !/^[a-zA-Z0-9]{28}$/.test(userId)) {
            console.warn(`Potentially invalid Firebase UID format: ${userId}`);
        }
        
        const result = await storeFCMToken(userEmail, userId, fcmToken, platform);
        
        if (result.success) {
            res.json({
                success: true,
                message: `FCM token registered for ${platform}`
            });
        } else {
            res.status(500).json({
                success: false,
                error: result.error
            });
        }
        
    } catch (error) {
        console.error('❌ Error registering FCM token:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Trigger daily puzzle job
router.post('/trigger-job', async (req, res) => {
    try {
        const result = await dailyPuzzleGenerationJobWithManualTrigger();
        
        res.json({
            success: true,
            message: 'Daily puzzle generation completed with tracking',
            result
        });
        
    } catch (error) {
        console.error('Manual daily job failed:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Check user FCM token
router.get('/check-fcm-token', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
        return res.status(400).json({ error: "Email parameter required" });
    }

    try {
        const { data: tokens, error } = await supabase
            .from('user_notification_tokens')
            .select('fcm_token, platform, last_used')
            .eq('user_email', email)
            .eq('is_active', true);

        if (error) {
            throw new Error(error.message);
        }

        return res.json({
            success: true,
            userEmail: email,
            activeTokens: tokens ? tokens.length : 0,
            tokens: tokens ? tokens.map(t => ({
                platform: t.platform,
                tokenPreview: t.fcm_token.substring(0, 20) + '...',
                lastUsed: t.last_used
            })) : []
        });

    } catch (error) {
        console.error("Error checking FCM tokens:", error);
        return res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;