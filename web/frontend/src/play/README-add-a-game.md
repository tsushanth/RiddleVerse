# Add a game

One JSON entry plus one frontend registry module. Do not edit `PuzzleRenderer.jsx`, `playPages.js` or any test count.

1. `web/shared/playCatalog.json`: append one object to `games[]` (`slug`, `name`, `category`, `summary` under 120 ASCII chars, `description` of 2-3 sentences, `howToPlay` with 3+ steps, `tip`). `slug` is lowercase letters only and must equal the puzzle type key. Add to `categories[]` only if you need a new category.
2. `web/frontend/src/components/puzzles/<family>/registry.jsx` (or `<name>Registry.jsx`): export `<x>TypeMap` (alias -> canonical key, including the slug) and `<x>Renderers` (canonical key -> component). `PuzzleRenderer.jsx` finds it with `import.meta.glob`. Use `React.lazy` inside the module for heavy game code. Keys must be unique across all families.
3. Verify: `cd web/frontend && npm test && npx vite build`, and `cd web/backend && npm test`. The registry test fails if a catalog slug has no renderer; the backend test fails if a catalog entry lacks a route, sitemap URL, meta or how-to text.
