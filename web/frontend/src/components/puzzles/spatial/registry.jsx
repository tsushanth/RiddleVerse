// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Registry consumed by PuzzleRenderer.jsx / PuzzlePage.jsx. Games are lazy-loaded so the main bundle is unchanged.
import { lazy, Suspense } from 'react'
import { SpatialErrorBoundary, Loading } from './SpatialShell.jsx'
import { SPATIAL_NAMES, normDifficulty, seedOfPuzzle } from './engine/common.js'

const games = {
  pixellogic: lazy(() => import('./PixelLogicGame.jsx')),
  tubetint: lazy(() => import('./TubeTintGame.jsx')),
  piecepack: lazy(() => import('./PiecePackGame.jsx')),
  crateshift: lazy(() => import('./CrateShiftGame.jsx')),
  conduit: lazy(() => import('./ConduitGame.jsx')),
}

function makeRenderer(type) {
  const Game = games[type]
  return function SpatialPuzzle({ puzzle, onAnswer }) {
    const difficulty = normDifficulty(puzzle?.difficulty)
    const seed = seedOfPuzzle(puzzle)
    // PuzzlePage's result banner prints puzzle.answer for failed puzzles; give it a JSON value so it stays quiet.
    if (puzzle && !puzzle.answer) puzzle.answer = '{"spatial":true}'
    const finish = (success, score, label) => onAnswer(success, label || (success ? 'completed' : 'skipped'))
    return (
      <SpatialErrorBoundary puzzleType={type} onFail={() => finish(false, 0, 'error')}>
        <Suspense fallback={<Loading />}>
          <Game difficulty={difficulty} seed={seed} onFinished={finish} />
        </Suspense>
      </SpatialErrorBoundary>
    )
  }
}

export const spatialRenderers = Object.fromEntries(Object.keys(games).map((t) => [t, makeRenderer(t)]))
const camel = { pixellogic: 'pixelLogic', tubetint: 'tubeTint', piecepack: 'piecePack', crateshift: 'crateShift' }
export const spatialTypeMap = Object.fromEntries(
  Object.keys(games).flatMap((t) => [[t, t], ...(camel[t] ? [[camel[t], t]] : [])]),
)
export const spatialTypeNames = SPATIAL_NAMES
