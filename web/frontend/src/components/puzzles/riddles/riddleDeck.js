// "Never repeat until the tier is exhausted", per device and per tier. Port of Android RiddleDeck.kt; the store is any
// { getString(key), putString(key, value) } (localStorage in the browser, a Map in tests). A store that throws
// (private mode, quota) degrades to a deck with no memory instead of breaking the game.

export function createLocalStore(prefix = 'rv_riddles_') {
  return {
    getString(key) {
      try { return localStorage.getItem(prefix + key) } catch { return null }
    },
    putString(key, value) {
      try { localStorage.setItem(prefix + key, value) } catch { /* storage unavailable */ }
    },
  }
}

export function createDeck(store) {
  const get = (k) => { try { return store.getString(k) } catch { return null } }
  const put = (k, v) => { try { store.putString(k, v) } catch { /* ignore */ } }
  const seenSet = (tier) => new Set((get(`seen_${tier}`) || '').split(',').filter(Boolean))
  const poolOf = (tier, bank) => bank.filter((r) => r.tier === tier)

  return {
    /** Riddles of `tier` not yet handed out in the current pass over the bank. */
    remaining(tier, bank) {
      const ids = new Set(poolOf(tier, bank).map((r) => r.id))
      let n = 0
      for (const id of seenSet(tier)) if (ids.has(id)) n++
      return ids.size - n
    },
    passesCompleted(tier) {
      const n = parseInt(get(`passes_${tier}`) || '', 10)
      return Number.isFinite(n) ? n : 0
    },
    /**
     * Picks a random riddle of `tier` not handed out yet in this pass and marks it handed out. When every riddle of the
     * tier has been used a new pass starts (and the first pick is never the riddle that ended the previous pass).
     * Ids that no longer exist in `bank` (older content version) are ignored.
     */
    next(tier, bank, random = Math.random) {
      const pool = poolOf(tier, bank)
      if (pool.length === 0) throw new Error(`no riddles for tier ${tier}`)
      const ids = new Set(pool.map((r) => r.id))
      let seen = new Set([...seenSet(tier)].filter((id) => ids.has(id)))
      const last = get(`last_${tier}`)
      if (seen.size >= pool.length) {
        seen = new Set()
        put(`passes_${tier}`, String(this.passesCompleted(tier) + 1))
      }
      let candidates = pool.filter((r) => !seen.has(r.id))
      if (seen.size === 0 && pool.length > 1) candidates = candidates.filter((r) => r.id !== last)
      const pick = candidates[Math.min(candidates.length - 1, Math.floor(random() * candidates.length))]
      seen.add(pick.id)
      put(`seen_${tier}`, [...seen].join(','))
      put(`last_${tier}`, pick.id)
      return pick
    },
  }
}
