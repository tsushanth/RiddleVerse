package com.kreativekoala.riddleverse

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

// --- riddle pack (feat/riddle-pack) --- registration point + pure rules for the Riddles game.
// Registered through WordPuzzleRegistry (one delimited block there), so every existing hook that already routes
// the word games (offline flag, NEW badge, tutorial, local generation, screen wrapper) also routes riddles.

object RiddlePack {
    const val TYPE = "riddles"

    /** Reuses a word-game screen name so PuzzleScreenType/PuzzleScreen need no edit; WordPuzzleScreenWrapper dispatches on the "game" key. */
    val SCREEN_NAME: String get() = PuzzleScreenType.WORD_GUESS_SCREEN.typeName

    const val PLAY_LINK =
        "https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=riddle&utm_campaign=card"

    const val MAX_STARS = 3
    const val STATS_KEY = "riddles"

    val category = QuizCategory("Riddles", "Classic riddles with hints, streaks and sharing", Icons.Default.QuestionAnswer, TYPE, "English")

    /** 3 stars with no hint, then one star less per hint, never below 1 for a solved riddle. Hints themselves are free. */
    fun stars(hintsShown: Int): Int = (MAX_STARS - hintsShown).coerceIn(1, MAX_STARS)

    /** Session points: 10 per star, plus a bonus for harder tiers. Only solved riddles score. */
    fun points(tier: RiddleTier, hintsShown: Int, solved: Boolean): Int =
        if (!solved) 0 else stars(hintsShown) * 10 + when (tier) { RiddleTier.EASY -> 0; RiddleTier.MEDIUM -> 5; RiddleTier.HARD -> 10 }

    /** Text only: the riddle and the Play link, never the answer or the hints. */
    fun shareText(context: Context, riddle: Riddle): String = context.getString(R.string.riddle_share_text, riddle.question, PLAY_LINK)

    fun createPuzzle(difficulty: String, context: Context, seed: Long): Puzzle {
        val tier = RiddleTier.fromDifficulty(difficulty)
        val riddle = try {
            RiddleDeck(PrefsRiddleStore(context)).next(tier, RiddleBank.get(context), Random(seed))
        } catch (e: Exception) {
            PuzzleEvents.error(TYPE, tier.key, "riddle bank failed: ${e.javaClass.simpleName}")
            throw e
        }
        return Puzzle(
            puzzleId = "${TYPE}_${riddle.id}_${System.currentTimeMillis()}",
            puzzleType = TYPE,
            question = riddle.toJson().toString(),
            answer = "generated",
            hint = "Type your answer. Hints are free but take away a star.",
            difficulty = tier.label,
            options = emptyList()
        )
    }

    fun tutorialContent(): SimpleTutorialContent = SimpleTutorialContent(
        "Riddles", "🧩",
        "Read the riddle and type your answer. Spelling is forgiving: capital letters, \"a\" or \"the\" and simple plurals do not matter.",
        "Hints are free, but each one takes a star off your score. Answer without hints for three stars and keep your streak going.",
        listOf(Color(0xFF6C5CE7), Color(0xFF4834B8))
    )
}
