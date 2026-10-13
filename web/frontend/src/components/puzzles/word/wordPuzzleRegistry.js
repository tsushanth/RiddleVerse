// Single registration point for the five word games (same type keys as Android WordPuzzleRegistry.kt).
import WordGuessPuzzle from './WordGuessPuzzle.jsx'
import WordLadderPuzzle from './WordLadderPuzzle.jsx'
import LetterBalloonsPuzzle from './LetterBalloonsPuzzle.jsx'
import WordHivePuzzle from './WordHivePuzzle.jsx'
import CodeCrackerPuzzle from './CodeCrackerPuzzle.jsx'

export const WORD_PUZZLE_TYPES = ['wordguess', 'wordladder', 'letterballoons', 'wordhive', 'codecracker']

export const wordPuzzleNames = {
  wordguess: 'Word Guess',
  wordladder: 'Word Ladder',
  letterballoons: 'Letter Balloons',
  wordhive: 'Word Hive',
  codecracker: 'Code Cracker',
}

export const wordPuzzleTypeMap = Object.fromEntries(WORD_PUZZLE_TYPES.map((t) => [t, t]))

export const wordPuzzleRenderers = {
  wordguess: WordGuessPuzzle,
  wordladder: WordLadderPuzzle,
  letterballoons: LetterBalloonsPuzzle,
  wordhive: WordHivePuzzle,
  codecracker: CodeCrackerPuzzle,
}
