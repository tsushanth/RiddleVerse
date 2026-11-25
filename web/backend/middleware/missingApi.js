// Answers for API paths that do not exist. Mounted after every real route and before the single-page-app catch-all, which
// would otherwise reply 200 with the web page: the apps read that as success ("puzzles ready") or failed to parse it.
import express from 'express';

// Called by shipped apps, never implemented. Topic puzzles cannot be played in the app, so generating them is retired.
export const RETIRED_PATHS = ['/generate-user-puzzles-with-notifications'];
// Referenced by app code but not served.
export const UNSERVED_PATHS = ['/get-daily-puzzle', '/check-token-status', '/group-completion-status'];

export function createMissingApiRouter() {
    const router = express.Router();
    router.all(RETIRED_PATHS, (req, res) => res.status(410).json({ success: false, error: 'This feature is not available', code: 'feature_unavailable' }));
    router.all(UNSERVED_PATHS, (req, res) => res.status(404).json({ success: false, error: 'Not found', code: 'not_found' }));
    router.use('/api', (req, res) => res.status(404).json({ success: false, error: 'Not found', code: 'not_found' }));
    return router;
}
