// --- web/game-riddles --- the riddle card: typed answer, free 3-step hint ladder (costs stars, not points), per-device
// streak, skip, reveal and share. Public and no-login; everything runs in the browser and works offline once loaded.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { loadBank, TIER_LABELS } from './riddleBank'
import { createDeck, createLocalStore } from './riddleDeck'
import { isCorrect } from './riddleMatcher'
import { MAX_STARS, loadStats, points, recordResult, shareText, stars as starsFor, tierFromPuzzle } from './riddlePack'
import { trackPuzzleEvent } from '../../../utils/puzzleEvents'

const MAX_INPUT = 60
const btn =
  'min-h-[44px] px-4 rounded-xl bg-white/15 hover:bg-white/25 active:bg-white/30 text-white font-semibold ' +
  'disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus-visible:ring-2 focus-visible:ring-white transition-colors'

export default function RiddlePuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const store = useMemo(() => createLocalStore(), [])
  const deck = useMemo(() => createDeck(store), [store])
  const [bank, setBank] = useState(null)
  const [loadError, setLoadError] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let alive = true
    setLoadError(false)
    loadBank().then((b) => { if (alive) setBank(b) }).catch(() => {
      if (alive) setLoadError(true)
      trackPuzzleEvent('error', 'riddles', { errorMessage: 'riddle bank failed to load' })
    })
    return () => { alive = false }
  }, [attempt])

  if (loadError) {
    return (
      <div role="alert" className="text-center space-y-3 py-6">
        <p className="text-white font-semibold">The riddles could not be loaded. Check your connection and try again.</p>
        <button type="button" className={btn} onClick={() => setAttempt((a) => a + 1)}>Try again</button>
      </div>
    )
  }
  if (!bank) return <p className="text-white/70 text-center py-10">Loading riddles...</p>
  return <RiddleCard bank={bank} deck={deck} store={store} tier={tier} onAnswer={onAnswer} />
}

function RiddleCard({ bank, deck, store, tier, onAnswer }) {
  const [riddle, setRiddle] = useState(() => deck.next(tier, bank))
  const [input, setInput] = useState('')
  const [hintsShown, setHintsShown] = useState(0)
  const [outcome, setOutcome] = useState('playing') // playing | solved | revealed
  const [feedback, setFeedback] = useState('')
  const [confirm, setConfirm] = useState(null) // null | 'skip' | 'reveal'
  const [stats, setStats] = useState(() => loadStats(store))
  const [copied, setCopied] = useState('')
  const inputRef = useRef(null)
  const sessionId = useRef('')
  const t0 = useRef(0)
  const finished = outcome !== 'playing'
  const solved = outcome === 'solved'
  const stars = starsFor(hintsShown)

  // One telemetry session per riddle shown. Deferred so StrictMode's double mount does not emit a phantom start.
  useEffect(() => {
    sessionId.current = `riddles-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
    t0.current = Date.now()
    const t = setTimeout(() => trackPuzzleEvent('start', 'riddles', { difficulty: tier, sessionId: sessionId.current }), 0)
    return () => clearTimeout(t)
  }, [riddle, tier])

  const finish = useCallback((didSolve, hints, how) => {
    setStats(recordResult(store, didSolve))
    trackPuzzleEvent(didSolve ? 'complete' : 'fail', 'riddles', {
      difficulty: tier, sessionId: sessionId.current, durationMs: Date.now() - t0.current, hintsUsed: hints, score: points(tier, hints, didSolve),
    })
    onAnswer?.(didSolve, how, !didSolve)
  }, [store, tier, onAnswer])

  const nextRiddle = useCallback(() => {
    setRiddle(deck.next(tier, bank))
    setInput(''); setHintsShown(0); setOutcome('playing'); setFeedback(''); setConfirm(null); setCopied('')
    setTimeout(() => inputRef.current?.focus(), 0)
  }, [deck, tier, bank])

  function check(e) {
    e?.preventDefault()
    const guess = input.trim()
    if (!guess) { setFeedback('Type an answer first.'); return }
    if (isCorrect(guess, riddle)) {
      setFeedback('')
      setOutcome('solved')
      finish(true, hintsShown, guess)
    } else {
      setFeedback('Not quite. Try again or take a hint.')
    }
  }

  function showHint() {
    if (hintsShown >= riddle.hints.length) return
    setHintsShown(hintsShown + 1)
    trackPuzzleEvent('hint', 'riddles', { difficulty: tier, sessionId: sessionId.current })
  }

  function skip() {
    setConfirm(null)
    finish(false, hintsShown, 'skipped')
    nextRiddle()
  }

  function reveal() {
    setConfirm(null)
    setOutcome('revealed')
    finish(false, hintsShown, riddle.answer)
  }

  async function share() {
    const text = shareText(riddle)
    try {
      if (navigator.share) { await navigator.share({ text }); return }
    } catch (e) {
      if (e?.name === 'AbortError') return
    }
    try {
      await navigator.clipboard.writeText(text)
      setCopied('Copied. Paste it anywhere to share.')
    } catch {
      setCopied('Could not copy automatically. Select and copy this text:\n' + text)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="font-semibold text-white/70">{TIER_LABELS[riddle.tier]}</span>
        <span className="text-xl text-yellow-300" role="img" aria-label={`${stars} of ${MAX_STARS} stars`}>
          {'★'.repeat(stars) + '☆'.repeat(MAX_STARS - stars)}
        </span>
        <span className="font-semibold text-white/70" aria-label={`Correct answers in a row: ${stats.streak}`}>Streak: {stats.streak}</span>
      </div>

      <h2 className="text-xl leading-7 font-medium text-white bg-black/20 rounded-2xl p-4">{riddle.question}</h2>

      {riddle.hints.slice(0, hintsShown).map((h, i) => (
        <p key={i} role="status" className="rounded-xl bg-white/10 p-3 text-white/90">Hint {i + 1}: {h}</p>
      ))}

      {!finished && (
        <>
          <form onSubmit={check} className="flex gap-2 items-end">
            <label className="flex-1 text-sm text-white/70">
              Your answer
              <input
                ref={inputRef} type="text" value={input} maxLength={MAX_INPUT} autoComplete="off" autoCapitalize="none" spellCheck="false"
                enterKeyHint="done" onChange={(e) => { setInput(e.target.value.slice(0, MAX_INPUT)); setFeedback('') }}
                className="mt-1 w-full min-h-[48px] rounded-xl bg-white/90 text-gray-900 px-3 text-base focus:outline-none focus-visible:ring-4 focus-visible:ring-yellow-300"
              />
            </label>
            <button type="submit" className={`${btn} !bg-purple-500 hover:!bg-purple-600 min-h-[48px]`}>Check</button>
          </form>
          <p role="status" aria-live="polite" className="min-h-[1.5rem] text-sm text-yellow-200">{feedback}</p>

          <div className="grid grid-cols-3 gap-2">
            <button type="button" className={btn} disabled={hintsShown >= riddle.hints.length} onClick={showHint}
              aria-label={hintsShown < riddle.hints.length ? `Show hint ${hintsShown + 1} of 3. Each hint takes one star off your score.` : 'All hints are shown'}>
              {hintsShown < riddle.hints.length ? `Hint ${hintsShown + 1} of 3` : 'No more hints'}
            </button>
            <button type="button" className={btn} onClick={() => (stats.streak > 0 ? setConfirm('skip') : skip())}>Skip</button>
            <button type="button" className={btn} onClick={() => setConfirm('reveal')}>Reveal</button>
          </div>

          {confirm && (
            <div role="alertdialog" aria-label={confirm === 'reveal' ? 'Reveal the answer?' : 'Skip this riddle?'} className="rounded-xl bg-black/30 p-3 space-y-2">
              <p className="font-semibold text-white">{confirm === 'reveal' ? 'Reveal the answer?' : 'Skip this riddle?'}</p>
              <p className="text-sm text-white/80">
                {confirm === 'reveal' ? 'This riddle will count as unsolved and your streak will reset.' : 'Your streak will reset.'}
              </p>
              <div className="flex gap-2">
                <button type="button" className={`${btn} flex-1`} onClick={() => setConfirm(null)}>Cancel</button>
                <button type="button" className={`${btn} flex-1 !bg-purple-500 hover:!bg-purple-600`} onClick={confirm === 'reveal' ? reveal : skip}>Yes</button>
              </div>
            </div>
          )}

          <button type="button" className={`${btn} w-full`} onClick={share}>Share riddle</button>
        </>
      )}

      {finished && (
        <div className="rounded-2xl bg-black/25 p-4 space-y-2" role="status">
          <h3 className={`text-xl font-bold ${solved ? 'text-green-400' : 'text-yellow-200'}`}>
            {solved ? `Correct! The answer is ${riddle.displayAnswer}.` : `The answer is ${riddle.displayAnswer}.`}
          </h3>
          <p className="text-white/90">{riddle.explanation}</p>
          {solved && <p className="text-white/90">Stars earned: {stars} of {MAX_STARS} (hints used: {hintsShown})</p>}
          <p className="text-white/70 text-sm">
            Streak: {stats.streak} | Best: {stats.best} | Solved: {stats.solved} of {stats.played}
          </p>
          <div className="flex gap-2 pt-1">
            <button type="button" className={`${btn} flex-1`} onClick={share}>Share riddle</button>
            <button type="button" className={`${btn} flex-1 !bg-purple-500 hover:!bg-purple-600`} onClick={nextRiddle} autoFocus>Next riddle</button>
          </div>
        </div>
      )}
      {copied && <p role="status" className="text-sm text-white/80 whitespace-pre-wrap break-words">{copied}</p>}
    </div>
  )
}

