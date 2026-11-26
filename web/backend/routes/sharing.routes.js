// routes/sharing.routes.js - Riddle sharing endpoints
import express from 'express';
import nodemailer from 'nodemailer';
import path from 'path';
import { admin, db } from '../config/firebaseAdmin.js';
import { singleRecipient, mailName, shareLimiters } from '../utils/shareMail.js';

const router = express.Router();
const __dirname = path.resolve();

// SMTP credentials come only from the environment (SHARE_SMTP_USER, SHARE_SMTP_PASS); no defaults in code.
let transporter = null;
function getTransporter() {
    const user = process.env.SHARE_SMTP_USER;
    const pass = process.env.SHARE_SMTP_PASS;
    if (!user || !pass) return null;
    if (!transporter) transporter = nodemailer.createTransport({ service: "gmail", auth: { user, pass } });
    return transporter;
}

/**
 * Share riddle via email
 */
router.post('/share-riddle', ...shareLimiters(), async (req, res) => {
    try {
        const { puzzleId, recipientEmail, recipientName, senderId } = req.body;

        if (!puzzleId || !recipientEmail || !recipientName || !senderId) {
            return res.status(400).json({ error: "Missing required fields" });
        }
        const to = singleRecipient(recipientEmail);
        if (!to || typeof puzzleId !== 'string' || puzzleId.length > 128) {
            return res.status(400).json({ error: "Invalid recipient or puzzle" });
        }
        const toName = mailName(recipientName, 'there');
        const fromName = mailName(senderId, 'A friend');

        const mailer = getTransporter();
        if (!mailer) {
            console.warn('Riddle share email disabled: set SHARE_SMTP_USER and SHARE_SMTP_PASS');
            return res.status(503).json({ error: "Email sharing is not configured" });
        }

        // A record of the share; nothing reads it back, so the email does not wait for it or fail with it.
        Promise.resolve()
            .then(() => db.collection('sharedRiddles').add({
                puzzleId,
                recipientEmail: to,
                recipientName: String(recipientName).slice(0, 80),
                senderId: String(senderId).slice(0, 128),
                sharedAt: admin.firestore.Timestamp.now(),
                status: "pending"
            }))
            .catch((e) => console.error('Share record not saved (non-fatal):', e.message));

        const deepLink = `https://puzzleverseai.com/open-riddle?puzzleId=${encodeURIComponent(puzzleId)}`;

        const mailOptions = {
            from: `"Puzzleverse" <${process.env.SHARE_SMTP_USER}>`,
            to,
            subject: `🧩 You've Been Invited to Solve a Riddle!`,
            html: `
                <h2>Hi ${toName},</h2>
                <p><strong>Your friend ${fromName} has shared a riddle with you!</strong></p>
                <p>Click below to open the puzzle in the app:</p>
                <p><a href="${deepLink}" style="background:#007bff;padding:10px 20px;color:white;text-decoration:none;border-radius:5px;">🔓 Open Riddle</a></p>
                <p>Don't have the app? <a href="https://puzzleverseai.com/download">Download it here.</a></p>
                <p>Happy puzzling! 🏁</p>
            `
        };

        await mailer.sendMail(mailOptions);

        res.json({ message: "Riddle shared successfully, email sent!" });

    } catch (error) {
        console.error("Error sharing riddle:", error);
        res.status(500).json({ error: "Failed to share riddle" });
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

    const deepLink = `puzzleverse://riddle?puzzleId=${encodeURIComponent(String(puzzleId)).replace(/'/g, '%27')}`;

    const userAgent = req.headers['user-agent'] || '';
    const isAndroid = /android/i.test(userAgent);
    const isIOS = /iphone|ipad|ipod/i.test(userAgent);

    if (isAndroid) {
        // Without the app installed the bare intent:// URL dead-ends in Chrome; the fallback sends the person to Google Play.
        const fallback = encodeURIComponent('https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=email_share&utm_medium=open_riddle&utm_campaign=riddle');
        return res.redirect(`intent://puzzleverseai.com/open-riddle?puzzleId=${encodeURIComponent(puzzleId)}#Intent;scheme=https;package=com.kreativekoala.riddleverse;S.browser_fallback_url=${fallback};end`);
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
