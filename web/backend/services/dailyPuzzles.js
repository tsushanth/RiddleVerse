// services/dailyPuzzles.js — Simple in-memory daily puzzle cache
// Replaces the Redis-based dailyPuzzleCache with a plain JS Map.
// On startup + daily cron, generates/fetches puzzles for ALL types.
// Same date + type + difficulty = same puzzles for everyone.

import { supabase } from '../config/database.js';
import { PuzzleGenerator } from './puzzleGeneration.js';
import { PUZZLE_TYPES } from '../config/puzzleConfig.js';
import { DEFAULT_LANGUAGE, langCacheSuffix } from '../utils/contentLanguage.js';

// ---------------------------------------------------------------------------
// Configuration: how many puzzles per type per day
// ---------------------------------------------------------------------------

const PUZZLE_COUNTS = {
  // Large pool (>50K in DB) — 10 each
  anagram: 10,
  letterset: 10,
  uniqueobject: 10,
  memorystory: 10,

  // Medium pool (3K–50K) — 5 each
  wordsnake: 5,
  imagepuzzle: 5,
  memoryretention: 5,
  wordsearch: 5,
  memorypreviouspair: 5,
  math: 5,

  // Small pool (<3K) — 3 each
  wordprefix: 3,
  antonyms: 3,
  trivia: 3,
  synonyms: 3,
  memorysequencing: 3,
  memoryprevioussingle: 3,
  realorai: 3,
  crossword: 3,
  sentencetransitions: 3,
  conversion: 3,

  // Types that may have 0 DB entries — generate via AI
  crypto: 3,
  numbersequence: 3,
  riddle: 3,
  qa: 3,
  storypuzzle: 3,
  mathestimation: 3,
  mathtipping: 3,
  mathcomparison: 3,
  percentages: 3,
  division: 3,
  average: 3,
  subtraction: 3,
  purchasing: 3,
  discounts: 3,
  memorysquares: 3,
  memorymatrixpath: 3,
  pinballdeflector: 3,
  imagequestion: 3,
  imagematch: 3,
  flowpuzzle: 3,
  progressiverevelation: 3,
  oddoneout: 3,
  connotationwords: 3,
  waldopuzzle: 3,
  find_differences: 3,
  find_object: 3,
  musicidentification: 3,
  dailycrossword: 3,
};

// Difficulties to cache
const DIFFICULTIES = ['easy', 'medium', 'hard'];

// Only types that actually have data in the DB — skip types with 0 entries
const DB_BACKED_TYPES = [
  'anagram', 'letterset', 'uniqueobject', 'memorystory',
  'wordsnake', 'imagepuzzle', 'memoryretention', 'wordsearch',
  'memorypreviouspair', 'math', 'wordprefix', 'antonyms',
  'trivia', 'synonyms', 'memorysequencing', 'memoryprevioussingle',
  'realorai', 'crossword', 'sentencetransitions', 'conversion',
];

// ---------------------------------------------------------------------------
// In-memory cache: Map<string, { puzzles: Array, generatedAt: Date }>
// Key format: `${type}_${difficulty}_${YYYY-MM-DD}` for English, `${type}_${difficulty}_${lang}_${YYYY-MM-DD}` for
// another language, so languages never share an entry. Only English pools are filled today (the puzzles table has no
// language); a request for another language falls back to the English entry and says so (see resolveLocalized).
// ---------------------------------------------------------------------------

const cache = new Map();

// Track per-request index for the legacy endpoint (per type+difficulty per day)
// Key: `${type}_${difficulty}_${date}`, Value: number (next index to serve)
const serveIndex = new Map();

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function todayUTC() {
  return new Date().toISOString().split('T')[0]; // YYYY-MM-DD
}

function cacheKey(type, difficulty, date, lang = DEFAULT_LANGUAGE) {
  return `${type}_${difficulty}${langCacheSuffix(lang)}_${date}`;
}

/**
 * Cache entry for the requested language, else the English one. Returns { key, cached, contentLanguage } so callers can
 * tell the client which language the text really is in (never silently label English text as another language).
 */
function resolveLocalized(type, difficulty, date, lang) {
  if (lang && lang !== DEFAULT_LANGUAGE) {
    const key = cacheKey(type, difficulty, date, lang);
    const cached = cache.get(key);
    if (cached && cached.puzzles.length > 0) return { key, cached, contentLanguage: lang };
  }
  const key = cacheKey(type, difficulty, date);
  return { key, cached: cache.get(key), contentLanguage: DEFAULT_LANGUAGE };
}

/** Test hook / future per-language pool loader: store a localized pool next to the English one. */
export function setLocalizedPool(type, difficulty, lang, puzzles, date = todayUTC()) {
  cache.set(cacheKey(type.toLowerCase(), (difficulty || 'easy').toLowerCase(), date, lang), { puzzles, generatedAt: new Date() });
}

/** Test hook: drop everything (module state). */
export function _resetDailyCacheForTests() {
  cache.clear();
  serveIndex.clear();
}

function getCountForType(type) {
  return PUZZLE_COUNTS[type] || 3;
}

/**
 * Format a raw Supabase puzzle row into the client-expected shape.
 */
function formatPuzzle(puzzle) {
  return {
    puzzleId: puzzle.puzzleid,
    puzzleType: puzzle.type,
    question: puzzle.question,
    answer: puzzle.answer,
    hint: puzzle.hint || '',
    difficulty: puzzle.difficulty,
    generatedBy: puzzle.source || '',
    validatedBy: '',
    timestamp: puzzle.timestamp,
    options: puzzle.options || [],
    correct_option: puzzle.correct_option || puzzle.answer,
  };
}

// ---------------------------------------------------------------------------
// Core: fetch or generate puzzles for one type+difficulty
// ---------------------------------------------------------------------------

async function fetchPuzzlesFromDB(type, difficulty, count) {
  try {
    // Use a date-seeded random offset so every day is different
    // but the same date always produces the same set.
    const dateStr = todayUTC();
    const seed = hashDateSeed(dateStr, type, difficulty);

    // First, find total count for this type+difficulty
    const { count: totalCount, error: countError } = await supabase
      .from('puzzles')
      .select('*', { count: 'exact', head: true })
      .eq('type', type.toLowerCase())
      .eq('difficulty', difficulty);

    if (countError || !totalCount || totalCount === 0) {
      return [];
    }

    // Pick a deterministic offset based on the date seed
    const offset = seed % Math.max(1, totalCount - count);

    const { data: puzzles, error } = await supabase
      .from('puzzles')
      .select('*')
      .eq('type', type.toLowerCase())
      .eq('difficulty', difficulty)
      .order('timestamp', { ascending: true })
      .range(offset, offset + count - 1);

    if (error || !puzzles) {
      return [];
    }

    return puzzles.map(formatPuzzle);
  } catch (err) {
    console.error(`[DailyPuzzles] DB fetch error for ${type}/${difficulty}:`, err.message);
    return [];
  }
}

/**
 * Simple deterministic hash from date+type+difficulty to a positive integer.
 */
function hashDateSeed(dateStr, type, difficulty) {
  const str = `${dateStr}_${type}_${difficulty}`;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

/**
 * Try to generate puzzles using PuzzleGenerator (AI-backed).
 * Returns formatted puzzles or empty array on failure.
 */
async function generatePuzzlesViaAI(type, difficulty, count) {
  const generator = new PuzzleGenerator();
  const puzzles = [];

  for (let i = 0; i < count; i++) {
    try {
      const result = await generator.generatePuzzle(type, 'gpt-4o-mini', difficulty);
      if (result && result.success) {
        // The generator may return a single puzzle or batch; normalise
        if (result.puzzleIds && result.puzzleIds.length > 0) {
          // Puzzles were stored in DB — fetch them
          const { data } = await supabase
            .from('puzzles')
            .select('*')
            .in('puzzleid', result.puzzleIds);
          if (data) {
            puzzles.push(...data.map(formatPuzzle));
          }
          // Got enough from one batch call
          if (puzzles.length >= count) break;
        } else if (result.puzzle) {
          puzzles.push(formatPuzzle(result.puzzle));
        }
      }
    } catch (err) {
      console.warn(`[DailyPuzzles] AI generation failed for ${type}/${difficulty} (attempt ${i + 1}):`, err.message);
    }
  }

  return puzzles.slice(0, count);
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Refresh daily puzzles for ALL types and difficulties.
 * Called on startup and by the daily cron.
 */
export async function refreshDailyPuzzles() {
  const date = todayUTC();
  console.log(`[DailyPuzzles] Refreshing puzzles for ${date}...`);
  const startTime = Date.now();
  let successCount = 0;
  let failCount = 0;

  // Clear old dates from cache to free memory
  for (const key of cache.keys()) {
    if (!key.endsWith(`_${date}`)) {
      cache.delete(key);
    }
  }
  // Also clear stale serve indices
  for (const key of serveIndex.keys()) {
    if (!key.endsWith(`_${date}`)) {
      serveIndex.delete(key);
    }
  }

  // Process in parallel batches of 5 for speed
  const tasks = [];
  for (const type of DB_BACKED_TYPES) {
    for (const difficulty of DIFFICULTIES) {
      const key = cacheKey(type, difficulty, date);
      if (cache.has(key) && cache.get(key).puzzles.length > 0) {
        successCount++;
        continue;
      }
      tasks.push({ type, difficulty, key, count: getCountForType(type) });
    }
  }

  const BATCH_SIZE = 5;
  for (let i = 0; i < tasks.length; i += BATCH_SIZE) {
    const batch = tasks.slice(i, i + BATCH_SIZE);
    const results = await Promise.allSettled(
      batch.map(async ({ type, difficulty, key, count }) => {
        const puzzles = await fetchPuzzlesFromDB(type, difficulty, count);
        if (puzzles.length > 0) {
          cache.set(key, { puzzles, generatedAt: new Date() });
          return { type, difficulty, count: puzzles.length };
        }
        return null;
      })
    );

    for (const r of results) {
      if (r.status === 'fulfilled' && r.value) {
        successCount++;
      } else {
        failCount++;
      }
    }
  }

  const duration = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`[DailyPuzzles] Refresh complete: ${successCount} cached, ${failCount} failed in ${duration}s`);

  return { success: true, cached: successCount, failed: failCount, duration };
}

/**
 * Get daily puzzles for given types + difficulty + date.
 *
 * @param {string[]} types   - Array of puzzle type strings (e.g. ['anagram','trivia'])
 * @param {string}   difficulty - 'easy' | 'medium' | 'hard'
 * @param {string}   [date]  - YYYY-MM-DD, defaults to today UTC
 * @returns {{ date: string, puzzles: Record<string, Array> }}
 */
export function getDailyPuzzles(types, difficulty, date, lang = DEFAULT_LANGUAGE) {
  const d = date || todayUTC();
  const normalizedDiff = difficulty ? difficulty.toLowerCase() : 'easy';
  const result = {};
  const contentLanguage = {};

  for (const type of types) {
    const { cached, contentLanguage: cl } = resolveLocalized(type.toLowerCase(), normalizedDiff, d, lang);
    result[type] = cached ? cached.puzzles : [];
    contentLanguage[type] = cl;
  }

  return { date: d, language: lang || DEFAULT_LANGUAGE, puzzles: result, contentLanguage };
}

/**
 * Get the next unserved puzzle from today's cache for a given type+difficulty.
 * Used by the legacy /fetch-next-puzzle-ios endpoint.
 *
 * @param {string} type
 * @param {string} difficulty
 * @returns {object|null} Formatted puzzle or null if none available
 */
export function getNextDailyPuzzle(type, difficulty, lang = DEFAULT_LANGUAGE) {
  const d = todayUTC();
  const normalizedDiff = difficulty ? difficulty.toLowerCase() : 'easy';
  const { key, cached } = resolveLocalized(type.toLowerCase(), normalizedDiff, d, lang);

  if (!cached || cached.puzzles.length === 0) {
    return null;
  }

  const idxKey = key;
  const currentIdx = serveIndex.get(idxKey) || 0;

  // Wrap around when all puzzles have been served
  const idx = currentIdx % cached.puzzles.length;
  serveIndex.set(idxKey, currentIdx + 1);

  return cached.puzzles[idx];
}

/**
 * Check whether the cache has puzzles for a given type+difficulty today.
 */
export function hasDailyPuzzles(type, difficulty, lang = DEFAULT_LANGUAGE) {
  const { cached } = resolveLocalized(type.toLowerCase(), (difficulty || 'easy').toLowerCase(), todayUTC(), lang);
  return !!(cached && cached.puzzles.length > 0);
}

/** Language the next daily puzzle of this type is really in ('en' when only the English pool exists). */
export function dailyContentLanguage(type, difficulty, lang = DEFAULT_LANGUAGE) {
  return resolveLocalized(type.toLowerCase(), (difficulty || 'easy').toLowerCase(), todayUTC(), lang).contentLanguage;
}

/**
 * Get cache statistics for monitoring.
 */
export function getDailyCacheStats() {
  const date = todayUTC();
  const stats = {
    date,
    totalEntries: cache.size,
    todayEntries: 0,
    typeCounts: {},
  };

  for (const [key, value] of cache) {
    if (key.endsWith(`_${date}`)) {
      stats.todayEntries++;
      if (/_(?:easy|medium|hard)_[a-z]{2}_\d{4}-\d{2}-\d{2}$/.test(key)) continue; // localized pool: counted above, not per type
      const parts = key.replace(`_${date}`, '').split('_');
      const type = parts.slice(0, -1).join('_');
      const diff = parts[parts.length - 1];
      if (!stats.typeCounts[type]) stats.typeCounts[type] = {};
      stats.typeCounts[type][diff] = value.puzzles.length;
    }
  }

  return stats;
}
