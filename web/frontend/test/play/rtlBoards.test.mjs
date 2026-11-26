// Right-to-left pages (/play in Arabic, Persian, Urdu, Hebrew) set dir=rtl on the document. Boards that place things with
// physical left/right, and English words, must opt out or Sign Grid signs land between the wrong cells.
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (p) => readFileSync(new URL(`../../src/components/puzzles/${p}`, import.meta.url), 'utf8')

test('grid board stays left to right', () => {
  assert.match(read('grid/GridBoard.jsx'), /<div\s+dir="ltr"\s+role="group"\s+aria-label=\{`\$\{spec\.game\} board/)
})

test('Word Hive current word stays left to right', () => {
  assert.match(read('word/WordHivePuzzle.jsx'), /<div dir="ltr"[^>]*aria-label=\{`Current word /)
})
