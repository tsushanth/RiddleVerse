// routes/sharing.routes.js - Riddle sharing endpoints
import express from 'express';
import nodemailer from 'nodemailer';
import path from 'path';
import { admin, db } from '../config/firebaseAdmin.js';

const router = express.Router();
const __dirname = path.resolve();

const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
        user: "puzzleverseai@gmail.com",
        pass: "duoo ukes bjkx nanj"
    }
});

/**
 * Share riddle via email
 */
router.post('/share-riddle', async (req, res) => {
    try {
        const { puzzleId, recipientEmail, recipientName, senderId } = req.body;

        if (!puzzleId || !recipientEmail || !recipientName || !senderId) {
            return res.status(400).json({ error: "Missing required fields" });
        }

        await db.collection('sharedRiddles').add({
            puzzleId,
            recipientEmail,
            recipientName,
            senderId,
            sharedAt: admin.firestore.Timestamp.now(),
            status: "pending"
        });

        const deepLink = `https://puzzleverseai.com/open-riddle?puzzleId=${puzzleId}`;

        const mailOptions = {
            from: `"Puzzleverse" <puzzleverseai@gmail.com>`,
            to: recipientEmail,
            subject: `🧩 You've Been Invited to Solve a Riddle!`,
            html: `
                <h2>Hi ${recipientName},</h2>
                <p><strong>Your friend ${senderId} has shared a riddle with you!</strong></p>
                <p>Click below to open the puzzle in the app:</p>
                <p><a href="${deepLink}" style="background:#007bff;padding:10px 20px;color:white;text-decoration:none;border-radius:5px;">🔓 Open Riddle</a></p>
                <p>Don't have the app? <a href="https://puzzleverseai.com/download">Download it here.</a></p>
                <p>Happy puzzling! 🏁</p>
            `
        };

        await transporter.sendMail(mailOptions);

        res.json({ message: "Riddle shared successfully, email sent!" });

    } catch (error) {
        console.error("Error sharing riddle:", error);
        res.status(500).json({ error: "Failed to share riddle" + error });
    }
});

/**
 * Open riddle deep link handler
 */
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

export default router;
