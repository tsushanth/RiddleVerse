import express from 'express';
import nodemailer from 'nodemailer';
import axios from 'axios';
import path from 'path';
import { 
    resetPuzzleProgress, 
    resetAllUsersPuzzleProgress
} from '../services/puzzleService.js';
import { db } from '../config/firebaseAdmin.js';
import { supabase } from '../config/database.js';

const router = express.Router();

const __dirname = path.resolve();

// Email transporter configuration
const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASSWORD
    }
});

// Anthropic configuration
const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
const ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

// Save user preferences
router.post('/preferences', async (req, res) => {
    try {
        const { email, topics } = req.body;
        
        if (!email || !topics || !Array.isArray(topics)) {
            return res.status(400).json({
                success: false,
                error: "Missing required fields: email and topics (array)"
            });
        }
        
        // Save to Firebase Firestore
        const userRef = db.collection("user_topics").doc(email);
        await userRef.set({
            topics: topics,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        
        console.log(`Saved preferences for ${email}: ${topics.join(', ')}`);
        
        res.json({
            success: true,
            message: `Saved ${topics.length} topics successfully`,
            email: email,
            topics: topics
        });
        
    } catch (error) {
        console.error('Error saving user preferences:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get user rewards and badges
router.get("/rewards-badges", async (req, res) => {
    const { email } = req.query;
    if (!email) return res.status(400).json({ error: "Missing email" });

    try {
        const rewardsDoc = await db.collection("user_rewards").doc(email).get();
        const badgesDoc = await db.collection("user_badges").doc(email).get();

        const rewards = rewardsDoc.exists ? rewardsDoc.data().rewards || [] : [];
        const badges = badgesDoc.exists ? badgesDoc.data().badges || [] : [];

        return res.json({ rewards, badges });
    } catch (err) {
        console.error("Firestore error:", err);
        res.status(500).json({ error: "Failed to fetch user data" });
    }
});

// Submit feedback
router.post('/feedback', async (req, res) => {
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

// Submit rating
router.post('/rating', async (req, res) => {
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
            console.error('Rating insert error:', error);
            return res.status(500).json({ success: false, error: error.message });
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Rating endpoint error:', err);
        res.status(500).json({ success: false, error: 'Internal server error' });
    }
});

// Share riddle via email
router.post('/share-riddle', async (req, res) => {
    try {
        const { puzzleId, recipientEmail, recipientName, senderId } = req.body;

        if (!puzzleId || !recipientEmail || !recipientName || !senderId) {
            return res.status(400).json({ error: "Missing required fields" });
        }

        // Store in Firestore
        await db.collection('sharedRiddles').add({
            puzzleId,
            recipientEmail,
            recipientName,
            senderId,
            sharedAt: admin.firestore.Timestamp.now(),
            status: "pending"
        });

        // Generate Deep Link to Open in App
        const deepLink = `https://puzzleverseai.com/open-riddle?puzzleId=${puzzleId}`;

        // Email Content
        const mailOptions = {
            from: `"Puzzleverse" <puzzleverseai@gmail.com>`,
            to: recipientEmail,
            subject: `You've Been Invited to Solve a Riddle!`,
            html: `
                <h2>Hi ${recipientName},</h2>
                <p><strong>Your friend ${senderId} has shared a riddle with you!</strong></p>
                <p>Click below to open the puzzle in the app:</p>
                <p><a href="${deepLink}" style="background:#007bff;padding:10px 20px;color:white;text-decoration:none;border-radius:5px;">Open Riddle</a></p>
                <p>Don't have the app? <a href="https://puzzleverseai.com/download">Download it here.</a></p>
                <p>Happy puzzling!</p>
            `
        };

        // Send Email
        await transporter.sendMail(mailOptions);

        res.json({ message: "Riddle shared successfully, email sent!" });

    } catch (error) {
        console.error("Error sharing riddle:", error);
        res.status(500).json({ error: "Failed to share riddle" + error });
    }
});

// Open riddle deep link handler
router.get('/open-riddle', (req, res) => {
    const { puzzleId } = req.query;

    if (!puzzleId) {
        return res.status(400).send('Missing puzzleId');
    }

    const deepLink = `puzzleverse://riddle?puzzleId=${puzzleId}`;

    const userAgent = req.headers['user-agent'] || '';
    const isAndroid = /android/i.test(userAgent);
    const isIOS = /iphone|ipad|ipod/i.test(userAgent);

    if (isAndroid) {
        return res.redirect(`intent://puzzleverseai.com/open-riddle?puzzleId=${encodeURIComponent(puzzleId)}#Intent;scheme=https;package=com.kreativekoala.riddleverse;end`);
    } else if (isIOS) {
        return res.send(`
            <html>
                <head>
                    <meta http-equiv="refresh" content="0;url=${deepLink}" />
                    <script>
                        setTimeout(function() {
                            window.location.href = 'https://apps.apple.com/us/app/riddlerise/id6746389183';
                        }, 2000);
                    </script>
                </head>
                <body>
                    <p>Opening app...</p>
                </body>
            </html>
        `);
    } else {
        return res.send(`
            <html>
                <body>
                    <p>This link is intended to be opened on a mobile device.</p>
                </body>
            </html>
        `);
    }
});

// Reset user puzzle progress
router.get('/reset-puzzle-progress', (req, res) => {
    // Convert query params to body format
    req.body = {
        userId: req.query.userId,
        puzzleType: req.query.puzzleType,
        difficulty: req.query.difficulty
    };
    resetPuzzleProgress(req, res);
});

router.post('/reset-puzzle-progress', resetPuzzleProgress);

// Reset all users puzzle progress
router.get('/reset-all-users-puzzle-progress', (req, res) => {
    // Convert query params to body format
    req.body = {
        puzzleType: req.query.puzzleType,
        difficulty: req.query.difficulty
    };
    resetAllUsersPuzzleProgress(req, res);
});

router.post('/reset-all-users-puzzle-progress', resetAllUsersPuzzleProgress);


// Send notifications to all users
router.post('/send-notifications', async (req, res) => {
    const { title, body } = req.body;

    if (!title || !body) {
        return res.status(400).json({ error: 'Missing title or body' });
    }

    try {
        const snapshot = await db.collection('user_tokens').get();
        const tokens = [];

        snapshot.forEach(doc => {
            const data = doc.data();
            if (data.token) {
                tokens.push(data.token);
            }
        });

        if (tokens.length === 0) {
            return res.status(200).json({ message: 'No tokens found' });
        }

        const message = {
            notification: {
                title,
                body
            },
            tokens
        };

        const response = await admin.messaging().sendMulticast(message);
        console.log(`Sent to ${response.successCount}, failed: ${response.failureCount}`);

        res.status(200).json({
            success: true,
            sent: response.successCount,
            failed: response.failureCount
        });
    } catch (error) {
        console.error("Error sending notifications:", error);
        res.status(500).json({ success: false, error: error.message });
    }
});

// Call LLM endpoint
router.post('/call-llm', async (req, res) => {
    try {
        const { conversation } = req.body;
        
        if (!conversation || !Array.isArray(conversation)) {
            return res.status(400).json({ error: "Invalid request. 'conversation' must be an array." });
        }
        
        // Extract system messages
        const systemMsgs = conversation.filter(m => m.role === 'system');
        const nonSystemMsgs = conversation.filter(m => m.role !== 'system');
        const systemText = systemMsgs.map(m => typeof m.content === 'string' ? m.content : '').join('\n\n');

        const reqBody = {
            model: "claude-haiku-4-5-20251001",
            max_tokens: 2048,
            messages: nonSystemMsgs
        };
        if (systemText) {
            reqBody.system = systemText;
        }

        const response = await axios.post(ANTHROPIC_API_URL, reqBody, {
            headers: {
                'x-api-key': ANTHROPIC_API_KEY,
                'Content-Type': 'application/json',
                'anthropic-version': '2023-06-01'
            }
        });

        res.json({ message: (response.data.content?.[0]?.type === 'text' ? response.data.content[0].text.trim() : "") || "Call to Anthropic failed." });
    } catch (error) {
        console.error("Error calling Anthropic API:", error.response ? error.response.data : error.message);
        res.status(500).json({ error: "Failed to fetch response from AI API." });
    }
});

export default router;