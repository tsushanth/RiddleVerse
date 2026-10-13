import { useEffect, useMemo, useRef, useState } from 'react'
import * as wh from './lib/wordHiveLogic.js'
import { createRng, shuffled } from './lib/prng.js'
import {
  stats, seedFor, difficultyOf, useWordLists, ListsGate, useGameSession, useMessage, Announcer,
  usePhysicalKeys, ActionButton, ResultCard, withWordGame,
} from './WordPuzzleUi.jsx'

// Hex positions (px offsets from centre) for the six outer cells.
const POS = [[0, -68], [59, -34], [59, 34], [0, 68], [-59, 34], [-59, -34]]

function Hive({ lists, puzzle, onAnswer }) {
  const difficulty = difficultyOf(puzzle)
  const p = useMemo(() => wh.generate(lists, difficulty, seedFor(puzzle, 'wordhive')), [lists, puzzle, difficulty])
  const session = useGameSession('wordhive', difficulty)
  const letters = useMemo(() => new Set(p.outer + p.center), [p])
  const sols = useMemo(() => new Set(p.words), [p])
  const targetIdx = wh.targetRankIndex(difficulty)
  const [found, setFound] = useState([])
  const [entry, setEntry] = useState('')
  const [shuffle, setShuffle] = useState(0)
  const [hints, setHints] = useState(0)
  const [finished, setFinished] = useState(false)
  const [msg, show] = useMessage()
  const recorded = useRef(false)
  const [snap, setSnap] = useState(null)

  const outer = useMemo(() => (shuffle === 0 ? [...p.outer] : shuffled([...p.outer], createRng(shuffle))), [p, shuffle])
  const score = found.reduce((a, w) => a + wh.wordScore(w, letters), 0)
  const rankIdx = wh.rankIndex(score, p.maxScore)
  const success = rankIdx >= targetIdx
  const finalScore = success ? Math.max(40 + rankIdx * 10 - hints * 10, 10) : 0
  const pangrams = p.words.filter((w) => wh.isPangram(w, letters))

  useEffect(() => {
    if (finished && !recorded.current) {
      recorded.current = true
      stats.record('wordhive', success)
      setSnap(stats.snapshot('wordhive'))
      session.finish(success, finalScore)
    }
  }, [finished]) // eslint-disable-line react-hooks/exhaustive-deps

  const submit = () => {
    const why = wh.rejection(entry, letters, p.center, sols, found)
    if (why) { session.mistake(); show(why); return }
    session.move()
    const pts = wh.wordScore(entry, letters)
    show(wh.isPangram(entry, letters) ? `Pangram! +${pts}` : `+${pts}`)
    setFound([...found, entry]); setEntry('')
  }
  const hint = () => {
    const missing = p.words.filter((w) => !found.includes(w))
    if (!missing.length) { show('You found them all!'); return }
    const min = Math.min(...missing.map((w) => w.length))
    const w = missing.find((m) => m.length === min)
    setHints((h) => h + 1); session.hint()
    show(`Try: ${w.slice(0, 2).toUpperCase()}... (${w.length} letters)`)
  }
  usePhysicalKeys((k) => {
    if (k === 'Enter') submit()
    else if (k === 'Backspace') setEntry((e) => e.slice(0, -1))
    else if (k.length === 1) setEntry((e) => (e.length < 20 ? e + k : e))
  }, !finished)

  const cell = (c, style, center) => (
    <button key={c + (center ? 'c' : '')} type="button" onClick={() => setEntry((e) => e + c)}
      aria-label={`${c.toUpperCase()}${center ? ', centre letter, required' : ''}`}
      style={style}
      className={`absolute w-14 h-14 -ml-7 -mt-7 rounded-xl text-xl font-extrabold touch-manipulation ${
        center ? 'bg-amber-400 text-black ring-2 ring-white' : 'bg-white/20 text-white hover:bg-white/30'}`}>
      {c.toUpperCase()}
    </button>
  )

  return (
    <div className="space-y-3 flex flex-col items-center">
      <p className="text-white/70 text-sm text-center">Make words of 4+ letters that use the gold centre letter</p>
      <p className="text-white text-lg font-bold">{wh.RANKS[rankIdx][0]} - {score} pts</p>
      <div className="w-full h-2 rounded bg-white/20" role="progressbar" aria-valuemin={0} aria-valuemax={p.maxScore} aria-valuenow={score} aria-label="Score progress">
        <div className="h-2 rounded bg-amber-400" style={{ width: `${Math.min(100, (score / Math.max(p.maxScore, 1)) * 100)}%` }} />
      </div>
      <p className="text-white/70 text-sm">Goal: reach {wh.RANKS[targetIdx][0]} &nbsp; Words {found.length}/{p.words.length}</p>
      {!finished ? (
        <>
          <div className="min-h-[44px] flex items-center text-3xl font-extrabold tracking-wide" aria-label={`Current word ${entry.toUpperCase()}`}>
            {[...entry].map((c, i) => (
              <span key={i} className={c === p.center ? 'text-amber-300' : letters.has(c) ? 'text-white' : 'text-red-300'}>{c.toUpperCase()}</span>
            ))}
          </div>
          <Announcer message={msg} />
          <div className="relative w-[220px] h-[210px] mt-1" role="group" aria-label="Letters">
            {outer.map((c, i) => cell(c, { left: 110 + POS[i][0], top: 105 + POS[i][1] }, false))}
            {cell(p.center, { left: 110, top: 105 }, true)}
          </div>
          <div className="flex gap-2 flex-wrap justify-center">
            <ActionButton onClick={() => setEntry(entry.slice(0, -1))} disabled={!entry} label="Delete last letter">Delete</ActionButton>
            <ActionButton onClick={() => setShuffle(shuffle + 1)} label="Shuffle outer letters">Shuffle</ActionButton>
            <ActionButton primary onClick={submit}>Enter</ActionButton>
          </div>
          <div className="flex gap-2 flex-wrap justify-center">
            <ActionButton onClick={hint} label="Hint: show the start of a word you have not found">Hint (-10 pts)</ActionButton>
            <ActionButton onClick={() => setFinished(true)} label={success ? 'Finish with your rank' : 'Give up and see results'}>{success ? 'Finish' : 'Give up'}</ActionButton>
          </div>
          {success && <p className="text-green-300 text-sm font-semibold">Goal reached! Keep going for a higher rank or finish.</p>}
        </>
      ) : (
        <ResultCard
          success={success} score={finalScore}
          headline={success ? `${wh.RANKS[rankIdx][0]}!` : `Final rank: ${wh.RANKS[rankIdx][0]}`}
          lines={[
            `Found ${found.length} of ${p.words.length} words, ${score} of ${p.maxScore} points`,
            pangrams.some((w) => !found.includes(w)) ? `Pangram: ${pangrams.filter((w) => !found.includes(w)).map((w) => w.toUpperCase()).join(', ')}` : '',
          ]}
          snapshot={snap}
          shareText={`RiddleVerse Word Hive: ${wh.RANKS[rankIdx][0]}, ${found.length}/${p.words.length} words`}
          onContinue={() => onAnswer(success, `${found.length} words`)}
        />
      )}
      {found.length > 0 && (
        <div className="w-full">
          <p className="text-white text-sm font-semibold mb-1">Found words</p>
          <ul className="flex flex-wrap gap-1.5">
            {[...found].sort().map((w) => {
              const pan = wh.isPangram(w, letters)
              return (
                <li key={w} aria-label={pan ? `${w}, pangram` : w}
                  className={`px-2 py-1 rounded-md text-sm ${pan ? 'bg-amber-400 text-black font-bold' : 'bg-white/15 text-white'}`}>
                  {w}{pan && <span aria-hidden="true"> {'★'}</span>}
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}

function Inner({ puzzle, onAnswer }) {
  const state = useWordLists()
  return (
    <ListsGate state={state}>
      {(lists) => <Hive lists={lists} puzzle={puzzle} onAnswer={onAnswer} />}
    </ListsGate>
  )
}
export default withWordGame(Inner, 'wordhive')
