package com.kreativekoala.riddleverse

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WordDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val validationCache = LruCache<String, WordValidationResult>(2000)
    private val existsCache = LruCache<String, Boolean>(1000)
    private val suggestionsCache = LruCache<String, List<String>>(500)

    companion object {
        private const val DATABASE_NAME = "words.db"
        private const val DATABASE_VERSION = 1
        private const val TAG = "WordDatabaseHelper"

        @Volatile
        private var INSTANCE: WordDatabaseHelper? = null

        fun getInstance(context: Context): WordDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = WordDatabaseHelper(context.applicationContext)
                instance.copyDatabaseFromAssets(context)
                INSTANCE = instance
                instance
            }
        }
    }

    private fun copyDatabaseFromAssets(context: Context) {
        val dbPath = context.getDatabasePath(DATABASE_NAME)

        // Only copy if database doesn't exist
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()

            try {
                context.assets.open("database/words.db").use { input ->
                    FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Database copied from assets")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Database already exists from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle upgrades if needed
    }

    suspend fun validateWord(
        word: String,
        letterSet: String,
        usedWords: Set<String>,
        puzzleWords: Map<String, LetterSetWord>? = null
    ): WordValidationResult = withContext(Dispatchers.IO) {
        val normalizedWord = word.trim().uppercase()
        val cacheKey = normalizedWord.lowercase()

        Log.d(TAG, "Validating word: '$word' -> '$normalizedWord'")

        // Quick validations
        if (normalizedWord.length < 3) {
            return@withContext WordValidationResult.invalid("Word must be at least 3 letters", "client")
        }

        if (!normalizedWord.matches(Regex("^[A-Z]+$"))) {
            return@withContext WordValidationResult.invalid("Letters only", "client")
        }

        if (usedWords.contains(normalizedWord)) {
            return@withContext WordValidationResult.invalid("Already used", "client")
        }

        if (!canFormFromLetterSet(normalizedWord, letterSet)) {
            return@withContext WordValidationResult.invalid("Cannot form from available letters", "client")
        }

        // Check puzzle words first
        puzzleWords?.get(normalizedWord)?.let { puzzleWord ->
            return@withContext WordValidationResult.valid(
                word = normalizedWord,
                points = puzzleWord.points,
                frequency = puzzleWord.frequency.toInt(),
                rarity = puzzleWord.rarity,
                source = "server",
                isNewDiscovery = false
            )
        }

        // Check cache
        validationCache.get(cacheKey)?.let { return@withContext it }

        // Query database
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT word, frequency, rarity FROM words WHERE word = ? LIMIT 1",
            arrayOf(cacheKey)
        )

        val result = if (cursor.moveToFirst()) {
            val frequency = cursor.getInt(1)
            val rarity = cursor.getString(2) ?: "common"

            WordValidationResult.valid(
                word = normalizedWord,
                points = calculatePoints(normalizedWord, frequency, rarity),
                frequency = frequency,
                rarity = rarity,
                source = "local",
                isNewDiscovery = true
            )
        } else {
            WordValidationResult.invalid("Word not found in dictionary", "local")
        }

        cursor.close()
        validationCache.put(cacheKey, result)
        result
    }

    suspend fun isValidWord(word: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedWord = word.trim().lowercase()

        existsCache.get(normalizedWord)?.let { return@withContext it }

        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM words WHERE word = ?",
            arrayOf(normalizedWord)
        )

        val exists = if (cursor.moveToFirst()) cursor.getInt(0) > 0 else false
        cursor.close()

        existsCache.put(normalizedWord, exists)
        exists
    }

    suspend fun getSuggestions(word: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val normalizedWord = word.trim().lowercase()
        val cacheKey = "$normalizedWord:$limit"

        suggestionsCache.get(cacheKey)?.let { return@withContext it }

        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT word FROM words 
            WHERE word LIKE ? || '%' 
               OR word LIKE '%' || ? || '%'
            ORDER BY frequency DESC
            LIMIT ?
            """,
            arrayOf(normalizedWord, normalizedWord, limit.toString())
        )

        val suggestions = mutableListOf<String>()
        while (cursor.moveToNext()) {
            suggestions.add(cursor.getString(0))
        }
        cursor.close()

        suggestionsCache.put(cacheKey, suggestions)
        suggestions
    }

    suspend fun getHints(letterSet: String, count: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT word FROM words WHERE length <= ? ORDER BY frequency DESC LIMIT 200",
            arrayOf(letterSet.length.toString())
        )

        val words = mutableListOf<String>()
        while (cursor.moveToNext()) {
            words.add(cursor.getString(0))
        }
        cursor.close()

        words.filter { canFormFromLetterSet(it, letterSet) }
            .take(count)
            .map { it.uppercase() }
    }

    private fun canFormFromLetterSet(word: String, letterSet: String): Boolean {
        val letterCounts = mutableMapOf<Char, Int>()
        letterSet.uppercase().forEach { letter ->
            letterCounts[letter] = letterCounts.getOrDefault(letter, 0) + 1
        }

        word.uppercase().forEach { letter ->
            val available = letterCounts.getOrDefault(letter, 0)
            if (available <= 0) return false
            letterCounts[letter] = available - 1
        }
        return true
    }

    private fun calculatePoints(word: String, frequency: Int, rarity: String): Int {
        val basePoints = 10
        val lengthBonus = maxOf(0, word.length - 3) * 3
        val frequencyBonus = when {
            frequency >= 150 -> 5
            frequency >= 75 -> 10
            else -> 20
        }
        val rarityBonus = when (rarity) {
            "rare" -> 15
            "uncommon" -> 5
            else -> 0
        }
        return basePoints + lengthBonus + frequencyBonus + rarityBonus
    }

    fun clearCaches() {
        validationCache.evictAll()
        existsCache.evictAll()
        suggestionsCache.evictAll()
    }
}