// Guards the PuzzleRenderer registry: the glob-based merge must resolve every puzzle type to exactly the
// component the old hand-written Object.assign blocks did. Runs the real modules through vite's SSR loader
// (needed for import.meta.glob and JSX). registry-snapshot.json was captured from the pre-refactor code.
import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..', '..')
const snapshot = JSON.parse(fs.readFileSync(path.join(here, 'registry-snapshot.json'), 'utf8'))
const catalog = JSON.parse(fs.readFileSync(path.resolve(root, '..', 'shared', 'playCatalog.json'), 'utf8'))
const nameOf = (c) => c.displayName || c.name || String(c.$$typeof)

let server, R, families
before(async () => {
  server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
  R = await server.ssrLoadModule('/src/components/puzzles/PuzzleRenderer.jsx')
  const g = await server.ssrLoadModule('/src/components/puzzles/grid/gridRegistry.jsx')
  const c = await server.ssrLoadModule('/src/components/puzzles/classic/classicRegistry.jsx')
  const s = await server.ssrLoadModule('/src/components/puzzles/spatial/registry.jsx')
  const w = await server.ssrLoadModule('/src/components/puzzles/word/wordPuzzleRegistry.js')
  families = [
    [g.gridTypeMap, g.gridRenderers], [c.classicTypeMap, c.classicRenderers],
    [s.spatialTypeMap, s.spatialRenderers], [w.wordPuzzleTypeMap, w.wordPuzzleRenderers],
  ]
})
after(async () => { await server?.close() })

// web/game-riddles: games added after the refactor (catalog slugs) are allowed on top of the snapshot; every snapshot
// entry must still be identical, and anything extra must be a catalog game (so a stray alias still fails).
const catalogSlugs = new Set(catalog.games.map((g) => g.slug))
const sortObj = (o) => Object.fromEntries(Object.entries(o).sort(([a], [b]) => (a < b ? -1 : 1)))

test('typeMap equals the pre-refactor snapshot (every alias, same canonical key); extras are only catalog games', () => {
  const now = sortObj(R.typeMap)
  const base = Object.fromEntries(Object.entries(now).filter(([k]) => k in snapshot.typeMap))
  assert.deepEqual(base, snapshot.typeMap)
  for (const [k, v] of Object.entries(now)) if (!(k in snapshot.typeMap)) assert.ok(catalogSlugs.has(k) && catalogSlugs.has(v), `unexpected typeMap entry ${k} -> ${v}`)
})

test('renderers: same canonical keys, same component names as the pre-refactor snapshot; extras are only catalog games', () => {
  const now = sortObj(Object.fromEntries(Object.entries(R.renderers).map(([k, v]) => [k, nameOf(v)])))
  const base = Object.fromEntries(Object.entries(now).filter(([k]) => k in snapshot.renderers))
  assert.deepEqual(base, snapshot.renderers)
  for (const k of Object.keys(now)) if (!(k in snapshot.renderers)) assert.ok(catalogSlugs.has(k), `unexpected renderer ${k}`)
})

test('every family renderer is the very same component object the family registry exports', () => {
  for (const [, renderers] of families) {
    for (const [key, comp] of Object.entries(renderers)) assert.equal(R.renderers[key], comp, key)
  }
})

test('every registered alias resolves to the snapshot renderer; unknown types fall back to QA / Trivia', () => {
  for (const [alias, canonical] of Object.entries(snapshot.typeMap)) {
    assert.equal(R.resolveRenderer(alias, {}), R.renderers[canonical], alias)
    assert.equal(nameOf(R.resolveRenderer(alias, {})), snapshot.renderers[canonical], alias)
  }
  assert.equal(nameOf(R.resolveRenderer('no-such-type', {})), 'QAPuzzle')
  assert.equal(nameOf(R.resolveRenderer('no-such-type', { options: ['a', 'b'] })), 'TriviaPuzzle')
})

test('family registries do not collide with each other (a collision would make order matter)', () => {
  const seenType = new Map()
  const seenRenderer = new Map()
  for (const [i, [typeMap, renderers]] of families.entries()) {
    for (const k of Object.keys(typeMap)) { assert.ok(!seenType.has(k) || seenType.get(k) === i, `alias ${k} in two families`); seenType.set(k, i) }
    for (const k of Object.keys(renderers)) { assert.ok(!seenRenderer.has(k), `renderer ${k} in two families`); seenRenderer.set(k, i) }
  }
})

test('mergeRegistries: merges *TypeMap / *Renderers exports from every module, ignores other exports', () => {
  const tm = { a: 'a' }
  const rn = { a: 1 }
  R.mergeRegistries({
    './z/zRegistry.js': { zTypeMap: { z: 'z', Z: 'z' }, zRenderers: { z: 2 }, zHomeEntries: [1] },
    './b/registry.jsx': { bTypeMap: { b: 'b' }, bRenderers: { b: 3 }, isB: () => true },
  }, tm, rn)
  assert.deepEqual(tm, { a: 'a', b: 'b', z: 'z', Z: 'z' })
  assert.deepEqual(rn, { a: 1, b: 3, z: 2 })
})

test('every game in the public play catalog resolves to a registered web renderer', () => {
  for (const g of catalog.games) {
    const canonical = R.typeMap[g.slug]
    assert.ok(canonical, `${g.slug}: no typeMap entry (add a registry module for it)`)
    assert.ok(R.renderers[canonical], `${g.slug}: typeMap points at ${canonical} but no renderer is registered`)
  }
})
