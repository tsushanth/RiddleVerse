import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import PlayShell, { PlayStoreButton } from '../components/play/PlayShell'
import { PLAY_CATEGORIES, PLAY_GAMES, PLAY_INDEX_TITLE, playIndexDescription } from '../utils/playCatalog'
import { applyPageMeta } from '../utils/pageMeta'

export default function PlayIndexPage() {
  useEffect(() => {
    applyPageMeta({ title: PLAY_INDEX_TITLE, description: playIndexDescription(), path: '/play' })
  }, [])

  return (
    <PlayShell>
      <section className="space-y-2">
        <h1 className="text-3xl font-extrabold">Free online puzzle games</h1>
        <p className="text-white/80">Pick a game and start playing right away. No account, no download, no sign-up.</p>
      </section>

      {/* web/daily-page */}
      <Link to="/play/daily" className="glass block rounded-2xl p-4 hover:bg-white/25 transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
        <span className="block font-semibold text-lg">Daily puzzle</span>
        <span className="block text-sm text-white/80 mt-1">A new free puzzle every day, the same for everyone. Keep a streak and share your time.</span>
      </Link>

      {PLAY_CATEGORIES.map((cat) => (
        <section key={cat.id} aria-labelledby={`cat-${cat.id}`} className="space-y-3">
          <h2 id={`cat-${cat.id}`} className="text-xl font-bold">{cat.name}</h2>
          <ul className="grid gap-3 sm:grid-cols-2">
            {PLAY_GAMES.filter((g) => g.category === cat.id).map((g) => (
              <li key={g.slug}>
                <Link
                  to={`/play/${g.slug}`}
                  className="glass block rounded-2xl p-4 h-full hover:bg-white/25 transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white"
                >
                  <span className="block font-semibold text-lg">{g.name}</span>
                  <span className="block text-sm text-white/80 mt-1">{g.summary}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}

      <section className="glass rounded-2xl p-5 text-center space-y-3">
        <p className="font-semibold">Want these on your phone?</p>
        <PlayStoreButton campaign="index" />
      </section>
    </PlayShell>
  )
}
