// Google Play link for the public /play pages. Kept free of imports so node tests can load it.
// Must stay in sync with web/backend/services/playPages.js (a test compares the two).
export const PLAY_STORE_BASE = 'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse'

/** campaign = game slug, or 'index' for the /play listing. */
export function playStoreUrl(campaign) {
  return `${PLAY_STORE_BASE}&utm_source=web&utm_medium=play_page&utm_campaign=${encodeURIComponent(campaign || 'index')}`
}
