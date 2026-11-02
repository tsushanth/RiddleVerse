// web/game-linkup: registry module picked up by import.meta.glob in PuzzleRenderer.jsx.
import { lazy, Suspense } from 'react'

const LinkupPuzzle = lazy(() => import('./LinkupPuzzle.jsx'))

export const linkupTypeNames = { linkup: 'Linkup' }
export const linkupTypeMap = { linkup: 'linkup' }

function LinkupRenderer({ puzzle, onAnswer }) {
  return (
    <Suspense fallback={<p role="status" className="text-white/70 text-center py-10">Loading puzzle...</p>}>
      <LinkupPuzzle puzzle={puzzle} onAnswer={onAnswer} />
    </Suspense>
  )
}

export const linkupRenderers = { linkup: LinkupRenderer }
