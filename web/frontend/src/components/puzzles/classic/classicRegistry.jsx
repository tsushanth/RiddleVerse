// --- web classic puzzles (feat/web-puzzles-classic) --- wiring data: renderer + type-map entries, names, home category
import { lazy, Suspense } from 'react'
import { Merge, Grid3X3, Lightbulb, Flag, Layers } from 'lucide-react'
import { ClassicErrorBoundary } from './ClassicShell'
import { CLASSIC_NAMES, CLASSIC_TYPES } from './common'

const LAZY = {
  tilemerge: lazy(() => import('./TileMergePuzzle')),
  slidetiles: lazy(() => import('./SlideTilesPuzzle')),
  lightflip: lazy(() => import('./LightFlipPuzzle')),
  safesweep: lazy(() => import('./SafeSweepPuzzle')),
  disctower: lazy(() => import('./DiscTowerPuzzle')),
}

function make(type) {
  const Game = LAZY[type]
  return function ClassicRenderer({ puzzle, onAnswer }) {
    return (
      <ClassicErrorBoundary puzzleType={type}>
        <Suspense fallback={<p className="text-white/70 text-center py-10">Loading puzzle...</p>}>
          <Game puzzle={puzzle} onAnswer={onAnswer} />
        </Suspense>
      </ClassicErrorBoundary>
    )
  }
}

export const classicRenderers = Object.fromEntries(CLASSIC_TYPES.map((t) => [t, make(t)]))
export const classicTypeMap = Object.fromEntries(CLASSIC_TYPES.map((t) => [t, t]))
export const classicTypeNames = CLASSIC_NAMES

export const classicCategory = {
  id: 'classic',
  title: 'Classic Casual',
  puzzles: [
    { type: 'tilemerge', name: 'Tile Merge', icon: Merge, color: '#f97316', description: 'Merge tiles to the goal' },
    { type: 'slidetiles', name: 'Slide Tiles', icon: Grid3X3, color: '#3b82f6', description: 'Slide numbers into order' },
    { type: 'lightflip', name: 'Light Flip', icon: Lightbulb, color: '#eab308', description: 'Switch every light off' },
    { type: 'safesweep', name: 'Safe Sweep', icon: Flag, color: '#22c55e', description: 'Clear the minefield' },
    { type: 'disctower', name: 'Disc Tower', icon: Layers, color: '#a855f7', description: 'Move the tower' },
  ],
}

export { isClassicType } from './common'
