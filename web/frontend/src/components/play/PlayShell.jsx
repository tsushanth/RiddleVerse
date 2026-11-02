import { Link } from 'react-router-dom'
import { trackEvent } from '../../services/firebase'
import { playStoreUrl } from '../../utils/playLinks'

/** "Get the app" button. Link is UTM-tagged per game; the click is one GA event via the existing Firebase Analytics. */
export function PlayStoreButton({ campaign, className = '' }) {
  return (
    <a
      href={playStoreUrl(campaign)}
      target="_blank"
      rel="noopener"
      onClick={() => trackEvent('play_page_cta_click', { puzzle_type: campaign })}
      className={`inline-flex items-center justify-center gap-2 bg-black hover:bg-gray-900 text-white font-semibold px-5 py-3 min-h-[48px] rounded-xl border border-white/30 transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${className}`}
    >
      <span aria-hidden="true">{'▶'}</span>
      <span>Get it on Google Play</span>
    </a>
  )
}

/** Light chrome for the public pages: no auth, no coins, no nav that needs a login. */
export default function PlayShell({ children }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800 text-white">
      <header className="glass sticky top-0 z-40">
        <div className="max-w-3xl mx-auto px-4 py-3 flex items-center justify-between">
          <Link to="/play" className="flex items-center gap-2 font-bold text-lg">
            <span className="w-7 h-7 bg-pink-500 rounded-full flex items-center justify-center text-sm" aria-hidden="true">?</span>
            RIDDLEVERSE
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to="/play" className="hover:underline">All games</Link>
            <Link to="/welcome" className="hover:underline">Sign in</Link>
          </nav>
        </div>
      </header>
      <main className="max-w-3xl mx-auto px-4 py-6 space-y-6">{children}</main>
      <footer className="max-w-3xl mx-auto px-4 pb-10 text-sm text-white/70">
        <p>RiddleVerse puzzles are free to play in your browser. No account needed.</p>
      </footer>
    </div>
  )
}
