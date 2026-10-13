import { useEffect, useMemo, useRef, useState } from 'react'
import * as lb from './lib/letterBalloonsLogic.js'
import {
  stats, seedFor, difficultyOf, useGameSession, SrOnly, usePhysicalKeys, Keyboard, ActionButton, ResultCard, withWordGame,
} from './WordPuzzleUi.jsx'

const BALLOON = ['#ef4444', '#3b82f6', '#22c55e', '#eab308', '#a855f7', '#f97316', '#ec4899', '#14b8a6']

function Balloons({ left, total }) {
  const W = 320
  const gap = Math.min(40, (W - 40) / Math.max(total, 1))
  const x0 = W / 2 - (gap * (total - 1)) / 2
  return (
    <svg viewBox="0 0 320 130" className="w-full max-w-[320px] h-auto" role="img" aria-label={`${left} of ${total} balloons left`}>
      <rect x="145" y="112" width="30" height="14" fill="#8d6e63" />
      {Array.from({ length: total }, (_, i) => {
        const cx = x0 + gap * i
        const cy = 30 + (i % 2 ? 14 : 0)
        return i < left ? (
          <g key={i}>
            <line x1={cx} y1={cy + 18} x2={160} y2={112} stroke="#d1c4e9" strokeWidth="1.5" />
            <ellipse cx={cx} cy={cy} rx="15" ry="18" fill={BALLOON[i % BALLOON.length]} />
          </g>
        ) : (
          <g key={i} stroke="#fff" opacity=".45"><path d={`M${cx - 6} ${cy + 16} l12 0 m-6 -6 l0 12`} strokeWidth="2" /></g>
        )
      })}
    </svg>
  )
}

function Game({ puzzle, onAnswer }) {
  const difficulty = difficultyOf(puzzle)
  const data = useMemo(() => lb.generate(difficulty, seedFor(puzzle, 'letterballoons')), [puzzle, difficulty])
  const session = useGameSession('letterballoons', difficulty)
  const [g, setG] = useState(() => lb.newGame(data.word, data.category, data.lives))
  const [announce, setAnnounce] = useState('')
  const recorded = useRef(false)
  const [snap, setSnap] = useState(null)
  const over = lb.isOver(g)
  const won = lb.isWon(g)
  const score = lb.score(g)

  useEffect(() => {
    if (over && !recorded.current) {
      recorded.current = true
      stats.record('letterballoons', won)
      setSnap(stats.snapshot('letterballoons'))
      session.finish(won, score)
    }
  }, [over]) // eslint-disable-line react-hooks/exhaustive-deps

  const apply = (next, c) => {
    if (next === g) return
    session.move()
    if (c) {
      const hit = g.word.includes(c)
      if (!hit) session.mistake()
      setAnnounce(hit ? `${c.toUpperCase()} is in the word` : `${c.toUpperCase()} is not in the word. ${lb.livesLeft(next)} balloons left`)
    }
    setG(next)
  }
  const press = (c) => apply(lb.guess(g, c), c)
  usePhysicalKeys((k) => { if (k.length === 1) press(k) }, !over)

  const masked = lb.maskedLetters(g)
  const groups = []
  let cur = []
  masked.forEach((ch, i) => {
    if (ch === ' ') { groups.push(cur); cur = [] } else cur.push({ ch, real: g.word[i], pos: i + 1 })
  })
  groups.push(cur)
  const states = {}
  g.guessed.forEach((c) => { states[c] = g.word.includes(c) ? 'correct' : 'absent' })
  const wrong = lb.wrongLetters(g)

  return (
    <div className="space-y-3 flex flex-col items-center">
      <p className="text-yellow-200 font-bold">Category: {g.category}</p>
      <p className="text-white/60 text-xs">Guess the word before the balloons are all popped</p>
      <Balloons left={lb.livesLeft(g)} total={g.maxLives} />
      <div className="space-y-1.5" aria-label="Word">
        {groups.map((grp, gi) => (
          <div key={gi} className="flex gap-1 justify-center">
            {grp.map((t) => {
              const shown = t.ch || (lb.isLost(g) ? t.real : '')
              return (
                <div key={t.pos} aria-label={`Letter ${t.pos}, ${shown ? shown.toUpperCase() : 'hidden'}`}
                  className={`w-7 sm:w-9 h-11 flex items-center justify-center rounded-md border-2 text-lg font-extrabold bg-white/10 ${
                    !t.ch && lb.isLost(g) ? 'border-red-400 text-red-300' : 'border-white/40 text-white'}`}>
                  {shown.toUpperCase()}
                </div>
              )
            })}
          </div>
        ))}
      </div>
      <p className="text-white text-sm">
        Balloons left: {lb.livesLeft(g)}/{g.maxLives}
        {wrong.length > 0 && <>&nbsp;&nbsp;Missed: {wrong.map((c) => c.toUpperCase()).join(' ')}</>}
      </p>
      <SrOnly>{announce}</SrOnly>
      {!over ? (
        <>
          <ActionButton onClick={() => { session.hint(); setG(lb.hint(g)) }} label="Hint: reveal one letter for a score penalty">Hint (-20 pts)</ActionButton>
          <Keyboard keyStates={states} onLetter={press} showActions={false} />
        </>
      ) : (
        <ResultCard
          success={won} score={score}
          headline={won ? 'You saved the balloons!' : `The word was ${g.word.toUpperCase()}`}
          snapshot={snap}
          onContinue={() => onAnswer(won, g.guessed.join(''))}
        />
      )}
    </div>
  )
}
export default withWordGame(Game, 'letterballoons')
