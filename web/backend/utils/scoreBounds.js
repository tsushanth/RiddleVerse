// Bounds for a single POST /update-score delta.
// The Android client derives one completion's points from the session (10..1000, see CompletionScoring.kt) and older
// Android builds sent up to 1000. Some older clients (web coin sync, legacy iOS code) post a running total instead of a
// delta, so a value above the cap is clamped rather than rejected: those clients keep working, but one request can never
// add more than MAX_SCORE_DELTA. Anything that is not a finite number of at least 1 is not a score and is rejected.
export const MIN_SCORE_DELTA = 1;
export const MAX_SCORE_DELTA = 1000;

// Returns the delta to add (an integer in 1..1000), or null when the request should be rejected with 400.
export function clampScoreDelta(score) {
    if (typeof score !== 'number' || !Number.isFinite(score)) return null;
    const whole = Math.floor(score);
    if (whole < MIN_SCORE_DELTA) return null;
    return Math.min(whole, MAX_SCORE_DELTA);
}
