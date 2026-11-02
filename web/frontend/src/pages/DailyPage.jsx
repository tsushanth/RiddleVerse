// web/daily-page: public, no-login Daily puzzle at /play/daily. Web parity for the Android Daily (see src/daily/*).
// Today's game and number come from the UTC-day schedule; the board is generated in the browser from a
// deterministic per-day seed by the same /play web ports. Streak and result live in localStorage only.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Flame, Share2, Download, Timer } from 'lucide-react'
import PlayShell, { PlayStoreButton } from '../components/play/PlayShell'
import PuzzleRenderer from '../components/puzzles/PuzzleRenderer'
import { getPlayGame } from '../utils/playCatalog'
import { applyPageMeta } from '../utils/pageMeta'
import { trackEvent } from '../services/firebase'
import { dayOf, specFor, numberOf, millisUntilNext, webSeedFor } from '../daily/dailySchedule.js'
import { applySolve, display, solvedOn, loadDailyState, saveDailyState } from '../daily/dailyStreak.js'
import { PLAY_URL, formatTime, attemptGrid, buildShareText } from '../daily/dailyShare.js'
import { drawShareCard, renderShareCardBlob, CARD_SIZE } from '../daily/dailyShareCard.js'

const TITLE = 'Daily Puzzle: One Free Puzzle a Day, No Sign-Up | RiddleVerse'
const DESCRIPTION = 'A new free puzzle every day, the same for everyone. Solve it in your browser with no account, keep a streak and share your time without spoilers.'

const storage = () => { try { return window.localStorage } catch { return null } }

/** Counts time only while the tab is visible and `running` is true. Returns a getter in milliseconds. */
function useActiveClock(running) {
  const total = useRef(0)
  const last = useRef(null)
  useEffect(() => {
    if (!running) return undefined
    last.current = Date.now()
    const id = setInterval(() => {
      const now = Date.now()
      if (!document.hidden && last.current != null) total.current += now - last.current
      last.current = now
    }, 250)
    return () => {
      if (last.current != null && !document.hidden) total.current += Date.now() - last.current
      clearInterval(id)
      last.current = null
    }
  }, [running])
  return useCallback(() => {
    const live = running && last.current != null && !document.hidden ? Date.now() - last.current : 0
    return total.current + live
  }, [running])
}

function useNow() {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])
  return now
}

function Countdown({ now }) {
  const ms = millisUntilNext(now)
  const s = Math.ceil(ms / 1000)
  const hh = String(Math.floor(s / 3600)).padStart(2, '0')
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return (
    <p className="flex items-center justify-center gap-2 text-white/90" role="timer" aria-label="Time until the next daily puzzle">
      <Timer size={18} aria-hidden="true" /> Next daily in <span className="font-mono font-bold">{hh}:{mm}:{ss}</span>
    </p>
  )
}

function ShareCardPreview({ result }) {
  const ref = useRef(null)
  useEffect(() => {
    const c = ref.current
    const ctx = c && c.getContext && c.getContext('2d')
    if (ctx) drawShareCard(ctx, result)
  }, [result])
  return (
    <canvas
      ref={ref}
      width={CARD_SIZE}
      height={CARD_SIZE}
      role="img"
      aria-label={`Share card: Daily ${result.number}, ${result.gameName}, solved in ${formatTime(result.seconds)}`}
      className="w-full max-w-xs mx-auto rounded-2xl"
    />
  )
}

function Complete({ spec, game, saved, streak, now }) {
  const [status, setStatus] = useState('')
  const result = useMemo(
    () => ({ number: spec.number, gameName: game.name, seconds: saved.seconds, attempts: saved.attempts, streak }),
    [spec.number, game.name, saved.seconds, saved.attempts, streak]
  )
  const text = buildShareText(result)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setStatus('Result copied. Paste it anywhere.')
    } catch {
      setStatus('Could not copy automatically. Select the text above and copy it.')
    }
  }

  const share = async () => {
    trackEvent('daily_share', { puzzle_type: game.slug, number: spec.number })
    if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
      try {
        const blob = await renderShareCardBlob(result)
        const file = blob && typeof File !== 'undefined' ? new File([blob], `riddleverse-daily-${spec.number}.png`, { type: 'image/png' }) : null
        const withFile = file && navigator.canShare && navigator.canShare({ files: [file] })
        await navigator.share(withFile ? { text, files: [file] } : { text })
        return
      } catch (e) {
        if (e && e.name === 'AbortError') return // the player closed the share sheet
        // any other failure: fall through to copy
      }
    }
    await copy()
  }

  const saveImage = async () => {
    const blob = await renderShareCardBlob(result)
    if (!blob) return setStatus('Could not make the image on this browser. Use Share instead.')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `riddleverse-daily-${spec.number}.png`
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  return (
    <div className="space-y-4" data-testid="daily-complete">
      <div className="glass rounded-2xl p-5 text-center space-y-2">
        <p className="text-sm text-white/70">Daily #{spec.number} | {game.name}</p>
        <h2 className="text-2xl font-extrabold">Solved!</h2>
        <p className="text-5xl font-extrabold font-mono">{formatTime(saved.seconds)}</p>
        <p className="text-white/90">
          {saved.attempts === 1 ? 'First try' : `${saved.attempts} tries`} <span aria-hidden="true">{attemptGrid(saved.attempts)}</span>
        </p>
        {streak > 0 && (
          <p className="inline-flex items-center gap-1.5 font-bold text-orange-300"><Flame size={20} aria-hidden="true" /> {streak} day streak</p>
        )}
      </div>

      <ShareCardPreview result={result} />

      <pre className="glass rounded-xl p-3 text-sm whitespace-pre-wrap break-words select-all" aria-label="Share text">{text}</pre>

      <div className="flex flex-wrap gap-3 justify-center">
        <button type="button" onClick={share} className="inline-flex items-center gap-2 bg-white text-purple-800 px-5 min-h-[48px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
          <Share2 size={18} aria-hidden="true" /> Share result
        </button>
        <button type="button" onClick={saveImage} className="inline-flex items-center gap-2 bg-white/15 hover:bg-white/25 px-5 min-h-[48px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
          <Download size={18} aria-hidden="true" /> Save card image
        </button>
        <button type="button" onClick={copy} className="inline-flex items-center gap-2 bg-white/15 hover:bg-white/25 px-5 min-h-[48px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
          Copy text
        </button>
      </div>
      <p role="status" aria-live="polite" className="text-center text-sm text-white/80 min-h-[1.25rem]">{status}</p>

      <Countdown now={now} />
      <div className="flex justify-center"><PlayStoreButton campaign="daily" /></div>
    </div>
  )
}

function DailyGame({ spec, game, onSolved, streak }) {
  const [failed, setFailed] = useState(0)
  const [phase, setPhase] = useState('playing') // 'playing' | 'failed'
  const elapsed = useActiveClock(phase === 'playing')

  const puzzle = useMemo(
    () => ({
      // Same id every try so the board is identical on retry (word games seed from the id, grid/spatial from `question`).
      puzzleId: `daily-${spec.number}-${spec.type}`,
      puzzleType: spec.type,
      question: JSON.stringify({ game: spec.type, seed: webSeedFor(spec), difficulty: 'Medium' }),
      answer: '',
      hint: '',
      options: [],
      timer: '1:30',
      difficulty: 2, // Medium, as on Android
      daily: true, // grid games: fixed board, no re-roll, no answer reveal on a failed try
    }),
    [spec]
  )

  const onAnswer = useCallback(
    (ok) => {
      if (ok) onSolved({ seconds: Math.round(elapsed() / 1000), attempts: failed + 1 })
      else {
        setFailed((f) => f + 1)
        setPhase('failed')
      }
    },
    [elapsed, failed, onSolved]
  )

  return (
    <div className="space-y-4">
      {streak > 0 && (
        <p className="inline-flex items-center gap-1.5 font-semibold text-orange-300"><Flame size={18} aria-hidden="true" /> {streak} day streak. Solve today to keep it.</p>
      )}
      {phase === 'failed' ? (
        <div className="glass rounded-2xl p-5 text-center space-y-3" role="status">
          <p className="font-bold text-lg">Not this time</p>
          <p className="text-white/90">It is the same puzzle, so you can try again. Only a solve counts toward your streak.</p>
          <button type="button" onClick={() => setPhase('playing')} className="bg-white text-purple-800 px-6 min-h-[48px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
            Try again
          </button>
        </div>
      ) : (
        <div className="glass rounded-2xl p-4 sm:p-6">
          <PuzzleRenderer key={`${puzzle.puzzleId}-${failed}`} puzzleType={spec.type} puzzle={puzzle} onAnswer={onAnswer} />
        </div>
      )}
    </div>
  )
}

export default function DailyPage() {
  const now = useNow()
  const [day, setDay] = useState(() => dayOf(Date.now()))
  const [state, setState] = useState(() => loadDailyState(storage()))
  const spec = useMemo(() => specFor(day), [day])
  const game = getPlayGame(spec.type)

  useEffect(() => {
    applyPageMeta({ title: TITLE, description: DESCRIPTION, path: '/play/daily' })
  }, [])

  const solved = solvedOn(state.streak, day)
  const saved = state.result && state.result.day === day ? state.result : null
  const streakNow = display(state.streak, day)
  const newDayReady = dayOf(now) !== day

  const onSolved = useCallback(
    ({ seconds, attempts }) => {
      setState((prev) => {
        const out = applySolve(prev.streak, day)
        if (!out.counted) return prev // same UTC day twice: nothing changes
        const next = { streak: out.state, result: { day, seconds, attempts }, tries: null }
        saveDailyState(storage(), next)
        return next
      })
      trackEvent('daily_solved', { puzzle_type: spec.type, number: spec.number })
    },
    [day, spec]
  )

  if (!game) return null // unreachable: every rotation slug is in the catalog (covered by a test)

  return (
    <PlayShell>
      <nav aria-label="Breadcrumb" className="text-sm text-white/70">
        <Link to="/play" className="hover:underline">Play</Link> <span aria-hidden="true">/</span> Daily
      </nav>
      <h1 className="text-3xl font-extrabold">Daily puzzle #{numberOf(day)}</h1>
      <p className="text-white/90">
        Today is <strong>{game.name}</strong>. Everyone gets the same game each day, and it changes at midnight UTC.
        Solve it to extend your streak. No account needed.
      </p>

      {newDayReady && (
        <div className="glass rounded-xl p-4 flex flex-wrap items-center justify-between gap-3" role="status">
          <span>A new daily puzzle is ready.</span>
          <button type="button" onClick={() => setDay(dayOf(Date.now()))} className="bg-white text-purple-800 px-5 min-h-[44px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
            Play Daily #{numberOf(dayOf(now))}
          </button>
        </div>
      )}

      {solved && saved ? (
        <Complete spec={spec} game={game} saved={saved} streak={streakNow} now={now} />
      ) : solved ? (
        <div className="glass rounded-2xl p-5 text-center space-y-3">
          <p className="font-bold text-lg">You already solved today's puzzle.</p>
          <Countdown now={now} />
        </div>
      ) : (
        <DailyGame key={day} spec={spec} game={game} onSolved={onSolved} streak={streakNow} />
      )}

      <section className="glass rounded-2xl p-5 space-y-2">
        <h2 className="text-xl font-bold">How the daily works</h2>
        <ul className="list-disc pl-6 space-y-1 text-white/90">
          <li>One puzzle a day, the same game for everyone, switching at midnight UTC.</li>
          <li>Solving it keeps your streak alive. Missing a day resets it. Your streak is saved only in this browser.</li>
          <li>Sharing shows the game, your time and your tries, never the answer.</li>
          <li>Boards on the web and on Android are generated separately, so only the game and the number match.</li>
        </ul>
        <p className="text-sm text-white/70">Share link: <span className="break-all">{PLAY_URL}</span></p>
      </section>
    </PlayShell>
  )
}
