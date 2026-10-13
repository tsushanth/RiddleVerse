import GridPuzzle from './GridPuzzle'

export function SudokuPuzzle(props) {
  return <GridPuzzle game="sudoku" {...props} />
}
export function BinaryGridPuzzle(props) {
  return <GridPuzzle game="binarygrid" {...props} />
}
export function CageMathPuzzle(props) {
  return <GridPuzzle game="cagemath" {...props} />
}
export function SignGridPuzzle(props) {
  return <GridPuzzle game="signgrid" {...props} />
}

/** Canonical key -> component (merged into PuzzleRenderer's `renderers`). */
export const gridRenderers = {
  sudoku: SudokuPuzzle,
  binarygrid: BinaryGridPuzzle,
  cagemath: CageMathPuzzle,
  signgrid: SignGridPuzzle,
}

/** Type string aliases -> canonical key (merged into PuzzleRenderer's `typeMap`). */
export const gridTypeMap = {
  sudoku: 'sudoku',
  binarygrid: 'binarygrid',
  binaryGrid: 'binarygrid',
  cagemath: 'cagemath',
  cageMath: 'cagemath',
  signgrid: 'signgrid',
  signGrid: 'signgrid',
}

/** Home page tiles (merged into HomePage's category list). */
export const gridHomeEntries = [
  { type: 'sudoku', name: 'Sudoku', description: '4x4, 6x6 and 9x9 grids' },
  { type: 'binarygrid', name: 'Binary Grid', description: 'Balance the 0s and 1s' },
  { type: 'cagemath', name: 'Cage Math', description: 'Cages hit their targets' },
  { type: 'signgrid', name: 'Sign Grid', description: 'Respect every < and >' },
]

export const gridTypeNames = { sudoku: 'Sudoku', binarygrid: 'Binary Grid', cagemath: 'Cage Math', signgrid: 'Sign Grid' }
