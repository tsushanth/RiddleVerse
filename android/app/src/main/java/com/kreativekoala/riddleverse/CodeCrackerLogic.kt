package com.kreativekoala.riddleverse

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Feedback for one guess: [exact] = right colour in right slot, [misplaced] = right colour, wrong slot. */
data class CodeFeedback(val exact: Int, val misplaced: Int)

/** Pure rules for "Code Cracker" (colour code deduction). Colours are ints 0 until [colors]. */
object CodeCrackerRules {
    /** Standard scoring: each secret peg can match at most one guess peg. Handles repeated colours. */
    fun feedback(secret: List<Int>, guess: List<Int>): CodeFeedback {
        require(secret.size == guess.size) { "length mismatch" }
        var exact = 0
        val secretCounts = HashMap<Int, Int>()
        val guessCounts = HashMap<Int, Int>()
        for (i in secret.indices) {
            if (secret[i] == guess[i]) exact++
            else {
                secretCounts[secret[i]] = (secretCounts[secret[i]] ?: 0) + 1
                guessCounts[guess[i]] = (guessCounts[guess[i]] ?: 0) + 1
            }
        }
        var misplaced = 0
        for ((c, n) in guessCounts) misplaced += minOf(n, secretCounts[c] ?: 0)
        return CodeFeedback(exact, misplaced)
    }

    data class Config(val positions: Int, val colors: Int, val allowRepeats: Boolean, val maxTries: Int)

    fun config(difficulty: String) = when (difficulty.lowercase()) {
        "easy" -> Config(4, 5, false, 10)
        "hard" -> Config(5, 6, true, 10)
        else -> Config(4, 6, true, 10)
    }

    /** A guess that could still be the secret given all earlier feedback (used for hints / tests). */
    fun consistent(candidate: List<Int>, history: List<Pair<List<Int>, CodeFeedback>>): Boolean =
        history.all { (g, fb) -> feedback(candidate, g) == fb }

    fun score(triesUsed: Int, maxTries: Int, hintsUsed: Int): Int =
        (50 + (maxTries - triesUsed) * 12 - hintsUsed * 20).coerceAtLeast(10)

    val colorNames = listOf("Red", "Blue", "Green", "Yellow", "Purple", "Orange")
}

data class CodeCrackerPuzzle(val secret: List<Int>, val config: CodeCrackerRules.Config) {
    fun toJson(seed: Long, difficulty: String) = JSONObject().apply {
        put("game", "codecracker")
        put("secret", JSONArray(secret))
        put("positions", config.positions); put("colors", config.colors)
        put("repeats", config.allowRepeats); put("tries", config.maxTries)
        put("seed", seed); put("difficulty", difficulty)
    }

    companion object {
        fun fromJson(o: JSONObject): CodeCrackerPuzzle {
            val a = o.getJSONArray("secret")
            return CodeCrackerPuzzle(
                List(a.length()) { a.getInt(it) },
                CodeCrackerRules.Config(o.getInt("positions"), o.getInt("colors"), o.getBoolean("repeats"), o.getInt("tries"))
            )
        }
    }
}

object CodeCrackerGenerator {
    fun generate(difficulty: String, seed: Long): CodeCrackerPuzzle {
        val cfg = CodeCrackerRules.config(difficulty)
        val rnd = Random(seed)
        val secret = if (cfg.allowRepeats) List(cfg.positions) { rnd.nextInt(cfg.colors) }
        else (0 until cfg.colors).shuffled(rnd).take(cfg.positions)
        return CodeCrackerPuzzle(secret, cfg)
    }
}
