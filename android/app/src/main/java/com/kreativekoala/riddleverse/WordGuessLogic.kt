package com.kreativekoala.riddleverse

import org.json.JSONObject
import java.util.Random

/** Per-letter feedback. Order matters: later values beat earlier ones on the keyboard. */
enum class LetterState { UNKNOWN, ABSENT, PRESENT, CORRECT }

/** Pure rules for "Word Guess" (five-letter word guessing, six tries). No Android dependencies. */
object WordGuessRules {
    const val WORD_LENGTH = 5
    const val MAX_TRIES = 6

    /**
     * Two-pass feedback: exact matches are claimed first, then each remaining answer letter can
     * satisfy at most ONE misplaced guess letter (left to right). This is what makes duplicate
     * letters behave correctly (e.g. guess "LLAMA" vs answer "ALLOY").
     */
    fun evaluate(guess: String, answer: String): List<LetterState> {
        require(guess.length == answer.length) { "length mismatch" }
        val g = guess.lowercase()
        val a = answer.lowercase()
        val result = MutableList(g.length) { LetterState.ABSENT }
        val remaining = HashMap<Char, Int>()
        for (i in g.indices) {
            if (g[i] == a[i]) result[i] = LetterState.CORRECT
            else remaining[a[i]] = (remaining[a[i]] ?: 0) + 1
        }
        for (i in g.indices) {
            if (result[i] == LetterState.CORRECT) continue
            val left = remaining[g[i]] ?: 0
            if (left > 0) {
                result[i] = LetterState.PRESENT
                remaining[g[i]] = left - 1
            }
        }
        return result
    }

    /** Best known state of each letter across all guesses (for coloring the on-screen keyboard). */
    fun keyboardStates(guesses: List<String>, answer: String): Map<Char, LetterState> {
        val out = HashMap<Char, LetterState>()
        for (guess in guesses) {
            val fb = evaluate(guess, answer)
            for (i in guess.indices) {
                val c = guess[i].lowercaseChar()
                val old = out[c] ?: LetterState.UNKNOWN
                if (fb[i].ordinal > old.ordinal) out[c] = fb[i]
            }
        }
        return out
    }

    /**
     * Hard mode: every revealed hint must be used. Returns null when [candidate] is legal,
     * otherwise a short message. Green letters must stay in place; yellow/green letters must
     * appear at least as many times as they were revealed in a single earlier guess.
     */
    fun hardModeViolation(previous: List<String>, answer: String, candidate: String): String? {
        val cand = candidate.lowercase()
        for (prev in previous) {
            val fb = evaluate(prev, answer)
            for (i in prev.indices) {
                if (fb[i] == LetterState.CORRECT && cand[i] != prev[i].lowercaseChar()) {
                    return "Letter ${i + 1} must be ${prev[i].uppercaseChar()}"
                }
            }
            val needed = HashMap<Char, Int>()
            for (i in prev.indices) {
                if (fb[i] != LetterState.ABSENT) {
                    val c = prev[i].lowercaseChar()
                    needed[c] = (needed[c] ?: 0) + 1
                }
            }
            for ((c, n) in needed) {
                if (cand.count { it == c } < n) return "Guess must contain ${c.uppercaseChar()}"
            }
        }
        return null
    }

    /** Spoiler-free share text (emoji grid, no letters). */
    fun shareText(guesses: List<String>, answer: String, won: Boolean, hard: Boolean): String {
        val score = if (won) "${guesses.size}/$MAX_TRIES" else "X/$MAX_TRIES"
        val sb = StringBuilder("RiddleVerse Word Guess $score${if (hard) "*" else ""}\n\n")
        for (g in guesses) {
            for (s in evaluate(g, answer)) {
                sb.append(when (s) {
                    LetterState.CORRECT -> "🟩"
                    LetterState.PRESENT -> "🟨"
                    else -> "⬛"
                })
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    fun score(triesUsed: Int, hintUsed: Boolean, hard: Boolean): Int {
        val base = 60 + (MAX_TRIES - triesUsed) * 20 + if (hard) 20 else 0
        return (if (hintUsed) base - 30 else base).coerceAtLeast(10)
    }
}

enum class GuessStatus { PLAYING, WON, LOST }

sealed class SubmitOutcome {
    object Accepted : SubmitOutcome()
    object TooShort : SubmitOutcome()
    object NotInList : SubmitOutcome()
    data class HardMode(val message: String) : SubmitOutcome()
    object GameOver : SubmitOutcome()
}

/** Immutable game state; every action returns a new state, which keeps it trivially savable. */
data class WordGuessGame(
    val answer: String,
    val hard: Boolean,
    val guesses: List<String> = emptyList(),
    val current: String = "",
    val hintUsed: Boolean = false
) {
    val status: GuessStatus
        get() = when {
            guesses.lastOrNull() == answer -> GuessStatus.WON
            guesses.size >= WordGuessRules.MAX_TRIES -> GuessStatus.LOST
            else -> GuessStatus.PLAYING
        }

    fun type(c: Char): WordGuessGame =
        if (status != GuessStatus.PLAYING || current.length >= WordGuessRules.WORD_LENGTH || c !in 'a'..'z') this
        else copy(current = current + c)

    fun backspace(): WordGuessGame =
        if (status != GuessStatus.PLAYING || current.isEmpty()) this else copy(current = current.dropLast(1))

    fun submit(isValid: (String) -> Boolean): Pair<WordGuessGame, SubmitOutcome> {
        if (status != GuessStatus.PLAYING) return this to SubmitOutcome.GameOver
        if (current.length < WordGuessRules.WORD_LENGTH) return this to SubmitOutcome.TooShort
        if (!isValid(current)) return this to SubmitOutcome.NotInList
        if (hard) {
            WordGuessRules.hardModeViolation(guesses, answer, current)?.let {
                return this to SubmitOutcome.HardMode(it)
            }
        }
        return copy(guesses = guesses + current, current = "") to SubmitOutcome.Accepted
    }

    /** Index (0-based) of the first answer letter not yet placed correctly, or -1. */
    fun hintPosition(): Int {
        val solved = BooleanArray(answer.length)
        for (g in guesses) for (i in g.indices) if (g[i] == answer[i]) solved[i] = true
        return solved.indexOfFirst { !it }
    }
}

object WordGuessGenerator {
    /** Difficulty -> (answer pool rank window, hard mode forced). */
    fun generate(lists: WordPuzzleLists, difficulty: String, seed: Long): JSONObject {
        val d = difficulty.lowercase()
        val pool = when (d) {
            "easy" -> lists.answerPool(0, 700)
            "hard" -> lists.answerPool(700, 1700)
            else -> lists.answerPool(0, 1400)
        }
        val answer = pool[Random(seed).nextInt(pool.size)]
        return JSONObject().apply {
            put("game", "wordguess")
            put("answer", answer)
            put("hard", d == "hard")
            put("seed", seed)
            put("difficulty", difficulty)
        }
    }
}
