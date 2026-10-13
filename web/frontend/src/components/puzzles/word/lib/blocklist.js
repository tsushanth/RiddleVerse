// Words that must never be used as answers, targets or counted words.
// Exact match plus simple inflections. Same list as the Android app (WordPuzzleWordLists.kt).

const BLOCKED = new Set(
  (
    'ass arse arses asshole bastard bitch bitches bitchy blowjob boob boobs bollocks bugger ' +
    'butt buttocks clit cock cocks coon crap crappy cum cums cunt cunts damn dick dicks dildo ' +
    'dyke fag faggot fags fuck fucks fucker fucked fucking gook handjob hell hoe hoes homo ' +
    'horny jerk jism jizz kike kinky knob lesbo lust milf nazi negro nigga nigger nude nuded ' +
    'orgy paki penis piss pissed porn porno prick pubic pussy queer rape raped rapes rapist ' +
    'rectum retard schlong scrotum semen sex sexy shag shit shits shitty skank slut sluts ' +
    'smut spic tits titty twat vagina vulva wank wanker whore whores wop xxx anal anus ' +
    'arsehole bimbo bondage bukkake dago douche fetish gay gays genital ' +
    'genitals gigolo hooker incest molest nipple nipples nympho pedo pervert phallus porn ' +
    'pornos prostitute rimjob sodomy turd turds urinal wetback'
  )
    .split(' ')
    .filter(Boolean)
)

const SUFFIXES = ['s', 'es', 'ed', 'er', 'ing', 'y']

export function isBlocked(word) {
  const w = String(word).toLowerCase()
  if (BLOCKED.has(w)) return true
  for (const suf of SUFFIXES) {
    if (w.endsWith(suf) && w.length - suf.length >= 3 && BLOCKED.has(w.slice(0, w.length - suf.length))) return true
  }
  return false
}
