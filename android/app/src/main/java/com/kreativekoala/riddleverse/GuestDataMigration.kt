package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.gson.Gson

/**
 * Moves what a guest earned on this phone onto the account they sign in to.
 *
 * Guest progress is already stored locally under the id "guest_user" (UserStatsManager), so migration is a re-key:
 *   user_puzzle_stats   guest_user_<puzzleType>                -> <uid>_<puzzleType>              (stats merged)
 *   user_global_stats   guest_user_<anything>                  -> <uid>_<anything>                (counters summed, bests kept)
 *   daily_completions   guest_user_<date>_completions          -> <email>_<date>_completions      (summed)
 *   puzzle_group_completion  completed_types__<group>          -> completed_types_<email>_<group> (union)
 * It is idempotent (the guest keys are removed once moved) and additive: an existing account that signs in on a phone
 * with guest progress ends up with both. Streaks, coins from the daily gift, score and difficulty are device-wide
 * (not keyed by user) so they simply stay.
 *
 * NOT migratable: anything that only exists on the server for a signed-in id (leaderboard points, coin balance,
 * creator games, subscription grants) because a guest never wrote any. Guest points are not back-filled into the
 * global leaderboard (the /update-score endpoint semantics are not known to be safe to replay).
 */
interface PrefStore {
    fun all(): Map<String, Any?>
    fun apply(puts: Map<String, Any?>, removes: Set<String>)
}

class SharedPrefsStore(private val prefs: SharedPreferences) : PrefStore {
    override fun all(): Map<String, Any?> = prefs.all
    override fun apply(puts: Map<String, Any?>, removes: Set<String>) {
        val e = prefs.edit()
        removes.forEach { e.remove(it) }
        puts.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Float -> e.putFloat(k, v)
            }
        }
        e.apply()
    }
}

object GuestDataMigration {
    const val GUEST_ID = "guest_user"
    private const val GROUP_PREFIX = "completed_types_"
    private val gson = Gson()

    data class Plan(val puts: Map<String, Any?>, val removes: Set<String>) {
        val moved: Int get() = removes.size
    }

    // ---- merges (pure) -------------------------------------------------------------------------------------

    fun mergeStats(a: UserPuzzleStats, b: UserPuzzleStats): UserPuzzleStats = UserPuzzleStats(
        totalPlays = a.totalPlays + b.totalPlays,
        wins = a.wins + b.wins,
        highScore = maxOf(a.highScore, b.highScore),
        totalScore = a.totalScore + b.totalScore,
        topScores = (a.topScores.filterIsInstance<Int>() + b.topScores.filterIsInstance<Int>()).filter { it > 0 }.sortedDescending().take(10),
        totalTimeSpentSeconds = a.totalTimeSpentSeconds + b.totalTimeSpentSeconds,
        lastPlayedTimestamp = maxOf(a.lastPlayedTimestamp, b.lastPlayedTimestamp),
        currentStreak = maxOf(a.currentStreak, b.currentStreak)
    )

    fun mergeGlobal(a: GlobalUserStats, b: GlobalUserStats): GlobalUserStats = GlobalUserStats(
        totalXP = a.totalXP + b.totalXP,
        totalGamesPlayed = a.totalGamesPlayed + b.totalGamesPlayed,
        totalTimePlayedSeconds = a.totalTimePlayedSeconds + b.totalTimePlayedSeconds,
        lastActiveTimestamp = maxOf(a.lastActiveTimestamp, b.lastActiveTimestamp),
        joinDate = listOf(a.joinDate, b.joinDate).filter { it > 0 }.minOrNull() ?: a.joinDate
    )

    private fun parseStats(v: Any?): UserPuzzleStats? =
        (v as? String)?.let { runCatching { gson.fromJson(it, UserPuzzleStats::class.java) }.getOrNull() }

    private fun parseGlobal(v: Any?): GlobalUserStats? =
        (v as? String)?.let { runCatching { gson.fromJson(it, GlobalUserStats::class.java) }.getOrNull() }

    private fun mergeGroupSets(a: String?, b: String?): String =
        ((a.orEmpty().split(",") + b.orEmpty().split(",")).filter { it.isNotBlank() }.toSortedSet()).joinToString(",")

    /** Combine a guest value with the account's existing value for a user_global_stats key. */
    internal fun combineGlobal(suffix: String, guest: Any?, existing: Any?): Any? {
        if (existing == null) return guest
        return when {
            suffix == "global" -> {
                val g = parseGlobal(guest); val e = parseGlobal(existing)
                if (g != null && e != null) gson.toJson(mergeGlobal(g, e)) else existing
            }
            suffix == "last_active_date" -> listOfNotNull(guest as? String, existing as? String).maxOrNull()
            suffix.startsWith("achievement_progress_") -> maxOf(guest as? Int ?: 0, existing as? Int ?: 0)
            suffix.startsWith("achievement_") -> (guest as? Boolean ?: false) || (existing as? Boolean ?: false)
            guest is Int && existing is Int -> guest + existing   // daily_/weekly_/seasonal_ counters
            else -> existing
        }
    }

    // ---- plans (pure) --------------------------------------------------------------------------------------

    /** user_puzzle_stats: one JSON blob per puzzle type. */
    fun planPuzzleStats(entries: Map<String, Any?>, toUid: String): Plan {
        val puts = linkedMapOf<String, Any?>(); val removes = linkedSetOf<String>()
        entries.filterKeys { it.startsWith("${GUEST_ID}_") }.forEach { (key, value) ->
            val type = key.removePrefix("${GUEST_ID}_")
            val target = "${toUid}_$type"
            val guest = parseStats(value)
            removes += key
            if (guest != null) {
                val existing = parseStats(entries[target])
                puts[target] = gson.toJson(if (existing != null) mergeStats(guest, existing) else guest)
            }
        }
        return Plan(puts, removes)
    }

    /** user_global_stats: JSON blob + several counter kinds, all keyed "<id>_<suffix>". */
    fun planGlobalStats(entries: Map<String, Any?>, toUid: String): Plan {
        val puts = linkedMapOf<String, Any?>(); val removes = linkedSetOf<String>()
        entries.filterKeys { it.startsWith("${GUEST_ID}_") }.forEach { (key, value) ->
            val suffix = key.removePrefix("${GUEST_ID}_")
            puts["${toUid}_$suffix"] = combineGlobal(suffix, value, entries["${toUid}_$suffix"])
            removes += key
        }
        return Plan(puts.filterValues { it != null }, removes)
    }

    /** daily_completions: "<email>_<date>_completions" counters. Needs an e-mail; accounts without one are skipped. */
    fun planDailyCompletions(entries: Map<String, Any?>, toEmail: String?): Plan {
        if (toEmail.isNullOrBlank()) return Plan(emptyMap(), emptySet())
        val puts = linkedMapOf<String, Any?>(); val removes = linkedSetOf<String>()
        entries.filterKeys { it.startsWith("${GUEST_ID}_") && it.endsWith("_completions") }.forEach { (key, value) ->
            val target = toEmail + key.removePrefix(GUEST_ID)
            val sum = (value as? Int ?: 0) + (entries[target] as? Int ?: 0)
            puts[target] = sum
            removes += key
        }
        return Plan(puts, removes)
    }

    /** puzzle_group_completion: guest used the blank user id, so the key is "completed_types__<group>". */
    fun planGroupCompletion(entries: Map<String, Any?>, toEmail: String?): Plan {
        if (toEmail.isNullOrBlank()) return Plan(emptyMap(), emptySet())
        val guestPrefix = "${GROUP_PREFIX}_"
        val puts = linkedMapOf<String, Any?>(); val removes = linkedSetOf<String>()
        entries.filterKeys { it.startsWith(guestPrefix) }.forEach { (key, value) ->
            val group = key.removePrefix(guestPrefix)
            if (group.isBlank()) return@forEach
            val target = "$GROUP_PREFIX${toEmail}_$group"
            puts[target] = mergeGroupSets(value as? String, entries[target] as? String)
            removes += key
        }
        return Plan(puts, removes)
    }

    // ---- Android entry point -------------------------------------------------------------------------------

    /** Safe to call on every sign-in; it is a no-op once the guest keys are gone. Never throws. */
    fun migrateOnSignIn(context: Context, user: FirebaseUser?) {
        if (user == null || user.isAnonymous) return
        try {
            val app = context.applicationContext
            fun store(name: String) = SharedPrefsStore(app.getSharedPreferences(name, Context.MODE_PRIVATE))
            val stats = store("user_puzzle_stats"); val global = store("user_global_stats")
            val daily = store("daily_completions"); val groups = store("puzzle_group_completion")

            val p1 = planPuzzleStats(stats.all(), user.uid)
            val p2 = planGlobalStats(global.all(), user.uid)
            val p3 = planDailyCompletions(daily.all(), user.email)
            val p4 = planGroupCompletion(groups.all(), user.email)

            stats.apply(p1.puts, p1.removes); global.apply(p2.puts, p2.removes)
            daily.apply(p3.puts, p3.removes); groups.apply(p4.puts, p4.removes)

            if (p1.moved + p2.moved + p3.moved + p4.moved > 0) {
                Funnel.log(FunnelStep.GUEST_PROGRESS_MIGRATED, mapOf("puzzle_types" to p1.moved, "counters" to p2.moved + p3.moved))
                Log.d("GuestDataMigration", "Moved guest progress to account: stats=${p1.moved} global=${p2.moved} daily=${p3.moved} groups=${p4.moved}")
            }
            migrateTopics(app, user.email)
        } catch (e: Exception) {
            Log.e("GuestDataMigration", "Guest progress migration failed (local data left in place)", e)
        }
    }

    // Preferred topics: guests keep them on the phone only (PreferredTopicsActivity); copy to the account if it has none.
    const val TOPICS_PREFS = "guest_topics"
    const val TOPICS_KEY = "topics"

    private fun migrateTopics(app: Context, email: String?) {
        val prefs = app.getSharedPreferences(TOPICS_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(TOPICS_KEY, null)
        if (raw.isNullOrBlank() || email.isNullOrBlank()) return
        val topics = raw.split("\n").filter { it.isNotBlank() }
        val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("user_topics").document(email)
        doc.get().addOnSuccessListener { snap ->
            if (!snap.exists() || (snap.get("topics") as? List<*>).isNullOrEmpty()) {
                doc.set(mapOf("topics" to topics)).addOnSuccessListener { prefs.edit().remove(TOPICS_KEY).apply() }
            } else {
                prefs.edit().remove(TOPICS_KEY).apply() // account already has topics; theirs win
            }
        }
    }
}
