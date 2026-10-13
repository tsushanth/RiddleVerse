import { useEffect, useMemo, useRef, useState } from 'react'
import * as cc from './lib/codeCrackerLogic.js'
import {
  stats, seedFor, difficultyOf, useGameSession, useMessage, Announcer, SrOnly, usePhysicalKeys, ActionButton, ResultCard, withWordGame,
} from './WordPuzzleUi.jsx'

function Peg({ color, size = 40, label, selected, onClick }) {
  const filled = color >= 0
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag type={onClick ? 'button' : undefined} onClick={onClick}
      aria-label={`${label}, ${filled ? `${cc.COLOR_NAMES[color]} ${cc.COLOR_SYMBOLS[color]}` : 'empty'}${selected ? ', selected' : ''}`}
      aria-pressed={onClick && selected !== undefined ? !!selected : undefined}
      style={{ width: size, height: size, background: filled ? cc.COLOR_HEX[color] : 'transparent' }}
      className={`rounded-full flex items-center justify-center font-extrabold touch-manipulation border-2 ${
        selected ? 'border-white ring-2 ring-white' : 'border-white/50'} ${color === 3 ? 'text-black' : 'text-white'}`}>
      <span aria-hidden="true" style={{ fontSize: size * 0.45 }}>{filled ? cc.COLOR_SYMBOLS[color] : ''}</span>
    </Tag>
  )
}

function Feedback({ fb, positions, label }) {
  const pegs = [...Array(fb.exact).fill('exact'), ...Array(fb.misplaced).fill('mis'), ...Array(positions - fb.exact - fb.misplaced).fill('none')]
  return (
    <div aria-label={`${label}: ${fb.exact} right colour and place, ${fb.misplaced} right colour wrong place`}
      style={{ gridTemplateColumns: `repeat(${Math.ceil(positions / 2)}, 10px)` }}
      className="grid gap-0.5">
      {pegs.map((k, i) => (
        <span key={i} aria-hidden="true" className={`w-2.5 h-2.5 rounded-full border ${
          k === 'exact' ? 'bg-black border-white' : k === 'mis' ? 'bg-white border-white' : 'border-white/30'}`} />
      ))}
    </div>
  )
}

function Game({ puzzle, onAnswer }) {
  const difficulty = difficultyOf(puzzle)
  const p = useMemo(() => cc.generate(difficulty, seedFor(puzzle, 'codecracker')), [puzzle, difficulty])
  const session = useGameSession('codecracker', difficulty)
  const [guesses, setGuesses] = useState([])
  const [current, setCurrent] = useState(() => Array(p.positions).fill(-1))
  const [selected, setSelected] = useState(0)
  const [hints, setHints] = useState(0)
  const [hintPos, setHintPos] = useState(-1)
  const [msg, show] = useMessage()
  const [announce, setAnnounce] = useState('')
  const recorded = useRef(false)
  const [snap, setSnap] = useState(null)
  const feedbacks = useMemo(() => guesses.map((g) => cc.feedback(p.secret, g)), [guesses, p])
  const won = guesses.length > 0 && guesses[guesses.length - 1].every((c, i) => c === p.secret[i])
  const lost = !won && guesses.length >= p.tries
  const over = won || lost
  const score = won ? cc.score(guesses.length, p.tries, hints) : 0

  useEffect(() => {
    if (over && !recorded.current) {
      recorded.current = true
      stats.record('codecracker', won, guesses.length, p.tries)
      setSnap(stats.snapshot('codecracker'))
      session.finish(won, score)
    }
  }, [over]) // eslint-disable-line react-hooks/exhaustive-deps

  const place = (color) => {
    if (over || color < 0 || color >= p.colors) return
    const i = selected >= 0 && selected < p.positions ? selected : 0
    const next = current.slice(); next[i] = color
    setCurrent(next)
    let n = next.findIndex((c, k) => k > i && c < 0)
    if (n < 0) n = next.findIndex((c) => c < 0)
    if (n >= 0) setSelected(n)
  }
  const check = () => {
    if (over) return
    if (current.includes(-1)) { show('Fill every peg'); return }
    if (!p.repeats && new Set(current).size !== current.length) { session.mistake(); show('No repeated colours on this level'); return }
    const fb = cc.feedback(p.secret, current)
    session.move()
    setAnnounce(`Try ${guesses.length + 1}: ${fb.exact} right colour and place, ${fb.misplaced} right colour wrong place`)
    setGuesses([...guesses, current]); setCurrent(Array(p.positions).fill(-1)); setSelected(0)
  }
  const clear = () => { setCurrent(Array(p.positions).fill(-1)); setSelected(0) }
  const back = () => {
    const i = current.reduce((acc, c, k) => (c >= 0 ? k : acc), -1)
    if (i < 0) return
    const next = current.slice(); next[i] = -1; setCurrent(next); setSelected(i)
  }
  const hint = () => {
    const unsolved = [...Array(p.positions).keys()].filter((i) => guesses.every((g) => g[i] !== p.secret[i]))
    setHintPos(unsolved.length ? unsolved[0] : 0); setHints((h) => h + 1); session.hint()
  }
  usePhysicalKeys((k) => { if (k === 'Enter') check(); else if (k === 'Backspace') back() }, !over)
  useEffect(() => {
    if (over) return undefined
    const onKey = (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey || !/^[1-9]$/.test(e.key)) return
      place(parseInt(e.key, 10) - 1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  return (
    <div className="space-y-3 flex flex-col items-center">
      <p className="text-white/70 text-sm text-center">
        Crack the {p.positions}-peg code in {p.tries} tries. Black dot = right colour and place; white dot = right colour, wrong place.
        {p.repeats ? ' Colours can repeat.' : ' No colour repeats.'}
      </p>
      <div className="space-y-1.5">
        {guesses.map((g, r) => (
          <div key={r} className="flex items-center gap-1.5 justify-center">
            {g.map((c, i) => <Peg key={i} color={c} size={36} label={`Try ${r + 1}, position ${i + 1}`} />)}
            <Feedback fb={feedbacks[r]} positions={p.positions} label={`Try ${r + 1}`} />
          </div>
        ))}
        {!over && (
          <div className="flex items-center gap-1.5 justify-center">
            {current.map((c, i) => <Peg key={i} color={c} size={48} label={`Your try, position ${i + 1}`} selected={i === selected} onClick={() => setSelected(i)} />)}
          </div>
        )}
      </div>
      {!over && <p className="text-white/60 text-xs">Try {guesses.length + 1} of {p.tries}</p>}
      {hintPos >= 0 && !over && (
        <p className="text-yellow-200 text-sm font-semibold">
          Hint: position {hintPos + 1} is {cc.COLOR_NAMES[p.secret[hintPos]]} {cc.COLOR_SYMBOLS[p.secret[hintPos]]}
        </p>
      )}
      <Announcer message={msg} />
      <SrOnly>{announce}</SrOnly>
      {!over ? (
        <>
          <div className="flex gap-2 flex-wrap justify-center" role="group" aria-label="Colours">
            {Array.from({ length: p.colors }, (_, c) => (
              <Peg key={c} color={c} size={48} label={`Choose ${cc.COLOR_NAMES[c]}, key ${c + 1}`} selected={undefined} onClick={() => place(c)} />
            ))}
          </div>
          <div className="flex gap-2 flex-wrap justify-center">
            <ActionButton onClick={clear}>Clear</ActionButton>
            <ActionButton onClick={hint} disabled={hintPos >= 0} label="Hint: reveal one position of the code">Hint (-20 pts)</ActionButton>
            <ActionButton primary onClick={check}>Check</ActionButton>
          </div>
        </>
      ) : (
        <>
          {lost && (
            <div className="space-y-1">
              <p className="text-white text-sm text-center">The code was:</p>
              <div className="flex gap-1.5 justify-center">
                {p.secret.map((c, i) => <Peg key={i} color={c} size={44} label={`Code position ${i + 1}`} />)}
              </div>
            </div>
          )}
          <ResultCard
            success={won} score={score}
            headline={won ? `Cracked in ${guesses.length}!` : 'Out of tries'}
            snapshot={snap}
            shareText={cc.shareText(guesses, feedbacks, won, p.tries)}
            onContinue={() => onAnswer(won, `${guesses.length} tries`)}
          />
        </>
      )}
    </div>
  )
}
export default withWordGame(Game, 'codecracker')
