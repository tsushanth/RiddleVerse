// Image share card, drawn on a canvas (port of Android DailyShareCard.kt). It is built only from the spoiler-free
// result (number, game name, time, tries, streak): the puzzle, its seed and its solution are never passed in,
// so the image cannot leak the answer. No earnings, coins or rewards are mentioned anywhere on it.
import { formatTime } from './dailyShare.js'

export const CARD_SIZE = 1080
const VIOLET = '#6C4CF1'
const NIGHT = '#1E1B3A'
const SUN = '#FFB020'
const MINT = '#2DC98E'
const CORAL = '#FF6B6B'
const FLAME = '#FF8C00'
const WHITE = '#FFFFFF'
const SOFT = 'rgba(255,255,255,0.86)'
const SOFTER = 'rgba(255,255,255,0.78)'

function pill(ctx, x, y, w, h, r, color) {
  ctx.fillStyle = color
  ctx.beginPath()
  if (typeof ctx.roundRect === 'function') ctx.roundRect(x, y, w, h, r)
  else ctx.fillRect(x, y, w, h)
  ctx.fill()
}

/** Draws the card onto any 2D context (real canvas or a test double). */
export function drawShareCard(ctx, r) {
  const w = CARD_SIZE
  const g = ctx.createLinearGradient(0, 0, w, w)
  g.addColorStop(0, VIOLET); g.addColorStop(1, NIGHT)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, w)
  pill(ctx, 72, 72, w - 144, w - 144, 64, 'rgba(255,255,255,0.18)')

  ctx.textAlign = 'center'
  const text = (s, size, color, y, bold = true) => {
    ctx.fillStyle = color
    ctx.font = `${bold ? '700' : '400'} ${size}px Inter, system-ui, -apple-system, "Segoe UI", sans-serif`
    ctx.fillText(s, w / 2, y)
  }
  text('RiddleVerse', 56, SUN, 190)
  text(`Daily #${r.number}`, 112, WHITE, 320)
  text('SOLVED', 64, MINT, 410)
  text(formatTime(r.seconds), 220, WHITE, 640)
  text(r.gameName, 52, SOFT, 725, false)

  // Tries: one rounded square per try, wrong tries coral, the solving try mint.
  const n = Math.min(6, Math.max(1, Math.floor(Number(r.attempts) || 1)))
  const box = 84; const gap = 20
  let x = (w - (n * box + (n - 1) * gap)) / 2
  for (let i = 0; i < n; i++) {
    pill(ctx, x, 775, box, box, 18, i === n - 1 ? MINT : CORAL)
    x += box + gap
  }

  if (r.streak > 0) text(`\u{1F525} ${r.streak} day streak`, 64, FLAME, 940)
  text("Play today's puzzle on Google Play", 40, SOFTER, 1005, false)
}

/** Browser only: renders the card to a PNG Blob (null if canvas is unavailable). */
export function renderShareCardBlob(r) {
  if (typeof document === 'undefined') return Promise.resolve(null)
  const canvas = document.createElement('canvas')
  canvas.width = CARD_SIZE; canvas.height = CARD_SIZE
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.resolve(null)
  drawShareCard(ctx, r)
  return new Promise((resolve) => canvas.toBlob((b) => resolve(b), 'image/png'))
}
