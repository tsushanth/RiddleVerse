// Bounds for a single POST /update-score delta. The Android client derives one completion's points from the session
// (10..1000, see CompletionScoring.kt); older clients sent up to 1000. Anything outside that is not a real completion.
export const MIN_SCORE_DELTA = 1;
export const MAX_SCORE_DELTA = 1000;

export function isValidScoreDelta(score) {
    return typeof score === 'number' && Number.isInteger(score) && score >= MIN_SCORE_DELTA && score <= MAX_SCORE_DELTA;
}
