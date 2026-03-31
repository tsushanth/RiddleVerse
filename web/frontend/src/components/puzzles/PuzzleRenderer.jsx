import TriviaPuzzle from './TriviaPuzzle'
import QAPuzzle from './QAPuzzle'
import AnagramPuzzle from './AnagramPuzzle'
import WordSearchPuzzle from './WordSearchPuzzle'
import CrosswordPuzzle from './CrosswordPuzzle'
import CryptowordPuzzle from './CryptowordPuzzle'
import SynonymGroupingPuzzle from './SynonymGroupingPuzzle'
import AntonymBalloonPuzzle from './AntonymBalloonPuzzle'
import MemoryStoryPuzzle from './MemoryStoryPuzzle'
import NumberSequencePuzzle from './NumberSequencePuzzle'
import MathExpressionPuzzle from './MathExpressionPuzzle'
import SentenceTransitionsPuzzle from './SentenceTransitionsPuzzle'
import ProgressiveRevealPuzzle from './ProgressiveRevealPuzzle'
import RealOrAiPuzzle from './RealOrAiPuzzle'
import ColorTextMatchingPuzzle from './ColorTextMatchingPuzzle'
import WordPrefixPuzzle from './WordPrefixPuzzle'
import LetterSetPuzzle from './LetterSetPuzzle'
import WordSnakePuzzle from './WordSnakePuzzle'
import UniqueObjectPuzzle from './UniqueObjectPuzzle'
// Bucket 1: DB-backed types
import MemoryRetentionPuzzle from './MemoryRetentionPuzzle'
import ImagePuzzlePuzzle from './ImagePuzzlePuzzle'
import MemoryPreviousPairPuzzle from './MemoryPreviousPairPuzzle'
import MemorySequencingPuzzle from './MemorySequencingPuzzle'
import MemoryPreviousSinglePuzzle from './MemoryPreviousSinglePuzzle'
// Bucket 2: Math puzzles (client-side generated)
import NumberSumPuzzle from './NumberSumPuzzle'
import MathComparisonPuzzle from './MathComparisonPuzzle'
import MathEstimationPuzzle from './MathEstimationPuzzle'
import PercentagePuzzle from './PercentagePuzzle'
import AveragePuzzle from './AveragePuzzle'
import DivisionPuzzle from './DivisionPuzzle'
import SubtractionPuzzle from './SubtractionPuzzle'
import DiscountPricePuzzle from './DiscountPricePuzzle'
import TipBubblePuzzle from './TipBubblePuzzle'
import SubscriptionPuzzle from './SubscriptionPuzzle'
import ConversionPuzzle from './ConversionPuzzle'
// Bucket 2: Visual/Memory puzzles (client-side generated)
import ColorShapeMatchingPuzzle from './ColorShapeMatchingPuzzle'
import MemorySquaresPuzzle from './MemorySquaresPuzzle'
import TriangleDotMemoryPuzzle from './TriangleDotMemoryPuzzle'
import SymmetryPuzzle from './SymmetryPuzzle'
// Bucket 2: Interactive puzzles (client-side generated)
import ContextSwitchPuzzle from './ContextSwitchPuzzle'
import DualCardPuzzle from './DualCardPuzzle'

/**
 * Maps puzzle type string to the appropriate renderer component.
 *
 * Props passed through to each renderer:
 *   - puzzle: the full puzzleData object
 *   - onAnswer(isCorrect, userAnswer, showedAnswer?)
 *   - onSubmitToServer(guessedAnswer) — for types that use server-side checking
 *   - isChecking — whether server check is in progress
 */

// Type aliases: map various type strings to their canonical renderer key
const typeMap = {
  // Priority 1
  trivia: 'trivia',
  multiplechoice: 'trivia',
  daily: 'trivia', // daily challenges are typically trivia
  // Q&A-style (server-checked)
  qa: 'qa',
  riddle: 'qa',
  storypuzzle: 'qa',
  synonym: 'synonymgrouping',
  synonyms: 'synonymgrouping',
  antonym: 'antonymballoon',
  antonyms: 'antonymballoon',
  wordprefix: 'wordprefix',
  letterset: 'letterset',
  wordsnake: 'wordsnake',
  // Anagram
  anagram: 'anagram',
  jumble: 'anagram',
  // Grid-based
  wordsearch: 'wordsearch',
  crossword: 'crossword',
  // Priority 2
  cryptoword: 'cryptoword',
  crypto: 'cryptoword',
  synonymgrouping: 'synonymgrouping',
  synonymGrouping: 'synonymgrouping',
  antonymballoon: 'antonymballoon',
  antonymBalloon: 'antonymballoon',
  memorystory: 'memorystory',
  memoryStory: 'memorystory',
  // Dedicated memory types
  memoryretention: 'memoryretention',
  memoryRetention: 'memoryretention',
  memorypreviouspair: 'memorypreviouspair',
  memoryPreviousPair: 'memorypreviouspair',
  memorysequencing: 'memorysequencing',
  memorySequencing: 'memorysequencing',
  memoryprevioussingle: 'memoryprevioussingle',
  memoryPreviousSingle: 'memoryprevioussingle',
  // Number / math
  numbersequence: 'numbersequence',
  numberSequence: 'numbersequence',
  math: 'trivia',
  mathexpression: 'mathexpression',
  mathExpression: 'mathexpression',
  // New math types
  numbersum: 'numbersum',
  numberSum: 'numbersum',
  mathcomparison: 'mathcomparison',
  mathComparison: 'mathcomparison',
  mathestimation: 'mathestimation',
  mathEstimation: 'mathestimation',
  estimation: 'mathestimation',
  percentage: 'percentage',
  average: 'average',
  division: 'division',
  subtraction: 'subtraction',
  discountprice: 'discountprice',
  discountPrice: 'discountprice',
  tipbubble: 'tipbubble',
  tipBubble: 'tipbubble',
  subscription: 'subscription',
  conversion: 'conversion',
  // Sentence / reveal
  sentencetransitions: 'sentencetransitions',
  sentenceTransitions: 'sentencetransitions',
  progressivereveal: 'progressivereveal',
  progressiveReveal: 'progressivereveal',
  realorai: 'realorai',
  realOrAi: 'realorai',
  which_is_real: 'realorai',
  whichisreal: 'realorai',
  colortextmatching: 'colortextmatching',
  colorTextMatching: 'colortextmatching',
  // Visual / Spatial
  colorshapematching: 'colorshapematching',
  colorShapeMatching: 'colorshapematching',
  memorysquares: 'memorysquares',
  memorySquares: 'memorysquares',
  triangledotmemory: 'triangledotmemory',
  triangleDotMemory: 'triangledotmemory',
  symmetry: 'symmetry',
  // Image puzzle
  imagepuzzle: 'imagepuzzle',
  imagePuzzle: 'imagepuzzle',
  waldo: 'imagepuzzle',
  // Interactive
  contextswitch: 'contextswitch',
  contextSwitch: 'contextswitch',
  dualcard: 'dualcard',
  dualCard: 'dualcard',
  // Misc
  oddoneout: 'trivia',
  uniqueobject: 'uniqueobject',
  geography_cities: 'trivia',
  geography_countries: 'trivia',
}

const renderers = {
  trivia: TriviaPuzzle,
  qa: QAPuzzle,
  anagram: AnagramPuzzle,
  wordsearch: WordSearchPuzzle,
  crossword: CrosswordPuzzle,
  cryptoword: CryptowordPuzzle,
  synonymgrouping: SynonymGroupingPuzzle,
  antonymballoon: AntonymBalloonPuzzle,
  memorystory: MemoryStoryPuzzle,
  numbersequence: NumberSequencePuzzle,
  mathexpression: MathExpressionPuzzle,
  sentencetransitions: SentenceTransitionsPuzzle,
  progressivereveal: ProgressiveRevealPuzzle,
  realorai: RealOrAiPuzzle,
  colortextmatching: ColorTextMatchingPuzzle,
  wordprefix: WordPrefixPuzzle,
  letterset: LetterSetPuzzle,
  wordsnake: WordSnakePuzzle,
  uniqueobject: UniqueObjectPuzzle,
  // Bucket 1: DB-backed
  memoryretention: MemoryRetentionPuzzle,
  imagepuzzle: ImagePuzzlePuzzle,
  memorypreviouspair: MemoryPreviousPairPuzzle,
  memorysequencing: MemorySequencingPuzzle,
  memoryprevioussingle: MemoryPreviousSinglePuzzle,
  // Bucket 2: Math
  numbersum: NumberSumPuzzle,
  mathcomparison: MathComparisonPuzzle,
  mathestimation: MathEstimationPuzzle,
  percentage: PercentagePuzzle,
  average: AveragePuzzle,
  division: DivisionPuzzle,
  subtraction: SubtractionPuzzle,
  discountprice: DiscountPricePuzzle,
  tipbubble: TipBubblePuzzle,
  subscription: SubscriptionPuzzle,
  conversion: ConversionPuzzle,
  // Bucket 2: Visual/Memory
  colorshapematching: ColorShapeMatchingPuzzle,
  memorysquares: MemorySquaresPuzzle,
  triangledotmemory: TriangleDotMemoryPuzzle,
  symmetry: SymmetryPuzzle,
  // Bucket 2: Interactive
  contextswitch: ContextSwitchPuzzle,
  dualcard: DualCardPuzzle,
}

/**
 * Determines which renderer to use based on puzzle type and data shape.
 */
function resolveRenderer(puzzleType, puzzle) {
  // Direct lookup
  const canonical = typeMap[puzzleType]
  if (canonical && renderers[canonical]) {
    return renderers[canonical]
  }

  // Heuristic: if puzzle has options array with 2+ items, use trivia
  if (puzzle.options && puzzle.options.length >= 2) {
    return TriviaPuzzle
  }

  // Default fallback: QA
  return QAPuzzle
}

export default function PuzzleRenderer({ puzzleType, puzzle, onAnswer, onSubmitToServer, isChecking }) {
  const Component = resolveRenderer(puzzleType, puzzle)

  return (
    <Component
      puzzle={puzzle}
      onAnswer={onAnswer}
      onSubmitToServer={onSubmitToServer}
      isChecking={isChecking}
    />
  )
}

// Export for testing / direct use
export { resolveRenderer, typeMap, renderers }
