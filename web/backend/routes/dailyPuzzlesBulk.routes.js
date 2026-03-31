// routes/dailyPuzzlesBulk.routes.js
// New GET /api/puzzles/daily endpoint — returns all daily puzzles for requested types.
// No Firebase, no Redis, no user tracking. Same date = same puzzles for everyone.

import express from 'express';
import {
  getDailyPuzzles,
  getDailyCacheStats,
  refreshDailyPuzzles,
} from '../services/dailyPuzzles.js';

const router = express.Router();

/**
 * GET /api/puzzles/daily
 *
 * Query params:
 *   types      — comma-separated puzzle types (e.g. "anagram,trivia,wordsearch")
 *   difficulty — "easy" | "medium" | "hard"  (default: "easy")
 *   date       — YYYY-MM-DD (default: today UTC)
 *
 * Response:
 * {
 *   "date": "2026-03-28",
 *   "puzzles": {
 *     "anagram": [ ... ],
 *     "trivia": [ ... ]
 *   }
 * }
 */
let refreshPromise = null;

router.get('/daily', async (req, res) => {
  try {
    const { types, difficulty, date } = req.query;

    if (!types) {
      return res.status(400).json({
        success: false,
        error: 'Missing required query param: types (comma-separated list)',
      });
    }

    const typeList = types
      .split(',')
      .map((t) => t.trim().toLowerCase())
      .filter(Boolean);

    if (typeList.length === 0) {
      return res.status(400).json({
        success: false,
        error: 'types parameter must contain at least one puzzle type',
      });
    }

    const diff = difficulty || 'easy';

    // Check if cache has data for these types
    let result = getDailyPuzzles(typeList, diff, date || undefined);
    const hasData = Object.values(result.puzzles).some(arr => arr.length > 0);

    if (!hasData) {
      // Cache is empty — trigger refresh and wait (only one concurrent refresh)
      if (!refreshPromise) {
        console.log('[DailyPuzzlesBulk] Cache miss — triggering lazy refresh...');
        refreshPromise = refreshDailyPuzzles()
          .catch(err => console.error('[DailyPuzzlesBulk] Refresh failed:', err.message))
          .finally(() => { refreshPromise = null; });
      }
      await refreshPromise;
      result = getDailyPuzzles(typeList, diff, date || undefined);
    }

    return res.json({
      success: true,
      ...result,
    });
  } catch (error) {
    console.error('[DailyPuzzlesBulk] Error:', error);
    return res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

/**
 * GET /api/puzzles/daily/stats
 * Returns cache statistics for monitoring.
 */
router.get('/daily/stats', (req, res) => {
  try {
    const stats = getDailyCacheStats();
    return res.json({ success: true, ...stats });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * POST /api/puzzles/daily/refresh
 * Manually trigger a cache refresh (admin use).
 */
router.post('/daily/refresh', async (req, res) => {
  try {
    const result = await refreshDailyPuzzles();
    return res.json({ success: true, ...result });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
});

export default router;
