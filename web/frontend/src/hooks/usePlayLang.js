import { useEffect, useMemo, useState } from 'react'
import { pickLang, format, isRtl } from '../utils/playI18n'
import { SUPPORTED, EN, loadWebStrings } from '../utils/playI18nLoader'

/** t('cta_telegram') in the visitor's language (?lang= override, else the browser language), English until the file arrives. */
export default function usePlayLang() {
  const lang = useMemo(() => {
    let q = null
    try { q = new URLSearchParams(window.location.search).get('lang') } catch { /* ignore */ }
    const nav = typeof navigator !== 'undefined' ? (navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language]) : []
    return pickLang([q, ...nav], SUPPORTED)
  }, [])
  const [table, setTable] = useState(EN)
  useEffect(() => {
    let live = true
    loadWebStrings(lang).then((t) => { if (live) setTable(t) }).catch(() => {})
    return () => { live = false }
  }, [lang])
  useEffect(() => {
    try { document.documentElement.lang = lang; document.documentElement.dir = isRtl(lang) ? 'rtl' : 'ltr' } catch { /* ignore */ }
  }, [lang])
  const t = (key, params) => format(table, EN, key, params)
  return { lang, t }
}
