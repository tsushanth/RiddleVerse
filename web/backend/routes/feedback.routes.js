// routes/feedback.routes.js - User feedback and rating endpoints
import express from 'express';
import { supabase } from '../config/database.js';

const router = express.Router();

/**
 * Submit user feedback
 */
router.post('/submit-feedback', async (req, res) => {
    try {
        const { userId, isPositive, message, timestamp, appVersion, deviceInfo } = req.body;

        const { data, error } = await supabase
            .from('user_feedback')
            .insert({
                user_id: userId,
                is_positive: isPositive,
                message: message,
                timestamp: timestamp,
                app_version: appVersion,
                device_info: deviceInfo,
                created_at: new Date().toISOString()
            });

        if (error) {
            console.error('Feedback insert error:', error);
            return res.status(500).json({ success: false, error: error.message });
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Feedback endpoint error:', err);
        res.status(500).json({ success: false, error: 'Internal server error' });
    }
});

/**
 * Submit user rating
 */
router.post('/submit-rating', async (req, res) => {
    try {
        const { userId, rating, review, timestamp, appVersion } = req.body;

        const { data, error } = await supabase
            .from('user_ratings')
            .insert({
                user_id: userId,
                rating: rating,
                review: review,
                timestamp: timestamp,
                app_version: appVersion,
                created_at: new Date().toISOString()
            });

        if (error) {
            console.error('Rating insert error details:', error);
            return res.status(500).json({ success: false, error: error.message });
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Rating endpoint error:', err);
        res.status(500).json({ success: false, error: 'Internal server error' });
    }
});

export default router;
