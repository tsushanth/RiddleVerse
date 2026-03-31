import express from 'express';
import jwt from 'jsonwebtoken';
import { admin, db } from '../config/firebaseAdmin.js';
import { JWT_SECRET } from '../config/constants.js';

const router = express.Router();

// Google Authentication
router.post("/google", async (req, res) => {
    const { idToken } = req.body;

    try {
        console.log("Received ID Token:", idToken);
        
        // Verify Google ID Token (From Firebase Auth)
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        console.log("✅ Decoded Token:", decodedToken);

        const email = decodedToken.email;
        const userId = decodedToken.uid;

        // Check if user exists in Firestore
        const userRef = db.collection("users").doc(userId);
        let userDoc = await userRef.get();

        if (!userDoc.exists) {
            await userRef.set({
                email,
                lastPuzzleTimestamp: null,
                puzzlesAttempted: 0,
                puzzlesSolved: 0
            });
        } else {
            console.log("✅ User exists. Updating last login timestamp.");
            await userRef.update({
                lastLogin: admin.firestore.FieldValue.serverTimestamp()
            });
        }

        // Create JWT for session authentication
        const token = jwt.sign({ email, userId }, JWT_SECRET, { expiresIn: "24h" });
        console.log("signed token:", token);

        res.json({ success: true, token, email, userId });

    } catch (error) {
        console.error("Google Auth Error:", error);
        res.status(401).json({ success: false, message: "Invalid Google Token" });
    }
});

export default router;