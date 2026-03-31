import admin from "firebase-admin";

// Allow smoke tests to run during Docker build (env vars not yet available)
const IS_SMOKE_TEST = process.env.DOCKER_BUILD === 'true' ||
                       process.argv.some(arg => arg.includes('smoke-tests'));

// Load Firebase service account from environment variable (JSON string)
function getServiceAccountKey() {
    const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_KEY;

    if (!serviceAccountJson) {
        if (IS_SMOKE_TEST) {
            console.warn('⚠️ FIREBASE credentials not available during smoke test - skipping initialization');
            return null;
        }
        throw new Error('Missing FIREBASE_SERVICE_ACCOUNT_KEY environment variable');
    }

    try {
        return JSON.parse(serviceAccountJson);
    } catch (error) {
        throw new Error('Invalid FIREBASE_SERVICE_ACCOUNT_KEY - must be valid JSON');
    }
}

// Initialize Firebase Admin
let db = null;
const serviceAccountKey = getServiceAccountKey();

if (serviceAccountKey && !admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccountKey),
        databaseURL: process.env.FIREBASE_DATABASE_URL || `https://${serviceAccountKey.project_id}.firebaseio.com`
    });
    db = admin.firestore();
}

export { admin, db };