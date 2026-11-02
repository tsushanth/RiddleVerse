// web/game-crowns
// Registry for the Crowns family, found by PuzzleRenderer's import.meta.glob. The game (React + SVG) is lazy-loaded so
// the main bundle only pays for this small module; the engine ships inside the same lazy chunk.
import { lazy, Suspense } from 'react'
import { normDifficulty, seedOfPuzzle } from '../spatial/engine/common.js'
import { SpatialErrorBoundary, Loading } from '../spatial/SpatialShell.jsx'

const CrownsGame = lazy(() => import('./CrownsGame.jsx'))

function CrownsPuzzle({ puzzle, onAnswer }) {
  const difficulty = normDifficulty(puzzle?.difficulty)
  const seed = seedOfPuzzle(puzzle)
  // PuzzlePage's result banner prints puzzle.answer for failed puzzles; give it a JSON value so it stays quiet.
  if (puzzle && !puzzle.answer) puzzle.answer = '{"spatial":true}'
  const finish = (success, score, label) => onAnswer(success, label || (success ? 'completed' : 'skipped'))
  return (
    <SpatialErrorBoundary puzzleType="crowns" onFail={() => finish(false, 0, 'error')}>
      <Suspense fallback={<Loading />}>
        <CrownsGame difficulty={difficulty} seed={seed} onFinished={finish} />
      </Suspense>
    </SpatialErrorBoundary>
  )
}

/** Canonical key -> component (merged into PuzzleRenderer's `renderers`). */
export const crownsRenderers = { crowns: CrownsPuzzle }

/** Type string aliases -> canonical key (merged into PuzzleRenderer's `typeMap`). */
export const crownsTypeMap = { crowns: 'crowns', Crowns: 'crowns' }
