package com.kreativekoala.riddleverse

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import kotlin.random.Random

// --- word puzzles (feat/puzzles-word) --- single registration point for the five word games.
// Shared files (PuzzleScreenTypes, PuzzleScreen, LocalPuzzleGenerator, StartPuzzleScreen, QuizCategories,
// HomeActivity, TutorialPreferences, HardCodedPuzzleGroups) each get one tiny hunk that calls into this object.

object WordPuzzleRegistry {
    const val WORD_GUESS = "wordguess"
    const val WORD_LADDER = "wordladder"
    const val LETTER_BALLOONS = "letterballoons"
    const val WORD_HIVE = "wordhive"
    const val CODE_CRACKER = "codecracker"

    /** Puzzle type keys (Puzzle.puzzleType / QuizCategory.category). */
    val TYPES: Set<String> = setOf(WORD_GUESS, WORD_LADDER, LETTER_BALLOONS, WORD_HIVE, CODE_CRACKER)

    fun isWordPuzzleType(type: String?): Boolean = type != null && type.lowercase() in TYPES

    fun screenTypeName(type: String): String = when (type.lowercase()) {
        WORD_GUESS -> "Word Guess Screen"
        WORD_LADDER -> "Word Ladder Screen"
        LETTER_BALLOONS -> "Letter Balloons Screen"
        WORD_HIVE -> "Word Hive Screen"
        CODE_CRACKER -> "Code Cracker Screen"
        else -> "Default"
    }

    fun screenOptions(type: String): List<String> = listOf(screenTypeName(type))

    val quizCategories: List<QuizCategory> = listOf(
        QuizCategory("Word Guess", "Guess the 5-letter word in 6 tries", Icons.Default.GridView, WORD_GUESS, "English"),
        QuizCategory("Word Ladder", "Change one letter at a time", Icons.Default.Link, WORD_LADDER, "English"),
        QuizCategory("Letter Balloons", "Guess the word before the balloons pop", Icons.Default.TextFields, LETTER_BALLOONS, "English"),
        QuizCategory("Word Hive", "Build words from seven letters", Icons.Default.Search, WORD_HIVE, "English"),
        QuizCategory("Code Cracker", "Deduce the hidden colour code", Icons.Default.Lock, CODE_CRACKER, "Focus")
    )

    private fun hint(type: String) = when (type) {
        WORD_GUESS -> "Guess the hidden word. Green = right spot, yellow = wrong spot, gray = not in the word"
        WORD_LADDER -> "Change one letter at a time to reach the target word"
        LETTER_BALLOONS -> "Guess letters before all the balloons pop"
        WORD_HIVE -> "Make words of 4+ letters using the centre letter"
        else -> "Use the feedback pegs to crack the colour code"
    }

    /** Builds the JSON that the screen renders. Deterministic for a given [seed]. */
    fun generateJson(type: String, difficulty: String, context: Context, seed: Long): String {
        val d = difficulty.ifBlank { "Medium" }
        return when (type) {
            WORD_GUESS -> WordGuessGenerator.generate(WordPuzzleLists.get(context), d, seed).toString()
            WORD_LADDER -> {
                val lists = WordPuzzleLists.get(context)
                var p: WordLadderPuzzle? = null
                var s = seed
                var tries = 0
                while (p == null && tries < 5) { p = WordLadderGenerator.generate(lists, d, s); s++; tries++ }
                (p ?: WordLadderPuzzle("cat", "dog", 3, 3, listOf("cat", "cot", "dot", "dog"))).toJson(seed, d).toString()
            }
            LETTER_BALLOONS -> LetterBalloonsGenerator.generate(d, seed).toString()
            WORD_HIVE -> (WordHiveGenerator.generate(WordPuzzleLists.get(context), d, seed)
                ?: error("word list unavailable")).toJson(seed, d).toString()
            else -> CodeCrackerGenerator.generate(d, seed).toJson(seed, d).toString()
        }
    }

    fun createPuzzle(type: String, difficulty: String, context: Context, seed: Long = Random.nextLong()): Puzzle {
        val t = type.lowercase()
        return Puzzle(
            puzzleId = "${t}_${System.currentTimeMillis()}",
            puzzleType = t,
            question = try {
                generateJson(t, difficulty, context, seed)
            } catch (e: Exception) {
                PuzzleEvents.error(t, difficulty, "word generation failed: ${e.javaClass.simpleName}")
                throw e
            },
            answer = "generated",
            hint = hint(t),
            difficulty = difficulty,
            options = emptyList()
        )
    }

    fun tutorialContent(type: String): SimpleTutorialContent = when (type.lowercase()) {
        WORD_GUESS -> SimpleTutorialContent(
            "Word Guess", "🟩",
            "Guess the hidden 5-letter word in 6 tries. After each guess, green means the right letter in the right spot, yellow means the letter is in the word but elsewhere, gray means it is not in the word.",
            "Hard mode: you must reuse every hint you have been given.", listOf(Color(0xFF1E8E5A), Color(0xFF14604A)))
        WORD_LADDER -> SimpleTutorialContent(
            "Word Ladder", "🪜",
            "Turn the start word into the target word. Tap a tile, pick a new letter, then press ADD. Each step must change exactly one letter and make a real word.",
            "Beat par for three stars. Hints and undo are free to use, hints cost a few points.", listOf(Color(0xFF3AA0FF), Color(0xFF1F7BD6)))
        LETTER_BALLOONS -> SimpleTutorialContent(
            "Letter Balloons", "🎈",
            "Guess letters to uncover the hidden word. Each wrong letter pops a balloon. Uncover the word before they are all gone.",
            "The category is your clue. Vowels and common letters are a good start.", listOf(Color(0xFFFF6B6B), Color(0xFFD94848)))
        WORD_HIVE -> SimpleTutorialContent(
            "Word Hive", "🐝",
            "Make words of four or more letters from the seven letters. Every word must use the gold centre letter. Letters can be reused. A word that uses all seven letters is a pangram worth a bonus.",
            "Reach the goal rank to solve the puzzle.", listOf(Color(0xFFFFB020), Color(0xFFD98A00)))
        else -> SimpleTutorialContent(
            "Code Cracker", "🔐",
            "Crack the hidden colour code. Fill the pegs and press Check. A dark feedback peg means a right colour in the right place, a light peg means a right colour in the wrong place.",
            "Each colour has its own symbol, so you never rely on colour alone.", listOf(Color(0xFF9B5DE5), Color(0xFF7A3EC0)))
    }
}

@Composable
fun WordPuzzleTutorialScreen(type: String, onTutorialComplete: () -> Unit, onTutorialSkipped: () -> Unit) {
    SimpleTutorialScreen(content = WordPuzzleRegistry.tutorialContent(type), onComplete = onTutorialComplete, onSkip = onTutorialSkipped)
}

/** Routes the current Puzzle to the right word game and reports completion once per puzzle. */
@Composable
fun WordPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/" +
        "${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"
    val done: (Boolean, Int) -> Unit = { success, score -> handlePuzzleCompletion(success, true, score) }
    val json = currentPuzzle.question
    key(currentPuzzle.puzzleId) {
        when (JSONObject(json).optString("game", currentPuzzle.puzzleType ?: "")) {
            WordPuzzleRegistry.WORD_GUESS -> WordGuessScreen(json, level, onBack, done)
            WordPuzzleRegistry.WORD_LADDER -> WordLadderScreen(json, level, onBack, done)
            WordPuzzleRegistry.LETTER_BALLOONS -> LetterBalloonsScreen(json, level, onBack, done)
            WordPuzzleRegistry.WORD_HIVE -> WordHiveScreen(json, level, onBack, done)
            else -> CodeCrackerScreen(json, level, onBack, done)
        }
    }
}
