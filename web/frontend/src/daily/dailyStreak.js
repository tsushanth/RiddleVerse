// Solve-based streak: a port of Android DailyStreakRules.kt plus the localStorage layer. Days are UTC epoch days
// (see dailySchedule.js), never local dates, so changing time zone cannot add or remove a day.
// Opening the page or failing a puzzle never reaches applySolve, so neither can extend a streak.
// (Android's one-time "days you opened the app" migration does not apply: the web never had that streak.)

export const EMPTY_STREAK = Object.freeze({ current: 0, best: 0, lastSolveDay: null, totalSolved: 0 })

/** Streak to show on `today`: alive through the day after the last solve, then 0. */
export function display(s, today) {
  if (s.lastSolveDay == null) return 0
  return s.lastSolveDay >= today - 1 ? s.current : 0
}

export function solvedOn(s, day) {
  return s.lastSolveDay != null && s.lastSolveDay >= day
}

/**
 * Records a solve of the puzzle for `day`.
 *  - same day twice, or a day at or before the last solve (clock moved back): no change, counted false
 *  - the day after the last solve: streak + 1
 *  - anything else (first ever solve, or a missed day): restarts at 1
 */
export function applySolve(s, day) {
  const last = s.lastSolveDay
  if (last != null && day <= last) return { state: s, streak: s.current, counted: false }
  const next = last != null && last === day - 1 ? s.current + 1 : 1
  const state = { current: next, best: Math.max(s.best, next), lastSolveDay: day, totalSolved: s.totalSolved + 1 }
  return { state, streak: next, counted: true }
}

// ---- persistence ----
const KEY = 'rv_daily_v1'
const isCount = (n) => Number.isInteger(n) && n >= 0
const isDay = (n) => Number.isInteger(n)

/** What is stored: the streak, today's finished result (so a reload shows the completion screen) and failed tries. */
export function emptyDailyState() {
  return { streak: { ...EMPTY_STREAK }, result: null, tries: null }
}

function validStreak(s) {
  return s && typeof s === 'object' && isCount(s.current) && isCount(s.best) && isCount(s.totalSolved)
    && (s.lastSolveDay === null || isDay(s.lastSolveDay))
}
function validResult(r) {
  return r && typeof r === 'object' && isDay(r.day) && isCount(r.seconds) && Number.isInteger(r.attempts) && r.attempts >= 1
}
function validTries(t) {
  return t && typeof t === 'object' && isDay(t.day) && isCount(t.failed)
}

/** Never throws; any missing, corrupt or wrong-shaped value reads as an empty state. */
export function loadDailyState(storage) {
  try {
    const raw = storage && storage.getItem(KEY)
    if (!raw) return emptyDailyState()
    const o = JSON.parse(raw)
    if (!o || typeof o !== 'object' || Array.isArray(o) || !validStreak(o.streak)) return emptyDailyState()
    const s = o.streak
    return {
      streak: { current: s.current, best: s.best, lastSolveDay: s.lastSolveDay, totalSolved: s.totalSolved },
      result: validResult(o.result) ? { day: o.result.day, seconds: o.result.seconds, attempts: o.result.attempts } : null,
      tries: validTries(o.tries) ? { day: o.tries.day, failed: o.tries.failed } : null,
    }
  } catch {
    return emptyDailyState()
  }
}

export function saveDailyState(storage, state) {
  try {
    storage.setItem(KEY, JSON.stringify(state))
  } catch { /* private mode / quota: the streak just will not persist */ }
}
