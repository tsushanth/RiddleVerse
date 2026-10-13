import { useEffect, useMemo, useRef, useState } from 'react'
import * as wl from './lib/wordLadderLogic.js'
import {
  stats, seedFor, difficultyOf, useWordLists, ListsGate, useGameSession, useMessage, Announcer,
  usePhysicalKeys, Keyboard, ActionButton, ResultCard, withWordGame,
} from './WordPuzzleUi.jsx'

function Row({ word, prev, label, goal }) {
  return (
    <div className="flex gap-1.5 justify-center">
      {[...word].map((c, i) => {
        const changed = prev && prev[i] !== c
        const cls = goal ? 'bg-emerald-600 border-emerald-600' : changed ? 'bg-purple-500 border-purple-500' : 'bg-white/10 border-white/30'
        return (
          <div key={i} aria-label={`${label}, letter ${i + 1}, ${c.toUpperCase()}${changed ? ', changed' : ''}`}
            className={`w-12 h-12 flex items-center justify-center rounded-md border-2 text-xl font-extrabold text-white ${cls}`}>
            {c.toUpperCase()}
          </div>
        )
      })}
    </div>
  )
}

function Ladder({ lists, puzzle, onAnswer }) {
  const difficulty = difficultyOf(puzzle)
  const p = useMemo(() => wl.generateWithFallback(lists, difficulty, seedFor(puzzle, 'wordladder')), [lists, puzzle, difficulty])
  const graph = useMemo(() => new wl.WordLadderGraph([...wl.poolFor(lists, p.length), ...p.solution]), [lists, p])
  const session = useGameSession('wordladder', difficulty)
  const [chain, setChain] = useState([p.start])
  const [candidate, setCandidate] = useState(p.start)
  const [selected, setSelected] = useState(-1)
  const [hints, setHints] = useState(0)
  const [gaveUp, setGaveUp] = useState(false)
  const [msg, show] = useMessage(2500)
  const recorded = useRef(false)
  const [snap, setSnap] = useState(null)
  const last = chain[chain.length - 1]
  const solved = last === p.target
  const over = solved || gaveUp
  const steps = chain.length - 1
  const score = solved ? wl.score(steps, p.par, hints) : 0

  useEffect(() => {
    if (over && !recorded.current) {
      recorded.current = true
      stats.record('wordladder', solved)
      setSnap(stats.snapshot('wordladder'))
      session.finish(solved, score)
    }
  }, [over]) // eslint-disable-line react-hooks/exhaustive-deps

  const letter = (c) => {
    if (selected < 0) { show('Tap a tile to choose which letter to change'); return }
    setCandidate((w) => w.slice(0, selected) + c + w.slice(selected + 1))
  }
  const reset = () => { setCandidate(last); setSelected(-1) }
  const add = () => {
    const why = wl.rejection(graph, chain, candidate)
    if (why) { session.mistake(); show(why); return }
    session.move()
    setChain([...chain, candidate]); setCandidate(candidate); setSelected(-1)
    show(`Added ${candidate.toUpperCase()}`)
  }
  const undo = () => {
    if (chain.length < 2) return
    const c = chain.slice(0, -1)
    setChain(c); setCandidate(c[c.length - 1]); setSelected(-1); session.undo()
  }
  const hint = () => {
    const path = graph.shortestPath(last, p.target)
    if (path && path.length > 1) {
      const nxt = path[1]
      const pos = [...nxt].findIndex((ch, i) => ch !== last[i])
      setHints((h) => h + 1); session.hint()
      show(`Change letter ${pos + 1} to ${nxt[pos].toUpperCase()}`)
    } else show('No path from here - undo a step')
  }
  usePhysicalKeys((k) => {
    if (k === 'Enter') add()
    else if (k === 'Backspace') reset()
    else if (k === 'ArrowLeft') setSelected((s) => (s <= 0 ? p.length - 1 : s - 1))
    else if (k === 'ArrowRight') setSelected((s) => (s + 1) % p.length)
    else letter(k)
  }, !over)

  return (
    <div className="space-y-3 flex flex-col items-center">
      <p className="text-white/70 text-sm text-center">
        Change one letter at a time to turn <b>{p.start.toUpperCase()}</b> into <b>{p.target.toUpperCase()}</b>
      </p>
      <p className="text-white font-bold">Par {p.par} steps &nbsp; You: {steps}</p>
      <div className="space-y-1.5 w-full">
        {chain.map((w, i) => <Row key={i} word={w} prev={chain[i - 1]} label={i === 0 ? 'Start word' : `Step ${i}`} goal={w === p.target} />)}
        {!over && (
          <div className="flex gap-1.5 justify-center">
            {[...candidate].map((c, i) => (
              <button key={i} type="button" onClick={() => setSelected(i)}
                aria-label={`Your word, letter ${i + 1}, ${c.toUpperCase()}${i === selected ? ', selected' : ''}. Press to change this letter`}
                aria-pressed={i === selected}
                className={`w-12 h-12 rounded-md border-2 text-xl font-extrabold text-white touch-manipulation ${
                  i === selected ? 'bg-purple-400/50 border-white' : c !== last[i] ? 'bg-white/10 border-amber-400' : 'bg-white/10 border-white/40'}`}>
                {c.toUpperCase()}
              </button>
            ))}
          </div>
        )}
        {gaveUp && !solved && (
          <div className="space-y-1.5 pt-2">
            <p className="text-white font-semibold text-center text-sm">One shortest path:</p>
            {p.solution.map((w, i) => <Row key={i} word={w} prev={p.solution[i - 1]} label={`Solution step ${i}`} />)}
          </div>
        )}
      </div>
      <Announcer message={msg} />
      {!over ? (
        <>
          <div className="flex gap-2 flex-wrap justify-center">
            <ActionButton onClick={undo} disabled={chain.length < 2}>Undo</ActionButton>
            <ActionButton onClick={hint} label="Hint: show the next word on a shortest path">Hint</ActionButton>
            <ActionButton onClick={() => setGaveUp(true)} label="Give up and reveal the solution">Give up</ActionButton>
          </div>
          <Keyboard enterLabel="ADD" onLetter={letter} onDelete={reset} onEnter={add} />
        </>
      ) : (
        <ResultCard
          success={solved} score={score}
          headline={solved ? `Ladder complete in ${steps}! ${'★'.repeat(wl.stars(steps, p.par))}` : 'Better luck next time'}
          lines={solved ? [steps <= p.par ? 'You matched the shortest route.' : `Shortest route: ${p.par} steps.`] : []}
          snapshot={snap}
          shareText={solved ? `RiddleVerse Word Ladder ${p.start.toUpperCase()} to ${p.target.toUpperCase()}: ${steps} steps (par ${p.par})` : null}
          onContinue={() => onAnswer(solved, `${steps} steps`)}
        />
      )}
    </div>
  )
}

function Inner({ puzzle, onAnswer }) {
  const state = useWordLists()
  return <ListsGate state={state}>{(lists) => <Ladder lists={lists} puzzle={puzzle} onAnswer={onAnswer} />}</ListsGate>
}
export default withWordGame(Inner, 'wordladder')
