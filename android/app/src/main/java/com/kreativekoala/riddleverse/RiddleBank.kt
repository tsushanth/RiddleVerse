package com.kreativekoala.riddleverse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// --- riddle pack (feat/riddle-pack) --- content model + loader for assets/riddles/riddles_v1.json
// The file is built from scripts/riddles/*.txt by scripts/riddles/build_riddles.py and checked by
// scripts/riddles/validate_riddles.py and RiddleBankTest. Content is original or traditional/folk riddles.

enum class RiddleTier(val key: String, val label: String) {
    EASY("easy", "Easy"), MEDIUM("medium", "Medium"), HARD("hard", "Hard");

    companion object {
        fun fromKey(key: String?): RiddleTier? = values().firstOrNull { it.key == key?.lowercase(java.util.Locale.ROOT) }

        /** Maps the app's difficulty names (Tutorial, Beginner, Easy+, Medium-, Hard+, Expert ...) onto the three riddle tiers. */
        fun fromDifficulty(difficulty: String?): RiddleTier {
            val d = difficulty?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
            return when {
                d.startsWith("easy") || d.startsWith("beginner") || d.startsWith("tutorial") -> EASY
                d.startsWith("hard") || d.startsWith("expert") || d.startsWith("master") || d.startsWith("extreme") -> HARD
                else -> MEDIUM
            }
        }
    }
}

data class Riddle(
    val id: String,
    val tier: RiddleTier,
    val question: String,
    val answer: String,
    val alternates: List<String>,
    /** Exactly three steps: vague, pointed, first letter. */
    val hints: List<String>,
    val explanation: String
) {
    /** Every string the matcher accepts. */
    val acceptedAnswers: List<String> get() = listOf(answer) + alternates

    /** Answer as shown to the player ("deck of cards" -> "Deck of cards"). */
    val displayAnswer: String get() = answer.replaceFirstChar { it.uppercaseChar() }

    fun toJson(): JSONObject = JSONObject()
        .put("game", RiddlePack.TYPE).put("id", id).put("tier", tier.key).put("q", question).put("a", answer)
        .put("alt", JSONArray(alternates)).put("hints", JSONArray(hints)).put("why", explanation)

    companion object {
        /** Throws if a required field is missing or malformed; the loader skips such entries. */
        fun fromJson(o: JSONObject): Riddle {
            val tier = RiddleTier.fromKey(o.getString("tier")) ?: throw IllegalArgumentException("tier")
            fun list(name: String): List<String> {
                val a = o.optJSONArray(name) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            val hints = list("hints")
            require(hints.size == 3) { "hints" }
            return Riddle(o.getString("id"), tier, o.getString("q"), o.getString("a"), list("alt"), hints, o.getString("why"))
        }
    }
}

object RiddleBank {
    const val ASSET = "riddles/riddles_v1.json"

    @Volatile private var cache: List<Riddle>? = null

    fun parse(json: String): List<Riddle> {
        val arr = JSONObject(json).getJSONArray("riddles")
        return (0 until arr.length()).mapNotNull { i -> try { Riddle.fromJson(arr.getJSONObject(i)) } catch (_: Exception) { null } }
    }

    fun get(context: Context): List<Riddle> = cache ?: synchronized(this) {
        cache ?: context.applicationContext.assets.open(ASSET).use { s -> parse(s.bufferedReader().readText()) }
            .also { check(it.isNotEmpty()) { "riddle bank is empty" }; cache = it }
    }
}
