// routes/account.routes.js - User account management endpoints
import express from 'express';
import { supabase } from '../config/database.js';
import { admin, db } from '../config/firebaseAdmin.js';

const router = express.Router();

/**
 * Delete user account and all associated data
 */
router.delete('/delete-user-account', async (req, res) => {
    try {
        const { userId, email, confirmationText } = req.body;

        if (!userId) {
            return res.status(400).json({
                success: false,
                error: 'Missing required field: userId'
            });
        }

        const userEmail = email || userId;

        if (confirmationText !== 'DELETE') {
            return res.status(400).json({
                success: false,
                error: 'Invalid confirmation. Please type "DELETE" to confirm account deletion.'
            });
        }

        console.log(`🗑️ Starting account deletion for user: ${email}`);

        const deletionResults = {
            firestore: [],
            supabase: [],
            errors: []
        };

        // 1. Delete from Firestore collections
        const firestoreCollections = [
            'users',
            'user_topics',
            'user_tokens',
            'user_rewards',
            'user_badges',
            'leaderboard'
        ];

        for (const collection of firestoreCollections) {
            try {
                const userIdDoc = db.collection(collection).doc(userId);
                const userIdSnapshot = await userIdDoc.get();
                if (userIdSnapshot.exists) {
                    await userIdDoc.delete();
                    deletionResults.firestore.push(`${collection}:userId`);
                }

                if (['user_topics', 'user_rewards', 'user_badges'].includes(collection)) {
                    const emailDoc = db.collection(collection).doc(email);
                    const emailSnapshot = await emailDoc.get();
                    if (emailSnapshot.exists) {
                        await emailDoc.delete();
                        deletionResults.firestore.push(`${collection}:email`);
                    }
                }

                const queryByUserId = await db.collection(collection).where('userId', '==', userId).get();
                queryByUserId.forEach(async (doc) => {
                    await doc.ref.delete();
                    deletionResults.firestore.push(`${collection}:query:userId`);
                });

                const queryByEmail = await db.collection(collection).where('email', '==', email).get();
                queryByEmail.forEach(async (doc) => {
                    await doc.ref.delete();
                    deletionResults.firestore.push(`${collection}:query:email`);
                });

            } catch (error) {
                console.error(`Error deleting from Firestore ${collection}:`, error);
                deletionResults.errors.push(`Firestore ${collection}: ${error.message}`);
            }
        }

        // 2. Delete from Supabase tables
        const supabaseOperations = [
            { table: 'analytics_events', column: 'user_id', identifier: userId },
            { table: 'leaderboard', column: 'user_id', identifier: userId },
            { table: 'puzzle_performances', column: 'user_id', identifier: userId },
            { table: 'user_sessions', column: 'user_id', identifier: userId },
            { table: 'user_feedback', column: 'user_id', identifier: userId },
            { table: 'user_ratings', column: 'user_id', identifier: userId },
            { table: 'user_notification_tokens', column: 'user_email', identifier: userEmail },
            { table: 'user_generation_limits', column: 'user_email', identifier: userEmail },
            { table: 'user_notification_preferences', column: 'user_email', identifier: userEmail },
            { table: 'user_topics', column: 'user_email', identifier: userEmail },
            { table: 'daily_puzzles', column: 'user_email', identifier: userEmail },
            { table: 'generation_usage_log', column: 'user_email', identifier: userEmail },
            { table: 'notification_logs', column: 'user_email', identifier: userEmail },
            { table: 'puzzle_regeneration_requests', column: 'user_email', identifier: userEmail },
            { table: 'puzzle_sets', column: 'creator', identifier: userEmail },
            { table: 'custom_leaderboard', column: 'userid', identifier: userId },
        ];

        const supabasePromises = supabaseOperations.map(async ({ table, column, identifier }) => {
            try {
                const { data, error } = await supabase
                    .from(table)
                    .delete()
                    .eq(column, identifier);

                if (error) throw error;
                return { success: true, table, column, deletedCount: data?.length || 0 };
            } catch (error) {
                console.error(`Error deleting from Supabase ${table}:`, error);
                return { success: false, table, column, error: error.message };
            }
        });

        const supabaseResults = await Promise.allSettled(supabasePromises);

        let totalDeleted = 0;
        supabaseResults.forEach((result, index) => {
            if (result.status === 'fulfilled') {
                if (result.value.success) {
                    deletionResults.supabase.push(`${result.value.table}:${result.value.column}`);
                    totalDeleted += result.value.deletedCount || 0;
                } else {
                    deletionResults.errors.push(`Supabase ${result.value.table}: ${result.value.error}`);
                }
            } else {
                const operation = supabaseOperations[index];
                deletionResults.errors.push(`Supabase ${operation.table}: Promise rejected - ${result.reason}`);
            }
        });

        console.log(`✅ Account deletion completed for ${email}`);
        console.log(`📊 Deleted ${totalDeleted} records from Supabase`);

        res.json({
            success: true,
            message: `Account successfully deleted for ${email}`,
            deletionSummary: {
                firestoreDeleted: deletionResults.firestore.length,
                supabaseTablesProcessed: deletionResults.supabase.length,
                supabaseRecordsDeleted: totalDeleted,
                errors: deletionResults.errors.length,
                totalOperations: firestoreCollections.length + supabaseOperations.length
            },
            details: deletionResults,
            warning: 'Account deletion completed. This action cannot be undone.'
        });

    } catch (error) {
        console.error('❌ Error during account deletion:', error);
        res.status(500).json({
            success: false,
            error: 'Failed to delete account',
            message: error.message
        });
    }
});

/**
 * Get user rewards and badges
 */
router.get('/get-user-rewards-badges', async (req, res) => {
    const { email } = req.query;
    if (!email) return res.status(400).json({ error: "Missing email" });

    try {
        const rewardsDoc = await db.collection("user_rewards").doc(email).get();
        const badgesDoc = await db.collection("user_badges").doc(email).get();

        const rewards = rewardsDoc.exists ? rewardsDoc.data().rewards || [] : [];
        const badges = badgesDoc.exists ? badgesDoc.data().badges || [] : [];

        return res.json({ rewards, badges });
    } catch (err) {
        console.error("❌ Firestore error:", err);
        res.status(500).json({ error: "Failed to fetch user data" });
    }
});

/**
 * Save user preferences/topics
 */
router.post('/save-user-preferences', async (req, res) => {
    try {
        const { email, topics } = req.body;

        if (!email || !topics || !Array.isArray(topics)) {
            return res.status(400).json({
                success: false,
                error: "Missing required fields: email and topics (array)"
            });
        }

        const userRef = db.collection("user_topics").doc(email);
        await userRef.set({
            topics: topics,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        console.log(`✅ Saved preferences for ${email}: ${topics.join(', ')}`);

        res.json({
            success: true,
            message: `Saved ${topics.length} topics successfully`,
            email: email,
            topics: topics
        });

    } catch (error) {
        console.error('❌ Error saving user preferences:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;
