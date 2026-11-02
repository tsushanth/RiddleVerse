package com.kreativekoala.riddleverse

import android.content.Context
import kotlin.random.Random

// --- riddle pack (feat/riddle-pack) --- "never repeat until the bank is exhausted", per install and per tier.

/** Minimal string store so the deck is JVM-testable without Android. */
interface RiddleStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class PrefsRiddleStore(context: Context) : RiddleStore {
    private val prefs = context.applicationContext.getSharedPreferences("riddle_pack", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

class RiddleDeck(private val store: RiddleStore) {

    private fun seen(tier: RiddleTier): Set<String> =
        store.getString("seen_${tier.key}").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    /** Riddles of [tier] not yet handed out in the current pass over the bank. */
    fun remaining(tier: RiddleTier, bank: List<Riddle>): Int {
        val pool = bank.filter { it.tier == tier }.map { it.id }.toSet()
        return pool.size - seen(tier).count { it in pool }
    }

    fun passesCompleted(tier: RiddleTier): Int = store.getString("passes_${tier.key}")?.toIntOrNull() ?: 0

    /**
     * Picks a random riddle of [tier] that was not handed out yet in this pass and marks it as handed out.
     * When every riddle of the tier has been used, a new pass starts (and the first pick is never the riddle that
     * ended the previous pass). Ids that no longer exist in [bank] (older content version) are ignored.
     */
    @Synchronized
    fun next(tier: RiddleTier, bank: List<Riddle>, random: Random = Random.Default): Riddle {
        val pool = bank.filter { it.tier == tier }
        require(pool.isNotEmpty()) { "no riddles for tier $tier" }
        val ids = pool.map { it.id }.toSet()
        var seen = seen(tier).filter { it in ids }.toSet()
        val last = store.getString("last_${tier.key}")
        if (seen.size >= pool.size) {
            seen = emptySet()
            store.putString("passes_${tier.key}", (passesCompleted(tier) + 1).toString())
        }
        var candidates = pool.filter { it.id !in seen }
        if (seen.isEmpty() && pool.size > 1) candidates = candidates.filter { it.id != last }
        val pick = candidates.random(random)
        store.putString("seen_${tier.key}", (seen + pick.id).joinToString(","))
        store.putString("last_${tier.key}", pick.id)
        return pick
    }
}
