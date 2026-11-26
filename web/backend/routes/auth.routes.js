import express from 'express';
import jwt from 'jsonwebtoken';
import { admin, db } from '../config/firebaseAdmin.js';
import { verifyToken } from '../auth/verifyIdentity.js';
import { firestoreRead } from '../utils/firestoreBestEffort.js';
import { JWT_SECRET } from '../config/constants.js';

const router = express.Router();

// Google Authentication
router.post("/google", async (req, res) => {
    const { idToken } = req.body;

    try {
        // Verify Google ID Token (From Firebase Auth)
        const identity = await verifyToken(idToken);
        // Firebase: raw is the decoded token (unchanged). Supabase: uid is the canonical uid.
        const decodedToken = identity.provider === 'firebase' ? identity.raw : { ...identity.raw, uid: identity.uid };
        const email = decodedToken.email;
        const userId = decodedToken.uid;

        // The Firestore user document is bookkeeping: a quota or outage there must not read as "invalid token".
        try {
            const userRef = db.collection("users").doc(userId);
            const userDoc = await firestoreRead(() => userRef.get());

            if (!userDoc.exists) {
                await userRef.set({
                    email,
                    lastPuzzleTimestamp: null,
                    puzzlesAttempted: 0,
                    puzzlesSolved: 0
                });
            } else {
                await userRef.update({
                    lastLogin: admin.firestore.FieldValue.serverTimestamp()
                });
            }
        } catch (bookkeepingError) {
            console.error('Sign-in bookkeeping failed (non-fatal):', bookkeepingError.message);
        }

        // Create JWT for session authentication
        const token = jwt.sign({ email, userId }, JWT_SECRET, { expiresIn: "24h" });
        res.json({ success: true, token, email, userId });

    } catch (error) {
        console.error("Google Auth Error:", error);
        res.status(401).json({ success: false, message: "Invalid Google Token" });
    }
});

export default router;