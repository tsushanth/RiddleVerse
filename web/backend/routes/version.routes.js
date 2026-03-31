// routes/version.routes.js - App version checking endpoints
import express from 'express';

const router = express.Router();

// App version configuration
const APP_VERSIONS = {
    ios: {
        latestVersion: "60.0",
        minimumVersion: "58.0",
        appStoreUrl: "https://apps.apple.com/us/app/riddleverse/id6746389183",
        whatsNew: [
            "Personalized puzzle groups based on your history",
            "Discover trending puzzles from the community",
            "New puzzle types added weekly",
            "Improved performance and bug fixes"
        ]
    },
    android: {
        latestVersion: "129.0",
        minimumVersion: "120.0",
        appStoreUrl: "https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse",
        whatsNew: [
            "Personalized puzzle groups based on your history",
            "Discover trending puzzles from the community",
            "New puzzle types added weekly",
            "Improved performance and bug fixes"
        ]
    }
};

/**
 * Compare version strings
 */
function compareVersions(v1, v2) {
    const parts1 = v1.split('.').map(Number);
    const parts2 = v2.split('.').map(Number);

    for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
        const part1 = parts1[i] || 0;
        const part2 = parts2[i] || 0;

        if (part1 < part2) return -1;
        if (part1 > part2) return 1;
    }

    return 0;
}

/**
 * Check app version and determine if update is needed
 */
router.post('/check-version', (req, res) => {
    const { platform, currentVersion } = req.body;

    if (!['ios', 'android'].includes(platform)) {
        return res.status(400).json({ error: 'Invalid platform' });
    }

    const config = APP_VERSIONS[platform];

    const needsUpdate = compareVersions(currentVersion, config.latestVersion) < 0;
    const isForced = compareVersions(currentVersion, config.minimumVersion) < 0;

    res.json({
        platform,
        latestVersion: config.latestVersion,
        currentUserVersion: currentVersion,
        needsUpdate,
        whatsNew: config.whatsNew,
        appStoreUrl: config.appStoreUrl,
        isForced
    });
});

export default router;
