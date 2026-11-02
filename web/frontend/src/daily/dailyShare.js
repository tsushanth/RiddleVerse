// Spoiler-free share text: a port of Android DailyShareText.kt. It only ever receives the number, game name,
// time, tries and streak, never the puzzle, the seed or a solution.

/** Web campaign link: same destination as Android, tagged utm_medium=web_daily so web shares are countable. */
export const PLAY_URL =
  'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse' +
  '&utm_source=share&utm_medium=web_daily&utm_campaign=card'

const MAX_SQUARES = 6
const RED = '\u{1F7E5}'
const GREEN = '\u{1F7E9}'
const CHECK = '✅'
const FLAME = '\u{1F525}'

/** 1:42, 0:07, 1:05:09. Negative or non-finite input reads 0:00. */
export function formatTime(seconds) {
  const s = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const two = (n) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${two(m)}:${two(sec)}` : `${m}:${two(sec)}`
}

/** One square per try, wrong tries red and the solving try green. Long runs collapse to "(red x5)...green". */
export function attemptGrid(attempts) {
  const n = Math.max(1, Math.floor(Number(attempts) || 1))
  return n <= MAX_SQUARES ? RED.repeat(n - 1) + GREEN : RED.repeat(MAX_SQUARES - 1) + '…' + GREEN
}

/** "RiddleVerse Daily #123 (check) 1:42 (flame)5" */
export function headline(r) {
  const flame = r.streak > 0 ? ` ${FLAME}${r.streak}` : ''
  return `RiddleVerse Daily #${r.number} ${CHECK} ${formatTime(r.seconds)}${flame}`
}

/** Full text for the share sheet: headline, game plus tries grid, then the Play link. */
export function buildShareText(r) {
  return `${headline(r)}\n${r.gameName} ${attemptGrid(r.attempts)}\n${PLAY_URL}`
}
