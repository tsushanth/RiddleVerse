package com.kreativekoala.riddleverse

import java.io.File

internal object WordPuzzleTestSupport {
    val lists: WordPuzzleLists by lazy {
        val dir = listOf("src/main/assets/word_puzzles", "app/src/main/assets/word_puzzles", "android/app/src/main/assets/word_puzzles")
            .map { File(it) }.first { it.isDirectory }
        File(dir, "common_words.txt").inputStream().use { c ->
            File(dir, "valid5.txt").inputStream().use { v -> WordPuzzleLists.parse(c, v) }
        }
    }
}
