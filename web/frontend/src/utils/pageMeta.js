// Keeps <head> in step with the current /play page after client-side navigation.
// The first load already has the right tags (the backend injects them); this only matters for SPA links.
const ORIGIN = 'https://puzzleverseai.com'

function upsert(selector, create, attr, value) {
  let el = document.head.querySelector(selector)
  if (!el) {
    el = create()
    document.head.appendChild(el)
  }
  el.setAttribute(attr, value)
}

const meta = (key, name) => () => {
  const el = document.createElement('meta')
  el.setAttribute(key, name)
  return el
}

export function applyPageMeta({ title, description, path }) {
  if (typeof document === 'undefined') return
  const url = `${ORIGIN}${path}`
  document.title = title
  upsert('meta[name="description"]', meta('name', 'description'), 'content', description)
  upsert('link[rel="canonical"]', () => Object.assign(document.createElement('link'), { rel: 'canonical' }), 'href', url)
  upsert('meta[property="og:title"]', meta('property', 'og:title'), 'content', title)
  upsert('meta[property="og:description"]', meta('property', 'og:description'), 'content', description)
  upsert('meta[property="og:url"]', meta('property', 'og:url'), 'content', url)
  upsert('meta[name="robots"]', meta('name', 'robots'), 'content', 'index, follow, max-image-preview:large')
}
