package com.kreativekoala.riddleverse

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import android.content.Context
import android.util.Log
import androidx.collection.LruCache
import androidx.room.ColumnInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(
    tableName = "words",
    indices = [Index(value = ["word"], name = "idx_word_lookup")]
)
data class WordEntity(
    @PrimaryKey
    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "frequency", defaultValue = "100")
    val frequency: Int?,

    @ColumnInfo(name = "rarity", defaultValue = "'common'")
    val rarity: String?,

    @ColumnInfo(name = "length")
    val length: Int?
)

@Dao
interface WordDao {
    @Query("SELECT EXISTS(SELECT 1 FROM words WHERE word = :word)")
    suspend fun wordExists(word: String): Boolean

    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): WordEntity?

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getTotalWords(): Int

    @Query("SELECT word FROM words WHERE word LIKE :prefix || '%' LIMIT :limit")
    suspend fun getWordsWithPrefix(prefix: String, limit: Int): List<String>

    @Query("SELECT word FROM words WHERE length = :length LIMIT :limit")
    suspend fun getWordsByLength(length: Int, limit: Int): List<String>

    @Query("SELECT word FROM words WHERE length <= :maxLength ORDER BY frequency DESC LIMIT :limit")
    suspend fun getWordsUpToLength(maxLength: Int, limit: Int): List<String>

    // NEW: Methods for enhanced validation
    @Query("SELECT COUNT(*) FROM words WHERE word = :word")
    suspend fun exists(word: String): Int

    @Query("""
        SELECT word FROM words 
        WHERE word LIKE '%' || :word || '%' 
        OR (
            word LIKE :word || '%' 
            AND length(word) - length(:word) <= 2
        )
        ORDER BY 
            CASE WHEN word LIKE :word || '%' THEN 0 ELSE 1 END,
            length(word),
            frequency DESC
        LIMIT :limit
    """)
    suspend fun findSimilarWords(word: String, limit: Int): List<String>

    // Enhanced similarity search with edit distance approximation
    @Query("""
        SELECT word FROM words 
        WHERE (
            -- Prefix matches (typos at end)
            word LIKE :word || '%' 
            OR word LIKE '%' || :word
            -- Single character substitution patterns
            OR (length(word) = length(:word) AND 
                (substr(word, 2) = substr(:word, 2) OR
                 substr(word, 1, length(word)-1) = substr(:word, 1, length(:word)-1)))
            -- Common transposition patterns  
            OR (length(word) = length(:word) AND word GLOB :pattern1)
            OR (length(word) = length(:word) AND word GLOB :pattern2)
        )
        AND word != :word
        ORDER BY frequency DESC
        LIMIT :limit
    """)
    suspend fun findTypoSuggestions(
        word: String,
        pattern1: String,
        pattern2: String,
        limit: Int
    ): List<String>
}

@Database(
    entities = [WordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile
        private var INSTANCE: WordDatabase? = null

        fun getDatabase(context: Context): WordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordDatabase::class.java,
                    "word_database"
                )
                    .createFromAsset("database/words.db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class WordValidationResult(
    val valid: Boolean,
    val word: String? = null,
    val points: Int = 0,
    val frequency: Int = 0,
    val rarity: String = "common",
    val reason: String? = null,
    val source: String = "unknown",
    val isNewDiscovery: Boolean = false
) {
    companion object {
        fun valid(
            word: String,
            points: Int,
            frequency: Int,
            rarity: String,
            source: String,
            isNewDiscovery: Boolean = false
        ) = WordValidationResult(
            valid = true,
            word = word,
            points = points,
            frequency = frequency,
            rarity = rarity,
            source = source,
            isNewDiscovery = isNewDiscovery
        )

        fun invalid(reason: String, source: String = "validation") =
            WordValidationResult(
                valid = false,
                reason = reason,
                source = source
            )
    }
}

class LocalWordValidator private constructor(
    private val wordDao: WordDao
) {
    private val validationCache = LruCache<String, WordValidationResult>(2000)
    private val existsCache = LruCache<String, Boolean>(1000) // Cache for simple existence checks
    private val suggestionsCache = LruCache<String, List<String>>(500) // Cache for suggestions

    companion object {
        @Volatile
        private var INSTANCE: LocalWordValidator? = null
        private const val TAG = "LocalWordValidator"

        suspend fun getInstance(context: Context): LocalWordValidator {
            return INSTANCE ?: synchronized(this) {
                val database = WordDatabase.getDatabase(context)
                val instance = LocalWordValidator(database.wordDao())
                INSTANCE = instance
                instance
            }
        }
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

        // Quick validations first
        if (normalizedWord.length < 3) {
            return@withContext WordValidationResult.invalid("Word must be at least 3 letters", "client")
        }

        if (!normalizedWord.matches(Regex("^[A-Z]+$"))) {
            return@withContext WordValidationResult.invalid("Letters only", "client")
        }

        if (usedWords.contains(normalizedWord)) {
            return@withContext WordValidationResult.invalid("Already used", "client")
        }

        // Check if word can be formed from letter set
        if (!canFormFromLetterSet(normalizedWord, letterSet)) {
            return@withContext WordValidationResult.invalid("Cannot form from available letters", "client")
        }

        // Check puzzle words first (server data)
        puzzleWords?.get(normalizedWord)?.let { puzzleWord ->
            Log.d(TAG, "âœ… Found in puzzle data: ${puzzleWord.word}")
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
        validationCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "ðŸ“¦ Cache hit for: $normalizedWord")
            return@withContext cached
        }

        // Check local database
        Log.d(TAG, "ðŸ” Checking local database for: $normalizedWord")
        val wordEntity = wordDao.getWord(cacheKey)

        val result = if (wordEntity != null) {
            Log.d(TAG, "âœ… Found in local database: ${wordEntity.word}")
            val frequency = wordEntity.frequency ?: 100
            val rarity = wordEntity.rarity ?: "common"

            WordValidationResult.valid(
                word = normalizedWord,
                points = calculatePoints(normalizedWord, frequency, rarity),
                frequency = frequency,
                rarity = rarity,
                source = "local",
                isNewDiscovery = true
            )
        } else {
            Log.d(TAG, "âŒ Word not found: $normalizedWord")
            WordValidationResult.invalid("Word not found in dictionary", "local")
        }

        // Cache the result
        validationCache.put(cacheKey, result)
        return@withContext result
    }

    // NEW: Enhanced isValidWord method for CreatePuzzleActivity
    suspend fun isValidWord(word: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedWord = word.trim().lowercase()

        // Check cache first
        existsCache.get(normalizedWord)?.let { cached ->
            return@withContext cached
        }

        try {
            val exists = wordDao.exists(normalizedWord) > 0
            existsCache.put(normalizedWord, exists)
            Log.d(TAG, "Word existence check: '$word' = $exists")
            return@withContext exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking word existence: $word", e)
            return@withContext false
        }
    }

    // NEW: Enhanced getSuggestions method for CreatePuzzleActivity
    suspend fun getSuggestions(word: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val normalizedWord = word.trim().lowercase()
        val cacheKey = "$normalizedWord:$limit"

        // Check cache first
        suggestionsCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        try {
            val suggestions = mutableListOf<String>()

            // First try simple pattern matching for common typos
            val basicSuggestions = wordDao.findSimilarWords(normalizedWord, limit)
            suggestions.addAll(basicSuggestions)

            // If we need more suggestions and the word is long enough, try advanced patterns
            if (suggestions.size < limit && normalizedWord.length > 3) {
                val remainingLimit = limit - suggestions.size

                // Create patterns for common transpositions (e.g., "teh" -> "the")
                val chars = normalizedWord.toCharArray()
                val patterns = mutableListOf<String>()

                // Try swapping adjacent characters
                for (i in 0 until chars.size - 1) {
                    val pattern1 = StringBuilder(normalizedWord)
                    pattern1.setCharAt(i, chars[i + 1])
                    pattern1.setCharAt(i + 1, chars[i])
                    patterns.add("*${pattern1}*")
                }

                if (patterns.isNotEmpty()) {
                    val advancedSuggestions = wordDao.findTypoSuggestions(
                        normalizedWord,
                        patterns.getOrNull(0) ?: "",
                        patterns.getOrNull(1) ?: "",
                        remainingLimit
                    )

                    // Add only new suggestions
                    advancedSuggestions.forEach { suggestion ->
                        if (!suggestions.contains(suggestion) && suggestions.size < limit) {
                            suggestions.add(suggestion)
                        }
                    }
                }
            }

            val finalSuggestions = suggestions.take(limit)
            suggestionsCache.put(cacheKey, finalSuggestions)

            Log.d(TAG, "Generated ${finalSuggestions.size} suggestions for '$word': $finalSuggestions")
            return@withContext finalSuggestions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions for: $word", e)
            return@withContext emptyList()
        }
    }

    private fun canFormFromLetterSet(word: String, letterSet: String): Boolean {
        val letterCounts = mutableMapOf<Char, Int>()

        // Count available letters
        letterSet.uppercase().forEach { letter ->
            letterCounts[letter] = letterCounts.getOrDefault(letter, 0) + 1
        }

        // Check if word can be formed
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
            frequency >= 150 -> 5  // Common words get small bonus
            frequency >= 75 -> 10  // Uncommon words get medium bonus
            else -> 20             // Rare words get big bonus
        }
        val rarityBonus = when (rarity) {
            "rare" -> 15
            "uncommon" -> 5
            else -> 0
        }

        return basePoints + lengthBonus + frequencyBonus + rarityBonus
    }

    suspend fun getHints(letterSet: String, count: Int = 5): List<String> = withContext(Dispatchers.IO) {
        try {
            val maxLength = letterSet.length
            val potentialWords = wordDao.getWordsUpToLength(maxLength, 200)

            val validHints = potentialWords.filter { word ->
                canFormFromLetterSet(word, letterSet)
            }.take(count)

            Log.d(TAG, "Generated ${validHints.size} hints for letter set: $letterSet")
            return@withContext validHints.map { it.uppercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate hints", e)
            return@withContext emptyList()
        }
    }

    // Cache management methods
    fun clearCaches() {
        validationCache.evictAll()
        existsCache.evictAll()
        suggestionsCache.evictAll()
        Log.d(TAG, "All caches cleared")
    }

    fun getCacheStats(): String {
        return "Validation: ${validationCache.size()}/2000, " +
                "Exists: ${existsCache.size()}/1000, " +
                "Suggestions: ${suggestionsCache.size()}/500"
    }
}