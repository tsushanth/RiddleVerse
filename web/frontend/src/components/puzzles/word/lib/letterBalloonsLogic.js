// Letter Balloons (hangman with balloons). Mirrors LetterBalloonsLogic.kt. Word lists are original content.
import { createRng } from './prng.js'

export const CATEGORIES = {
  'Animals': ['cat', 'dog', 'horse', 'zebra', 'tiger', 'camel', 'otter', 'koala', 'panda', 'rabbit', 'monkey', 'giraffe', 'dolphin', 'penguin', 'buffalo', 'leopard', 'hedgehog', 'elephant', 'kangaroo', 'crocodile', 'chameleon', 'porcupine', 'armadillo', 'flamingo', 'squirrel', 'octopus', 'gorilla', 'cheetah', 'hamster', 'lobster'],
  'Food and Drink': ['bread', 'pasta', 'pizza', 'salad', 'apple', 'mango', 'lemon', 'honey', 'cheese', 'banana', 'burrito', 'noodles', 'pancake', 'sandwich', 'broccoli', 'pretzel', 'cinnamon', 'avocado', 'chocolate', 'spaghetti', 'pineapple', 'blueberry', 'croissant', 'lemonade', 'dumpling', 'cucumber', 'mushroom', 'cranberry', 'vinegar', 'tortilla'],
  'Countries': ['peru', 'chad', 'cuba', 'italy', 'japan', 'india', 'spain', 'kenya', 'egypt', 'norway', 'brazil', 'canada', 'mexico', 'ireland', 'germany', 'vietnam', 'morocco', 'portugal', 'thailand', 'argentina', 'australia', 'indonesia', 'venezuela', 'singapore', 'switzerland', 'madagascar', 'bangladesh', 'luxembourg', 'new zealand', 'south africa'],
  'Sports': ['golf', 'polo', 'judo', 'rugby', 'tennis', 'soccer', 'hockey', 'boxing', 'diving', 'rowing', 'archery', 'cycling', 'fencing', 'curling', 'cricket', 'karate', 'skiing', 'bowling', 'baseball', 'swimming', 'wrestling', 'volleyball', 'basketball', 'gymnastics', 'snowboarding', 'badminton', 'marathon', 'triathlon', 'skateboarding', 'weightlifting'],
  'Space': ['moon', 'mars', 'star', 'orbit', 'comet', 'venus', 'earth', 'saturn', 'galaxy', 'meteor', 'planet', 'nebula', 'rocket', 'pulsar', 'eclipse', 'neptune', 'asteroid', 'telescope', 'satellite', 'universe', 'astronaut', 'black hole', 'milky way', 'supernova', 'gravity', 'cosmos', 'jupiter', 'mercury', 'constellation', 'spacecraft'],
  'Around the House': ['door', 'lamp', 'sofa', 'table', 'chair', 'mirror', 'pillow', 'carpet', 'window', 'kitchen', 'blanket', 'curtain', 'toaster', 'bathtub', 'bookcase', 'bedroom', 'doorbell', 'staircase', 'wardrobe', 'microwave', 'dishwasher', 'television', 'refrigerator', 'bookshelf', 'chandelier', 'fireplace', 'cupboard', 'hallway', 'basement', 'balcony'],
  'Nature': ['tree', 'rain', 'river', 'cloud', 'ocean', 'storm', 'forest', 'meadow', 'desert', 'volcano', 'glacier', 'thunder', 'waterfall', 'mountain', 'rainbow', 'sunshine', 'blizzard', 'hurricane', 'wildflower', 'earthquake', 'canyon', 'island', 'valley', 'lagoon', 'tundra', 'prairie', 'jungle', 'coral reef', 'tornado', 'avalanche'],
  'Jobs': ['chef', 'pilot', 'nurse', 'baker', 'tailor', 'doctor', 'farmer', 'artist', 'writer', 'teacher', 'plumber', 'dentist', 'painter', 'lawyer', 'builder', 'musician', 'engineer', 'scientist', 'carpenter', 'librarian', 'architect', 'firefighter', 'electrician', 'photographer', 'veterinarian', 'journalist', 'accountant', 'astronomer', 'pharmacist', 'mechanic'],
  'Music': ['drum', 'harp', 'flute', 'piano', 'organ', 'tuba', 'violin', 'guitar', 'melody', 'rhythm', 'chorus', 'trumpet', 'cello', 'ukulele', 'clarinet', 'saxophone', 'orchestra', 'symphony', 'harmonica', 'trombone', 'accordion', 'keyboard', 'concert', 'lullaby', 'opera', 'harmony', 'soprano', 'bassoon', 'tambourine', 'xylophone'],
  'Travel': ['map', 'ticket', 'hotel', 'beach', 'plane', 'train', 'luggage', 'passport', 'airport', 'tourist', 'backpack', 'compass', 'voyage', 'harbor', 'suitcase', 'itinerary', 'souvenir', 'adventure', 'campsite', 'highway', 'cruise ship', 'road trip', 'departure', 'customs', 'boarding', 'journey', 'hostel', 'terminal', 'excursion', 'destination'],
  'Weather and Seasons': ['snow', 'wind', 'fog', 'frost', 'sunny', 'windy', 'foggy', 'autumn', 'winter', 'summer', 'spring', 'drizzle', 'monsoon', 'blizzard', 'humidity', 'rainfall', 'overcast', 'heatwave', 'snowflake', 'forecast', 'lightning', 'temperature', 'thunderstorm', 'sunrise', 'sunset', 'equinox', 'solstice', 'breeze', 'downpour', 'hailstorm'],
  'Colors and Shapes': ['red', 'blue', 'green', 'pink', 'circle', 'square', 'violet', 'orange', 'yellow', 'purple', 'crimson', 'triangle', 'rectangle', 'hexagon', 'pentagon', 'turquoise', 'magenta', 'diamond', 'octagon', 'cylinder', 'burgundy', 'lavender', 'scarlet', 'emerald', 'indigo', 'ellipse', 'spiral', 'crescent', 'trapezoid', 'rhombus'],
}

export function tier(word) {
  const n = word.replace(/ /g, '').length
  return n <= 6 ? 'easy' : n <= 8 ? 'medium' : 'hard'
}

export function lives(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? 8 : d === 'hard' ? 6 : 7
}

export function newGame(word, category, maxLives) {
  return { word, category, maxLives, guessed: [], hintsUsed: 0 }
}

export const wrongLetters = (g) => g.guessed.filter((c) => !g.word.includes(c)).sort()
export const livesLeft = (g) => Math.max(g.maxLives - wrongLetters(g).length, 0)
/** One entry per character: the letter, ' ' for a space, or null when still hidden. */
export const maskedLetters = (g) => [...g.word].map((c) => (c === ' ' ? ' ' : g.guessed.includes(c) ? c : null))
export const isWon = (g) => [...g.word].every((c) => c === ' ' || g.guessed.includes(c))
export const isLost = (g) => !isWon(g) && livesLeft(g) === 0
export const isOver = (g) => isWon(g) || isLost(g)

export function guess(g, c) {
  const ch = String(c).toLowerCase()
  if (isOver(g) || !/^[a-z]$/.test(ch) || g.guessed.includes(ch)) return g
  return { ...g, guessed: [...g.guessed, ch] }
}

/** Reveals one hidden letter (most frequent first, ties alphabetical). Costs score, not a balloon. */
export function hint(g) {
  if (isOver(g)) return g
  const counts = {}
  for (const c of g.word) if (c !== ' ' && !g.guessed.includes(c)) counts[c] = (counts[c] || 0) + 1
  const keys = Object.keys(counts)
  if (!keys.length) return g
  keys.sort((a, b) => counts[b] - counts[a] || (a < b ? -1 : 1))
  return { ...g, guessed: [...g.guessed, keys[0]], hintsUsed: g.hintsUsed + 1 }
}

export function score(g) {
  if (!isWon(g)) return 0
  return Math.max(50 + livesLeft(g) * 10 - g.hintsUsed * 20, 10)
}

export function generate(difficulty, seed) {
  const rng = createRng(seed)
  const cats = Object.keys(CATEGORIES)
  const category = cats[rng.int(cats.length)]
  const all = CATEGORIES[category]
  const d = String(difficulty).toLowerCase()
  const t = d === 'easy' || d === 'hard' ? d : 'medium'
  const filtered = all.filter((w) => tier(w) === t)
  const words = filtered.length ? filtered : all
  const word = words[rng.int(words.length)]
  return { game: 'letterballoons', word, category, lives: lives(difficulty), seed, difficulty }
}
