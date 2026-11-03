// --- web/game-riddles --- wiring for the Riddles game: one type key, one renderer. Game code (and the 93 KB riddle
// bank) load lazily, so this module adds almost nothing to the main chunk.
import { lazy, Suspense } from 'react'

const RiddlePuzzle = lazy(() => import('./RiddlePuzzle'))

function RiddlesRenderer({ puzzle, onAnswer }) {
  return (
    <Suspense fallback={<p className="text-white/70 text-center py-10">Loading riddles...</p>}>
      <RiddlePuzzle puzzle={puzzle} onAnswer={onAnswer} />
    </Suspense>
  )
}

export const riddlesTypeMap = { riddles: 'riddles' }
export const riddlesRenderers = { riddles: RiddlesRenderer }
