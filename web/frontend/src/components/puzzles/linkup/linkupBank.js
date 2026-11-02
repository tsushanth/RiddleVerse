// web/game-linkup: bank parsing, validation (port of scripts/linkup/validate_linkup.py) and the per-device,
// repeat-free puzzle selector (port of LinkupSelector / LinkupProgress). Pure: storage is injected.
import { createRng, shuffled, hashSeed } from '../word/lib/prng.js'

export const BLOCK = new Set(
  `ASS ARSE DAMN HELL CRAP SEX SEXY PORN NUDE NAKED RAPE DRUG DRUGS COCAINE HEROIN WHORE SLUT BASTARD BITCH
DICK COCK PISS SHIT FUCK TITS BOOB BOOBS PENIS VAGINA KILL MURDER SUICIDE NAZI HITLER GUN GUNS BOMB TERROR`.split(/\s+/),
)

// ---------- parsing ----------

/** puzzle: { id, groups:[{tier,title,words}], redHerrings:Set<string> } */
export function parsePuzzle(o) {
  const redHerrings = new Set(o.herrings || [])
  for (const a of o.ambiguity || []) redHerrings.add(a.word)
  return {
    id: o.id,
    groups: o.groups.map((g) => ({ tier: g.tier, title: g.title, words: [...g.words] })),
    redHerrings,
  }
}

export function parseBank(data) {
  const puzzles = data.puzzles.map(parsePuzzle)
  return { puzzles, words: (p) => p.groups.flatMap((g) => g.words) }
}

// ---------- validation ----------

const WORD_RE = /^[A-Z]{3,10}$/
const ANIMALS = 'CAT DOG RAT BAT ANT APE COW EEL OWL HEN PIG RAM BEAR LAMB FOX ASP LION ELK EMU GNU YAK TOAD'.split(' ')
const COLOURS = 'RED TAN TEAL GOLD BLUE LIME JADE PLUM AQUA ROSE GREY PINK'.split(' ')
const NUMBERS = 'ONE TWO TEN SIX NINE EIGHT FOUR FIVE'.split(' ')
const BODY = 'EAR ARM LEG HIP LIP CHIN SHIN HAIR HEAD NAIL GUM SKIN FOOT HAND NECK HEART'.split(' ')
const METALS = 'TIN IRON LEAD GOLD ZINC'.split(' ')
const ROMAN = new Set('IVXLCDM')
const TOPROW = new Set('QWERTYUIOP')

const pairs = (w) => [...w].filter((c, i) => i > 0 && w[i - 1] === c).length
const allIn = (w, set) => [...w].every((c) => set.has(c))
const hides = (w, list) => list.some((a) => w.includes(a) && a !== w)
const startsWith = (w, list) => list.some((a) => w.startsWith(a) && a !== w)

/** Words that break a rule of a mechanically checkable title (same rules as the Python validator). */
export function wordplayErrors(title, words) {
  const t = title.toLowerCase()
  return words.filter((w) => {
    let ok = true
    if (t.startsWith('palindrom')) ok = w === [...w].reverse().join('')
    else if (t.startsWith('anagrams')) ok = [...w].sort().join('') === [...words[0]].sort().join('')
    else if (t.startsWith('only roman')) ok = allIn(w, ROMAN)
    else if (t.startsWith('typed on the top row')) ok = allIn(w, TOPROW)
    else if (t.startsWith('letters in alphabetical')) ok = w === [...w].sort().join('')
    else if (t === 'silent k') ok = w.startsWith('KN')
    else if (t === 'silent w') ok = w.startsWith('WR')
    else if (t === 'silent g') ok = w.includes('GN')
    else if (t === 'silent b') ok = w.includes('MB') || w.includes('BT')
    else if (t === 'silent p') ok = w.startsWith('PS') || w.startsWith('PN')
    else if (t.startsWith('start with a silent letter')) ok = ['KN', 'GN', 'WR', 'PS'].includes(w.slice(0, 2))
    else if (t.startsWith('hide an animal')) ok = hides(w, ANIMALS)
    else if (t.startsWith('hide a colour')) ok = hides(w, COLOURS)
    else if (t.startsWith('hide a number')) ok = hides(w, NUMBERS)
    else if (t.startsWith('hide a body part')) ok = hides(w, BODY)
    else if (t.startsWith('start with a metal')) ok = startsWith(w, METALS)
    else if (t.startsWith('start with a body part')) ok = startsWith(w, BODY)
    else if (t.startsWith('two pairs of double')) ok = pairs(w) >= 2
    else if (t.startsWith('spell a word backwards')) ok = w !== [...w].reverse().join('')
    return !ok
  })
}

/** fit: Map word -> Set(tiers it plausibly fits). Counts ways to split into four groups of four, one per tier. */
export function countPartitions(fit, limit = 3) {
  const words = [...fit.keys()].sort()
  const cap = { 1: 4, 2: 4, 3: 4, 4: 4 }
  let sols = 0
  const rec = (i) => {
    if (sols >= limit) return
    if (i === words.length) {
      if (Object.values(cap).every((v) => v === 0)) sols++
      return
    }
    for (const t of [...fit.get(words[i])].sort()) {
      if (cap[t] > 0) {
        cap[t]--
        rec(i + 1)
        cap[t]++
      }
    }
  }
  rec(0)
  return sols
}

/** Raw JSON bank in, { errors, warnings } out. An empty errors array means the bank is shippable. */
export function validateBank(data) {
  const errors = []
  const warnings = []
  const ids = new Set()
  const wordsets = new Map()
  const groupsets = new Map()
  for (const pz of data.puzzles || []) {
    const pid = pz.id ?? '?'
    const err = (m) => errors.push(`${pid}: ${m}`)
    if (ids.has(pid)) err('duplicate puzzle id')
    ids.add(pid)
    const groups = pz.groups || []
    if (groups.length !== 4) { err(`${groups.length} groups, need 4`); continue }
    if (JSON.stringify(groups.map((g) => g.tier).sort()) !== '[1,2,3,4]') err('tiers must be exactly 1,2,3,4')
    const titles = new Set(groups.map((g) => String(g.title).trim().toLowerCase()))
    if (titles.size !== 4) err('duplicate category titles in puzzle')
    const home = new Map()
    const allw = []
    for (const g of groups) {
      const ws = g.words || []
      if (ws.length !== 4) err(`group '${g.title}' has ${ws.length} words, need 4`)
      if (!String(g.title).trim()) err('empty title')
      for (const w of ws) {
        allw.push(w)
        if (!WORD_RE.test(w)) err(`word '${w}' must be 3-10 letters A-Z`)
        if (BLOCK.has(w)) err(`word '${w}' blocked`)
        if (titles.has(String(w).toLowerCase())) err(`word '${w}' equals a category title`)
        if (home.has(w)) err(`word '${w}' appears in more than one group (tier ${home.get(w)} and tier ${g.tier})`)
        home.set(w, g.tier)
      }
      for (const bw of wordplayErrors(String(g.title), ws)) err(`word '${bw}' breaks the rule of '${g.title}'`)
    }
    if (allw.length !== 16 || new Set(allw).size !== 16) err(`needs 16 unique words, has ${new Set(allw).size} unique of ${allw.length}`)
    const fit = new Map([...home].map(([w, t]) => [w, new Set([t])]))
    for (const a of pz.ambiguity || []) {
      if (!home.has(a.word)) { err(`ambiguity word '${a.word}' is not in the puzzle`); continue }
      for (const t of a.fits || []) {
        if (![1, 2, 3, 4].includes(t)) err(`ambiguity '${a.word}' names unknown tier ${t}`)
        else if (t === home.get(a.word)) err(`ambiguity '${a.word}' targets its own group`)
        else fit.get(a.word).add(t)
      }
    }
    if (!(pz.ambiguity || []).length) warnings.push(`${pid}: no declared red herring`)
    if (home.size === 16) {
      const n = countPartitions(fit)
      if (n !== 1) err(`${n} valid partitions under the declared ambiguity list (need exactly 1)`)
    }
    const wk = [...allw].sort().join(',')
    if (wordsets.has(wk)) err(`identical 16-word set to ${wordsets.get(wk)}`)
    wordsets.set(wk, pid)
    for (const g of groups) {
      const gk = [...(g.words || [])].sort().join(',')
      if (groupsets.has(gk)) err(`group '${g.title}' duplicates a group in ${groupsets.get(gk)}`)
      groupsets.set(gk, pid)
    }
  }
  return { errors, warnings }
}

// ---------- selection ----------

const mix = (installSeed, epoch) => hashSeed(`linkup:${installSeed}:${epoch}`)

/** Shuffled 0..n-1 for one round; the first puzzle of a round is never the last one of the previous round. */
export function permutation(installSeed, epoch, n) {
  if (!(n > 0)) throw new Error('empty bank')
  const range = Array.from({ length: n }, (_, i) => i)
  const p = shuffled(range, createRng(mix(installSeed, epoch)))
  if (epoch > 0 && n > 2) {
    const prevLast = shuffled(range, createRng(mix(installSeed, epoch - 1)))[n - 1]
    if (p[0] === prevLast) [p[0], p[1]] = [p[1], p[0]]
  }
  return p
}

/** Puzzle number `counter` of a device is permutation(seed, floor(counter/n))[counter % n]. */
export function indexFor(installSeed, n, counter) {
  if (counter < 0) throw new Error('negative counter')
  return permutation(installSeed, Math.floor(counter / n), n)[counter % n]
}

const KEY_SEED = 'linkup_install_seed'
const KEY_CURSOR = 'linkup_cursor'
const INT_MAX = 2147483647

/** Wraps a {getInt,putInt} store so a throwing one (blocked storage) degrades to in-memory. */
export function safeStore(inner) {
  const mem = new Map()
  return {
    getInt(k, d = 0) {
      try { return inner.getInt(k, d) } catch { return mem.has(k) ? mem.get(k) : d }
    },
    putInt(k, v) {
      mem.set(k, v)
      try { inner.putInt(k, v) } catch { /* in-memory only */ }
    },
  }
}

/** {getInt,putInt} over localStorage; non-integers read as the default. */
export function localStorageStore() {
  return {
    getInt(k, d = 0) {
      const v = parseInt(localStorage.getItem(k), 10)
      return Number.isFinite(v) ? v : d
    },
    putInt(k, v) { localStorage.setItem(k, String(v)) },
  }
}

/** Per-device position in the bank sequence: only a seed and a counter are stored, locally. */
export class LinkupProgress {
  constructor(store, newSeed = () => 1 + Math.floor(Math.random() * (INT_MAX - 1))) {
    this.store = store
    this.newSeed = newSeed
  }
  installSeed() {
    let s = this.store.getInt(KEY_SEED, 0)
    if (!s) { s = this.newSeed(); this.store.putInt(KEY_SEED, s) }
    return s
  }
  cursor() { return this.store.getInt(KEY_CURSOR, 0) }
  /** Index of the next puzzle for a bank of n; advances the counter. */
  take(n) {
    const c = this.cursor()
    this.store.putInt(KEY_CURSOR, c >= INT_MAX ? 0 : c + 1)
    return indexFor(this.installSeed(), n, c)
  }
}

export const nextPuzzle = (bank, progress) => bank.puzzles[progress.take(bank.puzzles.length)]

// A puzzle is consumed when it is first served, so leaving it unfinished does not replay it. The cache makes
// re-renders and remounts of the same play (same key) return the same puzzle instead of consuming another.
const served = new Map()
export function puzzleFor(bank, playKey, progress) {
  if (served.has(playKey)) return served.get(playKey)
  const p = nextPuzzle(bank, progress)
  served.set(playKey, p)
  return p
}
