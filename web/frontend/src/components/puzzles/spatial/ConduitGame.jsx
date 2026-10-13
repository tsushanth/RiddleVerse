// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Conduit: tap (or Enter/Space on) a tile to rotate it clockwise until every lamp is powered.
import { useEffect, useMemo, useReducer, useState } from 'react'
import { generate, ConduitState, powered as poweredOf, N, E, S, W } from './engine/conduit.js'
import { SpatialFrame, ResultPanel, Loading, StdIcons, useSpatialSession, useGenerated } from './SpatialShell.jsx'

export default function ConduitGame({ difficulty, seed, onFinished }) {
  const puzzle = useGenerated(() => generate(difficulty, seed), [difficulty, seed])
  if (!puzzle) return <Loading />
  return <Board puzzle={puzzle} difficulty={difficulty} onFinished={onFinished} />
}

const bits = (m) => [m & N && 'up', m & E && 'right', m & S && 'down', m & W && 'left'].filter(Boolean).join(' and ')
const popcount = (m) => [N, E, S, W].reduce((s, b) => s + (m & b ? 1 : 0), 0)

function Tile({ mask, on, isSource, leaf, hinted, label, onClick }) {
  const col = on ? '#38bdf8' : 'rgba(255,255,255,0.5)'
  const w = on ? 22 : 14
  const seg = (x, y) => <line x1="50" y1="50" x2={x} y2={y} stroke={col} strokeWidth={w} strokeLinecap="butt" />
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="aspect-square w-full p-[1px] focus:outline-none focus-visible:ring-2 focus-visible:ring-white rounded-lg"
    >
      <svg viewBox="0 0 100 100" className="w-full h-full block" aria-hidden="true">
        <rect width="100" height="100" rx="12" fill="rgba(255,255,255,0.1)" stroke={hinted ? '#4ade80' : 'none'} strokeWidth={hinted ? 6 : 0} />
        {mask & N ? seg(50, 0) : null}
        {mask & S ? seg(50, 100) : null}
        {mask & W ? seg(0, 50) : null}
        {mask & E ? seg(100, 50) : null}
        <circle cx="50" cy="50" r={w / 2} fill={col} />
        {on && (
          <g stroke="#fff" strokeWidth="3" strokeDasharray="2 7" opacity="0.9">
            {mask & N ? <line x1="50" y1="50" x2="50" y2="0" /> : null}
            {mask & S ? <line x1="50" y1="50" x2="50" y2="100" /> : null}
            {mask & W ? <line x1="50" y1="50" x2="0" y2="50" /> : null}
            {mask & E ? <line x1="50" y1="50" x2="100" y2="50" /> : null}
          </g>
        )}
        {isSource && (
          <g>
            <circle cx="50" cy="50" r="26" fill="#FFB020" stroke="#fff" strokeWidth="3" />
            <path d="M54 32 L40 54 H50 L46 70 L60 47 H50 Z" fill="#fff" stroke="#7c4a00" strokeWidth="1.5" />
          </g>
        )}
        {leaf && !isSource && (
          <g>
            <circle cx="50" cy="50" r="22" fill={on ? '#FFEB3B' : 'rgba(255,255,255,0.18)'} stroke={col} strokeWidth="4" />
            {on ? (
              <g stroke="#FFEB3B" strokeWidth="4" strokeLinecap="round">
                <line x1="50" y1="14" x2="50" y2="22" /><line x1="86" y1="50" x2="78" y2="50" /><line x1="50" y1="86" x2="50" y2="78" /><line x1="14" y1="50" x2="22" y2="50" />
              </g>
            ) : (
              <path d="M40 40 L60 60 M60 40 L40 60" stroke={col} strokeWidth="4" strokeLinecap="round" />
            )}
          </g>
        )}
      </svg>
    </button>
  )
}

function Board({ puzzle, difficulty, onFinished }) {
  const n = puzzle.n
  const game = useMemo(() => new ConduitState(puzzle), [puzzle])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('conduit', difficulty)
  const [done, setDone] = useState(false)
  const [hintTile, setHintTile] = useState(-1)
  useEffect(() => { tel.trackMoves(game.moves) })

  const on = poweredOf(n, puzzle.source, game.masks)
  const count = on.filter(Boolean).length

  const rotate = (i) => {
    if (done) return
    setHintTile(-1)
    game.rotate(i)
    bump()
    if (game.solved()) {
      setDone(true)
      tel.complete({ score: game.score(), moves: game.moves, hintsUsed: game.hintsUsed })
    }
  }

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: !game.canUndo() || done, onClick: () => { game.undo(); tel.undo(); setHintTile(-1); bump() } },
    { icon: StdIcons.RotateCcw, label: 'Restart', disabled: done, onClick: () => { game.restart(); setHintTile(-1); bump() } },
    { icon: StdIcons.Lightbulb, label: 'Hint', disabled: done, onClick: () => { setHintTile(game.hint()); tel.hint(); bump() } },
    {
      icon: StdIcons.SkipForward, label: 'Skip', disabled: done,
      onClick: () => { tel.fail({ moves: game.moves, hintsUsed: game.hintsUsed }); onFinished(false, 0, 'skipped') },
    },
  ]

  return (
    <SpatialFrame
      type="conduit"
      difficulty={difficulty}
      status={`Powered ${count}/${n * n}`}
      actions={actions}
      message={hintTile >= 0 ? `Check row ${Math.floor(hintTile / n) + 1}, column ${(hintTile % n) + 1}` : ''}
      extra={<p className="text-xs text-white/70 text-center">Moves {game.moves} | Par {puzzle.par}. Tap a tile to rotate it.</p>}
    >
      <div
        className="grid mx-auto gap-0"
        role="group"
        aria-label={`Pipe grid, ${n} by ${n}`}
        style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))`, maxWidth: Math.min(n * 80, 520) }}
      >
        {game.masks.map((m, i) => {
          const isSource = i === puzzle.source
          const leaf = popcount(m) === 1 && !isSource
          const label =
            `Row ${Math.floor(i / n) + 1}, column ${(i % n) + 1}: ${isSource ? 'power source, ' : leaf ? 'lamp, ' : ''}pipe to ${bits(m)}` +
            (on[i] ? ', powered' : ', not powered')
          return (
            <Tile key={i} mask={m} on={on[i]} isSource={isSource} leaf={leaf} hinted={hintTile === i} label={label} onClick={() => rotate(i)} />
          )
        })}
      </div>
      {done && (
        <ResultPanel
          success
          title="All lit up!"
          detail={`${game.moves} rotations (par ${puzzle.par}). Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score(), 'completed')}
        />
      )}
    </SpatialFrame>
  )
}
