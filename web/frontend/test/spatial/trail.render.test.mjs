// Server-render smoke test for the Trail board: dots, walls, one labelled button per square, keyboard-focusable board.
import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import React from 'react'
import { renderToString } from 'react-dom/server'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
let server, G, E
before(async () => {
  server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
  G = await server.ssrLoadModule('/src/components/puzzles/trail/TrailGame.jsx')
  E = await server.ssrLoadModule('/src/components/puzzles/trail/engine.js')
})
after(async () => { await server?.close() })

test('board renders n*n labelled square buttons, every dot number, walls, and is keyboard focusable', () => {
  for (const [n, band, seed] of [[5, 0, 1], [6, 1, 2], [8, 2, 3]]) {
    const p = E.generate(n, band, seed)
    const html = renderToString(React.createElement(G.Board, { puzzle: p, difficulty: ['easy', 'medium', 'hard'][band], onFinished() {} })).replace(/<!-- -->/g, '')
    assert.equal((html.match(/<button[^>]*aria-label="Row \d+, column \d+\./g) || []).length, n * n)
    assert.match(html, /tabindex="0"/)
    assert.match(html, new RegExp(`Trail puzzle, ${n} by ${n}`))
    for (let k = 1; k <= p.waypoints.length; k++) assert.match(html, new RegExp(`>${k}</text>`))
    assert.equal((html.match(/<line x1="\d+" y1=/g) || []).length, p.wallCount)
    assert.match(html, /Dot 2 of \d+ is next/)
  }
})
