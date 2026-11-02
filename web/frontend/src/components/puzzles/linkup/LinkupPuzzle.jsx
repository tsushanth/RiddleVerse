// web/game-linkup: the Linkup board. Rules live in linkupLogic.js, bank/selection in linkupBank.js.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import * as L from './linkupLogic.js'
import { LinkupProgress, localStorageStore, parseBank, puzzleFor, safeStore } from './linkupBank.js'
import {
  stats, useGameSession, useMessage, Announcer, ActionButton, ResultCard, withWordGame,
} from '../word/WordPuzzleUi.jsx'
import { normalizeDifficulty } from '../word/lib/prng.js'

// The 152-puzzle bank is a separate chunk, fetched only when Linkup is opened.
let bankPromise = null
function loadBank() {
  if (!bankPromise) {
    bankPromise = import('../../../../../shared/linkupPuzzles.json')
      .then((m) => parseBank(m.default))
      .catch((e) => { bankPromise = null; throw e })
  }
  return bankPromise
}

function useBank() {
  const [state, setState] = useState({ bank: null, error: null })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let live = true
    loadBank()
      .then((bank) => live && setState({ bank, error: null }))
      .catch((error) => live && setState({ bank: null, error }))
    return () => { live = false }
  }, [attempt])
  return { ...state, retry: () => setAttempt((n) => n + 1) }
}

let progress = null
const getProgress = () => (progress ??= new LinkupProgress(safeStore(localStorageStore())))

// Fill + text per tier. Each tier also has its own name and shape glyph, so colour is never the only cue.
const TIER_STYLE = {
  1: { background: '#FFA94D', color: '#2B1600' },
  2: { background: '#4DD0B0', color: '#04291F' },
  3: { background: '#F06292', color: '#330012' },
  4: { background: '#5C6BC0', color: '#FFFFFF' },
}

function Banner({ group, missed }) {
  const label = `${L.tierName(group.tier)} group${missed ? ', not found' : ''}: ${group.title}. ${group.words.join(', ')}`
  return (
    <div role="group" aria-label={label} style={TIER_STYLE[group.tier]} className="rounded-lg px-3 py-2 text-center w-full">
      <div aria-hidden="true" className="text-xs font-semibold">
        {L.tierGlyph(group.tier)} {L.tierName(group.tier)}{missed ? ' (missed)' : ''}
      </div>
      <div aria-hidden="true" className="font-extrabold uppercase text-sm sm:text-base">{group.title}</div>
      <div aria-hidden="true" className="text-xs sm:text-sm">{group.words.join(', ')}</div>
    </div>
  )
}

function Tile({ word, selected, hinted, disabled, onToggle }) {
  const border = selected ? 'border-4 border-white' : hinted ? 'border-4 border-yellow-300' : 'border border-white/40'
  const fill = selected ? 'bg-white text-purple-900' : 'bg-white/15 text-white hover:bg-white/25'
  const size = word.length <= 6 ? 'text-sm sm:text-base' : word.length <= 8 ? 'text-xs sm:text-sm' : 'text-[11px] sm:text-xs'
  return (
    <button
      type="button" aria-pressed={selected} disabled={disabled} onClick={onToggle}
      aria-label={`${word}, ${selected ? 'selected' : 'not selected'}${hinted ? ', hint: belongs to the easiest group' : ''}`}
      className={`relative min-h-[56px] rounded-lg px-1 font-bold touch-manipulation transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-yellow-300 ${border} ${fill} ${size}`}
    >
      <span aria-hidden="true">{word}</span>
      {selected && <span aria-hidden="true" className="absolute top-0.5 left-1.5 text-[11px]">{'✓'}</span>}
      {hinted && <span aria-hidden="true" className="absolute top-0.5 right-1.5 text-[11px]">{'◆'}</span>}
    </button>
  )
}

function Board({ bank, puzzle: play, onAnswer }) {
  const difficulty = normalizeDifficulty(play?.difficulty)
  const prof = useMemo(() => L.profile(difficulty), [difficulty])
  const puzzle = useMemo(
    () => puzzleFor(bank, play?.puzzleId ?? `anon-${Math.random()}`, getProgress()),
    [bank, play?.puzzleId],
  )
  const session = useGameSession('linkup', difficulty)
  const [state, setState] = useState(L.newState)
  const [msg, show] = useMessage(2200)
  const [snap, setSnap] = useState(null)
  const recorded = useRef(false)

  const won = L.isWon(state)
  const lost = L.isLost(state)
  const finished = won || lost
  const tiles = useMemo(() => L.available(puzzle, state), [puzzle, state.solved, state.shuffle]) // eslint-disable-line react-hooks/exhaustive-deps
  const pts = L.score(state, prof)

  useEffect(() => {
    if (finished && !recorded.current) {
      recorded.current = true
      stats.record('linkup', won)
      setSnap(stats.snapshot('linkup'))
      session.finish(won, pts)
    }
  }, [finished]) // eslint-disable-line react-hooks/exhaustive-deps

  const submit = useCallback(() => {
    const { state: next, result } = L.submit(puzzle, state)
    setState(next)
    if (result === 'SOLVED') { session.move(); show(`Found it: ${puzzle.groups[next.solved[next.solved.length - 1]].title}`) }
    else if (result === 'CLOSE') { session.mistake(); show(prof.closeFeedback ? 'One away! Three of those belong together' : 'Not a group') }
    else if (result === 'WRONG') { session.mistake(); show('Not a group') }
    else if (result === 'ALREADY_TRIED') show('You already tried that set')
    else if (result === 'INCOMPLETE') show('Select four tiles first')
  }, [puzzle, state, prof, session, show])

  const takeHint = () => {
    const { state: next, word } = L.hint(puzzle, state, prof)
    setState(next)
    if (word === null) show('No hints left')
    else { session.hint(); show(`Hint: ${word} belongs to the easiest group still hidden`) }
  }

  const left = L.mistakesLeft(state)
  const hintsLeft = L.hintsLeft(state, prof)

  return (
    <div className="space-y-3 flex flex-col items-center w-full max-w-[480px] mx-auto">
      <p className="text-white/60 text-xs uppercase tracking-widest font-semibold">Linkup</p>
      <p className="text-white/80 text-sm text-center">Pick four tiles that share a link</p>
      <p className="text-white font-semibold" aria-label={`Mistakes left: ${left} of ${L.MAX_MISTAKES}`}>
        <span aria-hidden="true">Mistakes left: {'●'.repeat(left)}{'○'.repeat(L.MAX_MISTAKES - left)}</span>
      </p>
      <Announcer message={msg} />

      {state.solved.map((i) => <Banner key={i} group={puzzle.groups[i]} missed={false} />)}
      {lost && L.unsolvedGroups(puzzle, state).map((g) => <Banner key={g.title} group={g} missed />)}

      {!finished ? (
        <>
          <div className="grid grid-cols-4 gap-2 w-full" role="group" aria-label="Tiles">
            {tiles.map((w) => (
              <Tile key={w} word={w} selected={state.selected.includes(w)} hinted={state.hinted.includes(w)}
                onToggle={() => setState((s) => L.toggle(puzzle, s, w))} />
            ))}
          </div>
          <div className="flex flex-wrap gap-2 justify-center">
            <ActionButton onClick={() => setState(L.shuffle)} label="Shuffle the remaining tiles">Shuffle</ActionButton>
            <ActionButton onClick={() => setState(L.deselectAll)} disabled={!state.selected.length} label="Deselect all tiles">Clear</ActionButton>
            <ActionButton primary onClick={submit} disabled={state.selected.length !== L.GROUP_SIZE} label="Submit the four selected tiles">Submit</ActionButton>
          </div>
          <div className="flex flex-wrap gap-2 justify-center">
            <ActionButton onClick={takeHint} disabled={hintsLeft === 0}
              label={`Hint: mark one tile of the easiest group. ${hintsLeft} hints left`}>Hint ({hintsLeft} left)</ActionButton>
            <ActionButton onClick={() => setState(L.giveUp)} label="Give up and reveal the groups">Give up</ActionButton>
          </div>
        </>
      ) : (
        <ResultCard
          success={won} score={pts} snapshot={snap}
          headline={won ? 'Linkup solved!' : state.gaveUp ? 'You gave up' : 'Out of mistakes'}
          lines={[
            `${state.solved.length} of ${L.GROUPS} groups found`,
            `${state.mistakes} mistake${state.mistakes === 1 ? '' : 's'}, ${state.hints} hint${state.hints === 1 ? '' : 's'}`,
          ]}
          shareText={L.shareText(puzzle, state)}
          onContinue={() => onAnswer(won, '')}
        />
      )}
    </div>
  )
}

function LinkupInner({ puzzle, onAnswer }) {
  const { bank, error, retry } = useBank()
  if (error) {
    return (
      <div role="alert" className="text-center space-y-3 py-6">
        <p className="text-red-300">Could not load the puzzles.</p>
        <button onClick={retry} className="min-h-[44px] px-5 rounded-xl bg-white/20 text-white">Try again</button>
      </div>
    )
  }
  if (!bank) return <p role="status" className="text-center text-white/70 py-10">Loading puzzle...</p>
  return <Board bank={bank} puzzle={puzzle} onAnswer={onAnswer} />
}

export default withWordGame(LinkupInner, 'linkup')
