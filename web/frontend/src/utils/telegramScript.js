// Loads Telegram's Mini App script, and only when the page was opened from Telegram (nothing third-party loads for others).
const SRC = 'https://telegram.org/js/telegram-web-app.js'

export function loadTelegramWebApp() {
  return new Promise((resolve) => {
    try {
      if (window.Telegram && window.Telegram.WebApp) return resolve(window.Telegram.WebApp)
      const s = document.createElement('script')
      s.src = SRC
      s.async = true
      s.onload = () => resolve((window.Telegram && window.Telegram.WebApp) || null)
      s.onerror = () => resolve(null)
      document.head.appendChild(s)
    } catch { resolve(null) }
  })
}
