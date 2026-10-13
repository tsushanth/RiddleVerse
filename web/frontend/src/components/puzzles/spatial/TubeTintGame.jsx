// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Tube Tint: liquid sort. Each tube is a real button (keyboard + screen reader friendly); layers carry glyphs.
import { useMemo, useReducer, useState, useEffect } from 'react'
import { Plus } from 'lucide-react'
import { generate, TubeTintState, isTubeDone } from './engine/tubetint.js'
import { SpatialFrame, ResultPanel, Loading, StdIcons, Glyph, colorHex, colorName, useSpatialSession, useGenerated } from './SpatialShell.jsx'

export default function TubeTintGame({ difficulty, seed, onFinished }) {
  const puzzle = useGenerated(() => generate(difficulty, seed), [difficulty, seed])
  if (!puzzle) return <Loading />
  return <Board puzzle={puzzle} difficulty={difficulty} onFinished={onFinished} />
}

const W = 40
const H = 118
const PAD = 5

function Tube({ layers, capacity, index, selected, hint, onClick }) {
  const layerH = (H - PAD * 2) / capacity
  const complete = isTubeDone(layers, capacity)
  const desc =
    `Tube ${index + 1}, ` +
    (layers.length ? `${layers.map(colorName).join(', ')} from bottom to top` : 'empty') +
    (complete ? ', complete' : '') +
    (selected ? ', selected' : '') +
    (hint ? `, hint: ${hint}` : '')
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={desc}
      aria-pressed={selected}
      className="flex-1 min-w-[44px] max-w-[84px] flex flex-col items-center justify-end focus:outline-none group"
      style={{ minHeight: 140 }}
    >
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-auto transition-transform duration-150 group-focus-visible:drop-shadow-[0_0_6px_#fff]"
        style={{ transform: selected ? 'translateY(-14px)' : 'none', maxHeight: 150 }}
        aria-hidden="true"
      >
        <clipPath id={`tt-clip-${index}`}>
          <rect x={PAD} y={PAD} width={W - PAD * 2} height={H - PAD * 2} rx={(W - PAD * 2) / 2.4} />
        </clipPath>
        <rect x="1.5" y="1.5" width={W - 3} height={H - 3} rx="14" fill="rgba(255,255,255,0.06)"
          stroke={hint ? '#4ade80' : selected ? '#fff' : 'rgba(255,255,255,0.55)'} strokeWidth={selected || hint ? 3 : 1.6} />
        <g clipPath={`url(#tt-clip-${index})`}>
          {layers.map((c, k) => {
            const y = H - PAD - (k + 1) * layerH
            return (
              <g key={k}>
                <rect x={PAD} y={y} width={W - PAD * 2} height={layerH - 0.6} fill={colorHex(c)} />
                <Glyph index={c} cx={W / 2} cy={y + layerH / 2} s={layerH * 0.5} />
              </g>
            )
          })}
        </g>
        {complete && <path d="M12 10 l5 5 l11 -11" stroke="#fff" strokeWidth="2.5" fill="none" strokeLinecap="round" transform="translate(0 -2)" opacity="0.95" />}
      </svg>
      <span className="text-[10px] text-white/60 mt-1">{index + 1}{hint ? ` ${hint}` : ''}</span>
    </button>
  )
}

function Board({ puzzle, difficulty, onFinished }) {
  const game = useMemo(() => new TubeTintState(puzzle), [puzzle])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('tubetint', difficulty)
  const [selected, setSelected] = useState(-1)
  const [hintPair, setHintPair] = useState(null)
  const [message, setMessage] = useState('')
  const [done, setDone] = useState(false)
  const tubes = game.tubes

  useEffect(() => { tel.trackMoves(game.moves) })

  const after = () => {
    bump()
    if (game.solved()) {
      setDone(true)
      tel.complete({ score: game.score(), moves: game.moves, hintsUsed: game.hintsUsed })
    }
  }

  const tap = (i) => {
    setHintPair(null)
    setMessage('')
    if (done) return
    if (selected < 0) {
      if (tubes[i].length) setSelected(i)
    } else if (selected === i) {
      setSelected(-1)
    } else if (game.pour(selected, i)) {
      setSelected(-1)
      after()
    } else {
      setMessage("Can't pour there: the top colours must match and the tube needs room.")
      setSelected(tubes[i].length ? i : -1)
    }
  }

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: !game.canUndo() || done, onClick: () => { game.undo(); tel.undo(); setSelected(-1); setHintPair(null); bump() } },
    { icon: StdIcons.RotateCcw, label: 'Restart', disabled: done, onClick: () => { game.restart(); setSelected(-1); setHintPair(null); setMessage(''); bump() } },
    { icon: Plus, label: 'Add tube', disabled: game.extraTubeAdded || done, onClick: () => { game.addTube(); setSelected(-1); bump() } },
    {
      icon: StdIcons.Lightbulb, label: 'Hint', disabled: done,
      onClick: () => {
        const h = game.hint()
        tel.hint()
        if (h) {
          setHintPair(h)
          setSelected(-1)
          setMessage(`Pour tube ${h[0] + 1} into tube ${h[1] + 1}`)
        } else setMessage('No path from here. Try Undo, Restart or Add tube.')
        bump()
      },
    },
    {
      icon: StdIcons.SkipForward, label: 'Skip', disabled: done,
      onClick: () => { tel.fail({ moves: game.moves, hintsUsed: game.hintsUsed }); onFinished(false, 0, 'skipped') },
    },
  ]

  return (
    <SpatialFrame type="tubetint" difficulty={difficulty} status={`Moves ${game.moves}`} message={message} actions={actions}>
      <div className="flex flex-wrap justify-center gap-x-1 gap-y-3 pt-4" role="group" aria-label="Tubes">
        {tubes.map((layers, i) => (
          <Tube
            key={i}
            index={i}
            layers={layers}
            capacity={game.capacity}
            selected={selected === i}
            hint={hintPair ? (hintPair[0] === i ? 'from' : hintPair[1] === i ? 'to' : '') : ''}
            onClick={() => tap(i)}
          />
        ))}
      </div>
      {done && (
        <ResultPanel
          success
          title="Sorted!"
          detail={`${game.moves} moves. Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score(), 'completed')}
        />
      )}
    </SpatialFrame>
  )
}
