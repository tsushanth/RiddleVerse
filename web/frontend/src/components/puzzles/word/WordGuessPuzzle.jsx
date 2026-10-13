import { useEffect, useMemo, useRef, useState } from 'react'
import * as wg from './lib/wordGuessLogic.js'
import {
  stats, TILE_COLORS, GLYPH, STATE_LABEL, seedFor, difficultyOf, useWordLists, ListsGate, useGameSession,
  useMessage, Announcer, SrOnly, usePhysicalKeys, Keyboard, ActionButton, ResultCard, withWordGame,
} from './WordPuzzleUi.jsx'

function Board({ lists, puzzle, onAnswer }) {
  const difficulty = difficultyOf(puzzle)
  const data = useMemo(() => wg.generate(lists, difficulty, seedFor(puzzle, 'wordguess')), [lists, puzzle, difficulty])
  const session = useGameSession('wordguess', difficulty)
  const [game, setGame] = useState(() => wg.newGame(data.answer, data.hard))
  const [hintPos, setHintPos] = useState(-1)
  const [msg, show] = useMessage()
  const [announce, setAnnounce] = useState('')
  const recorded = useRef(false)
  const [snap, setSnap] = useState(null)
  const status = wg.status(game)
  const won = status === 'won'
  const score = won ? wg.score(game.guesses.length, game.hintUsed, game.hard) : 0

  useEffect(() => {
    if (status !== 'playing' && !recorded.current) {
      recorded.current = true
      stats.record('wordguess', won, game.guesses.length, wg.MAX_TRIES)
      setSnap(stats.snapshot('wordguess', wg.MAX_TRIES))
      session.finish(won, score)
    }
  }, [status]) // eslint-disable-line react-hooks/exhaustive-deps

  const press = (c) => setGame((g) => wg.typeLetter(g, c))
  const del = () => setGame((g) => wg.backspace(g))
  const enter = () => {
    const { game: next, outcome, message } = wg.submit(game, (w) => lists.valid5.has(w))
    if (outcome === 'accepted') {
      session.move()
      const fb = wg.evaluate(game.current, game.answer)
      setAnnounce(`${game.current.toUpperCase().split('').map((c, i) => `${c} ${STATE_LABEL[fb[i]]}`).join(', ')}`)
      setGame(next)
    } else {
      if (outcome !== 'gameOver') session.mistake()
      show(outcome === 'tooShort' ? 'Not enough letters' : outcome === 'notInList' ? 'Not in word list' : message)
    }
  }
  usePhysicalKeys((k) => {
    if (k === 'Enter') enter()
    else if (k === 'Backspace') del()
    else if (k.length === 1) press(k)
  }, status === 'playing')

  const hint = () => {
    const p = wg.hintPosition(game)
    if (p >= 0) { setHintPos(p); setGame({ ...game, hintUsed: true }); session.hint() }
  }

  return (
    <div className="space-y-3 flex flex-col items-center">
      <p className="text-white/60 text-xs uppercase tracking-widest font-semibold">
        Word Guess{game.hard ? ' (hard mode)' : ''}
      </p>
      <div className="grid gap-1.5 w-full max-w-[320px]" role="grid" aria-label="Guess board">
        {Array.from({ length: wg.MAX_TRIES }, (_, r) => {
          const submitted = game.guesses[r]
          const fb = submitted ? wg.evaluate(submitted, game.answer) : null
          const typing = r === game.guesses.length && status === 'playing' ? game.current : ''
          return (
            <div key={r} role="row" className="grid grid-cols-5 gap-1.5">
              {Array.from({ length: 5 }, (_, c) => {
                const letter = submitted ? submitted[c] : typing[c]
                const st = fb ? fb[c] : null
                const cls = st ? TILE_COLORS[st] : letter ? 'bg-white/10 border-white/70 text-white' : TILE_COLORS.none
                const label = !letter ? `Row ${r + 1}, column ${c + 1}, empty`
                  : st ? `Row ${r + 1}, column ${c + 1}, ${letter.toUpperCase()}, ${STATE_LABEL[st]}`
                    : `Row ${r + 1}, column ${c + 1}, ${letter.toUpperCase()}, not submitted`
                return (
                  <div key={c} role="gridcell" aria-label={label}
                    className={`relative aspect-square flex items-center justify-center rounded-md border-2 text-2xl font-extrabold ${cls}`}>
                    {letter ? letter.toUpperCase() : ''}
                    {st && <span aria-hidden="true" className="absolute top-0.5 right-1 text-[10px]">{GLYPH[st]}</span>}
                  </div>
                )
              })}
            </div>
          )
        })}
      </div>
      <Announcer message={msg} />
      <SrOnly>{announce}</SrOnly>
      {hintPos >= 0 && status === 'playing' && (
        <p className="text-yellow-200 text-sm font-semibold">Hint: letter {hintPos + 1} is {game.answer[hintPos].toUpperCase()}</p>
      )}
      {status === 'playing' ? (
        <>
          <div className="flex items-center gap-3">
            <ActionButton onClick={hint} disabled={hintPos >= 0} label="Hint: reveal one letter for a score penalty">Hint (-30 pts)</ActionButton>
            <span className="text-white/70 text-sm">{wg.MAX_TRIES - game.guesses.length} tries left</span>
          </div>
          <Keyboard keyStates={wg.keyboardStates(game.guesses, game.answer)} onLetter={press} onDelete={del} onEnter={enter} />
        </>
      ) : (
        <ResultCard
          success={won} score={score}
          headline={won ? `Solved in ${game.guesses.length}!` : `The word was ${game.answer.toUpperCase()}`}
          snapshot={snap} distribution={snap?.distribution}
          highlight={won ? game.guesses.length : 0}
          shareText={wg.shareText(game.guesses, game.answer, won, game.hard)}
          onContinue={() => onAnswer(won, game.guesses[game.guesses.length - 1] || '')}
        />
      )}
    </div>
  )
}

function WordGuessInner({ puzzle, onAnswer }) {
  const state = useWordLists()
  return <ListsGate state={state}>{(lists) => <Board lists={lists} puzzle={puzzle} onAnswer={onAnswer} />}</ListsGate>
}
export default withWordGame(WordGuessInner, 'wordguess')
