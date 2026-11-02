// --- web/game-trail ---
// Registry for Trail, picked up by import.meta.glob in PuzzleRenderer.jsx. The game code is lazy-loaded so the main bundle is unchanged.
import { lazy, Suspense } from 'react'
import { SpatialErrorBoundary, Loading } from '../spatial/SpatialShell.jsx'
import { normDifficulty, seedOfPuzzle } from '../spatial/engine/common.js'

const TrailGame = lazy(() => import('./TrailGame.jsx'))

function TrailPuzzle({ puzzle, onAnswer }) {
  const difficulty = normDifficulty(puzzle?.difficulty)
  const seed = seedOfPuzzle(puzzle)
  // PuzzlePage's result banner prints puzzle.answer for failed puzzles; give it a JSON value so it stays quiet.
  if (puzzle && !puzzle.answer) puzzle.answer = '{"spatial":true}'
  const finish = (success, score, label) => onAnswer(success, label || (success ? 'completed' : 'skipped'))
  return (
    <SpatialErrorBoundary puzzleType="trail" onFail={() => finish(false, 0, 'error')}>
      <Suspense fallback={<Loading />}>
        <TrailGame difficulty={difficulty} seed={seed} onFinished={finish} />
      </Suspense>
    </SpatialErrorBoundary>
  )
}

export const trailRenderers = { trail: TrailPuzzle }
export const trailTypeMap = { trail: 'trail' }
export const trailTypeNames = { trail: 'Trail' }
