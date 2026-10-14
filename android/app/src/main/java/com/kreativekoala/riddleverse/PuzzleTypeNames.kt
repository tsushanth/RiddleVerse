package com.kreativekoala.riddleverse

/**
 * Turns a puzzle type key ("numbersequence", "memoryprevioussingle", "storyPuzzle", "find_object")
 * into text a player can read ("Number Sequence", "Memory Previous Single", "Story Puzzle", "Find Object").
 *
 * Use [display] anywhere a type key becomes visible text. Order of precedence:
 *  1. the proper game name from [NewGameInfo] (grid, classic, spatial and word games),
 *  2. the home-screen title from [QuizCategories] (so the start screen matches the card that was tapped),
 *  3. [humanize], which never fails: it splits camelCase / separators / known words and Title-Cases the result.
 */
object PuzzleTypeNames {

    /** Visible name for [type]. Blank input gives "Puzzle". */
    fun display(type: String?): String {
        val key = type?.trim().orEmpty()
        if (key.isEmpty()) return "Puzzle"
        NewGameInfo.of(key)?.let { return it.name }
        quizTitle(key)?.let { return it }
        EXTRA_NAMES[key.lowercase()]?.let { return it }
        return humanize(key)
    }

    /** The Categories-tab card for [key] (covers QuizCategories, spatial and word registries), or null. */
    internal fun card(key: String): QuizCategory? = try {
        QuizCategories.allCategories.firstOrNull { it.category == key }
            ?: QuizCategories.allCategories.firstOrNull { it.category.equals(key, ignoreCase = true) }
    } catch (e: Throwable) {
        null // never let a name lookup crash a screen
    }

    private fun quizTitle(key: String): String? = card(key)?.title

    /**
     * Label for the category line on start screens: the new games keep [NewGameInfo]'s label, every other
     * card derives it from its home-screen filter, so the label always matches the filter the card lives in.
     * Null when no card exists (callers keep their own fallback).
     */
    fun categoryLabel(type: String?): String? {
        val key = type?.trim().orEmpty()
        if (key.isEmpty()) return null
        NewGameInfo.of(key)?.let { return it.category }
        return card(key)?.let { labelForFilter(it.filterCategory) }
    }

    fun labelForFilter(filter: String): String = when (filter) {
        "Logic" -> "Logic Games"
        "English", "Language" -> "Word Games"
        "Memory" -> "Memory Games"
        "Math" -> "Math Games"
        "Visual" -> "Visual Games"
        "Focus" -> "Focus Games"
        "Geography" -> "Geography Games"
        "Reaction" -> "Reaction Games"
        "Audio" -> "Audio Games"
        "Puzzles" -> "Puzzle Games"
        else -> "General"
    }

    /** Legacy keys that are not in [QuizCategories] (or are hidden from the home screen) but still reach start screens. */
    private val EXTRA_NAMES = mapOf(
        "storypuzzle" to "Story Puzzle",
        "wordassociation" to "Word Association",
        "imagematch" to "Image Match",
        "find_differences" to "Find Differences",
        "flowpuzzle" to "Flow Puzzle",
        "mathtipping" to "Tip Calculation",
        "mathestimation" to "Estimation",
        "memoryprevioussingle" to "Memory Match",
        "memorypreviouspair" to "Memory Pairs",
        "multiple choice" to "Multiple Choice",
        "q&a" to "Q&A",
        "daily" to "Daily Quiz",
        "custom" to "Custom Puzzle",
    )

    /** Words that appear glued together in the all-lowercase legacy keys. Longest match wins in [segment]. */
    private val VOCAB: Set<String> = setOf(
        "ai", "and", "antonym", "antonyms", "anagram", "average", "balloon", "balloons", "binary", "cage", "card", "cities", "city",
        "code", "color", "colour", "comparison", "conduit", "connotation", "context", "conversion", "country", "countries", "cracker", "crate",
        "crossword", "crypto", "deflector", "differences", "disc", "discounts", "division", "dot", "dual", "estimation", "expression",
        "find", "flip", "flow", "geography", "grid", "guess", "hive", "image", "images", "identification", "ladder", "letter", "light", "logic",
        "match", "matching", "math", "memory", "merge", "music", "number", "numbers", "object", "or", "pack", "pair", "percentages", "piece",
        "pinball", "pixel", "prefix", "previous", "progressive", "puzzle", "purchasing", "quiz", "real", "reveal", "revelation", "retention",
        "safe", "search", "sentence", "sequence", "sequencing", "set", "shape", "shift", "sign", "single", "slide", "snake", "squares",
        "story", "subtraction", "sudoku", "sum", "swipe", "switch", "symbol", "symmetry", "synonym", "synonyms", "text", "tile", "tiles",
        "tint", "tip", "tipping", "tower", "train", "transitions", "triangle", "trivia", "tube", "unique", "vortex", "waldo", "word", "words",
        "routing", "sweep", "visual", "audio", "listen", "learn", "timeline", "pairs", "singles", "mine", "mines", "peg", "pegs",
    )

    /**
     * Readable Title Case for any type key. Handles separators (`_`, `-`, space), camelCase and
     * all-lowercase concatenations of known words ("numbersequence" -> "Number Sequence").
     * Unknown blobs fall back to a plain capitalised word rather than guessing a split.
     */
    fun humanize(rawKey: String): String {
        val cleaned = rawKey.trim().dropWhile { !it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return "Puzzle"
        val tokens = cleaned
            .replace(Regex("[_\\-]+"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .flatMap { splitCamel(it) }
            .flatMap { token ->
                if (token.all { it.isLowerCase() } && token.length > 4) segment(token) else listOf(token)
            }
        return tokens.joinToString(" ") { titleCase(it) }
    }

    private fun titleCase(word: String): String =
        if (word.equals("ai", ignoreCase = true)) "AI" else word.lowercase().replaceFirstChar { it.uppercase() }

    /** "storyPuzzle" -> [story, Puzzle]; keeps acronym runs together ("AIQuiz" -> [AI, Quiz]). */
    private fun splitCamel(token: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (i in token.indices) {
            val c = token[i]
            val prev = token.getOrNull(i - 1)
            val next = token.getOrNull(i + 1)
            val boundary = prev != null && sb.isNotEmpty() && (
                (c.isUpperCase() && prev.isLowerCase()) ||
                    (c.isUpperCase() && prev.isUpperCase() && next != null && next.isLowerCase()) ||
                    (c.isDigit() != prev.isDigit())
                )
            if (boundary) {
                parts += sb.toString()
                sb.clear()
            }
            sb.append(c)
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        return parts
    }

    /** Fewest-words segmentation of an all-lowercase blob using [VOCAB]; the blob itself if it cannot be fully covered. */
    internal fun segment(blob: String): List<String> {
        val n = blob.length
        val best = arrayOfNulls<List<String>>(n + 1)
        best[0] = emptyList()
        for (end in 1..n) {
            var cand: List<String>? = null
            for (start in 0 until end) {
                val head = best[start] ?: continue
                val w = blob.substring(start, end)
                if (w !in VOCAB) continue
                val next = head + w
                // fewer words first; on ties prefer the later-starting (longer last) word
                if (cand == null || next.size < cand.size) cand = next
            }
            best[end] = cand
        }
        return best[n] ?: listOf(blob)
    }
}
