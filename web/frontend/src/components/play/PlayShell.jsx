import { Link } from 'react-router-dom'
import { trackEvent } from '../../services/firebase'
import { playStoreUrl } from '../../utils/playLinks'
import { telegramBotUrl } from '../../utils/telegramPlay'
import usePlayLang from '../../hooks/usePlayLang'

/** "Get the app" button. Link is UTM-tagged per game; the click is one GA event via the existing Firebase Analytics. */
export function PlayStoreButton({ campaign, className = '' }) {
  const { t } = usePlayLang()
  return (
    <a
      href={playStoreUrl(campaign)}
      target="_blank"
      rel="noopener"
      onClick={() => trackEvent('play_page_cta_click', { puzzle_type: campaign })}
      className={`inline-flex items-center justify-center gap-2 bg-black hover:bg-gray-900 text-white font-semibold px-5 py-3 min-h-[48px] rounded-xl border border-white/30 transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${className}`}
    >
      <span aria-hidden="true">{'\u25B6'}</span>
      <span>{t('cta_play_store')}</span>
    </a>
  )
}

/** "Play in Telegram": opens the bot (with the game when a slug is given). One GA event per click. */
export function TelegramButton({ slug, campaign, className = '' }) {
  const { t } = usePlayLang()
  return (
    <a
      href={telegramBotUrl(slug)}
      target="_blank"
      rel="noopener"
      onClick={() => trackEvent('play_page_telegram_click', { puzzle_type: campaign || slug || 'index' })}
      className={`inline-flex items-center justify-center gap-2 bg-sky-500 hover:bg-sky-400 text-white font-semibold px-5 py-3 min-h-[48px] rounded-xl transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${className}`}
    >
      <span aria-hidden="true">{'\u2708'}</span>
      <span>{t('cta_telegram')}</span>
    </a>
  )
}

/** Light chrome for the public pages: no auth, no coins, no nav that needs a login. */
export default function PlayShell({ children }) {
  const { t } = usePlayLang()
  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800 text-white">
      <header className="glass sticky top-0 z-40">
        <div className="max-w-3xl mx-auto px-4 py-3 flex items-center justify-between">
          <Link to="/play" className="flex items-center gap-2 font-bold text-lg">
            <span className="w-7 h-7 bg-pink-500 rounded-full flex items-center justify-center text-sm" aria-hidden="true">?</span>
            RIDDLEVERSE
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to="/play" className="hover:underline">{t('nav_all_games')}</Link>
            <Link to="/welcome" className="hover:underline">{t('nav_sign_in')}</Link>
          </nav>
        </div>
      </header>
      <main className="max-w-3xl mx-auto px-4 py-6 space-y-6">{children}</main>
      <footer className="max-w-3xl mx-auto px-4 pb-10 text-sm text-white/70">
        <p>{t('footer_note')}</p>
      </footer>
    </div>
  )
}
