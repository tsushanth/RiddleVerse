// Vite-only half of playI18n: each language file is its own lazy chunk (the "web" section only), so English visitors
// download nothing extra and other languages download one small file.
import en from '../../../shared/i18n/en.json'

const loaders = import.meta.glob('../../../shared/i18n/*.json', { import: 'web' })
export const SUPPORTED = Object.keys(loaders).map((p) => p.split('/').pop().replace('.json', ''))
export const EN = en.web

export async function loadWebStrings(lang) {
  if (lang === 'en') return EN
  const key = `../../../shared/i18n/${lang}.json`
  return loaders[key] ? loaders[key]() : EN
}
