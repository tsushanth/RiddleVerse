// Catalog for the public /play pages. The JSON is shared with the backend (meta injection + sitemap).
import catalog from '../../../shared/playCatalog.json'

export const PLAY_CATEGORIES = catalog.categories
export const PLAY_GAMES = catalog.games

export function getPlayGame(slug) {
  const s = String(slug || '').toLowerCase()
  return PLAY_GAMES.find((g) => g.slug === s) || null
}

export function playGameTitle(game) {
  return `Play ${game.name} Online Free, No Sign-Up | RiddleVerse`
}

export function playGameDescription(game) {
  return `${game.summary} Free in your browser, no sign-up.`
}

export const PLAY_INDEX_TITLE = 'Free Online Puzzle Games, No Sign-Up | RiddleVerse'

export function playIndexDescription() {
  return `Play ${PLAY_GAMES.length} free puzzle games in your browser with no account: Sudoku, word games, classic casual puzzles and spatial brain teasers.`
}
