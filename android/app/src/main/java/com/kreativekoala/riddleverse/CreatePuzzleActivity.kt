package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.graphics.Typeface
import androidx.cardview.widget.CardView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import androidx.compose.material3.Button
import androidx.core.widget.addTextChangedListener
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import android.text.InputType
import android.util.Log
import android.widget.AdapterView
import android.widget.*
import okhttp3.*

import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull


data class ValidationResult(val isValid: Boolean, val errorMessage: String)

data class TopicCategory(
    val name: String,
    val topics: List<String>,
    val colors: IntArray,
    val emoji: String
)

class CreatePuzzleActivity : AppCompatActivity() {

    private lateinit var topicInput: EditText
    private lateinit var categoriesContainer: LinearLayout
    private lateinit var numPuzzlesCounter: TextView
    private lateinit var incrementButton: Button
    private lateinit var decrementButton: Button
    private lateinit var formatDropdown: Spinner
    private lateinit var generateButton: Button
    private lateinit var backButton: Button
    private lateinit var quickActionsContainer: LinearLayout
    private var numPuzzles = 1 // Default to 1 for image puzzles

    private var showTutorial = false
    private var currentTutorialStep = 0
    private val tutorialManager = CreatePuzzleTutorialManager()
    private var tutorialOverlay: LinearLayout? = null
    private var validationWarningView: TextView? = null
    private var isTopicValid = false

    private var localWordValidator: LocalWordValidator? = null
    private var isValidatorInitialized = false
    private var isFromSuggestedTopic = false

    // Enhanced puzzle formats with Music Puzzle added
    private val puzzleFormats = arrayOf(
        "🖼️ Image Puzzle", // Default option
        "🎵 Music Puzzle", // New music puzzle type
        "Multiple Choice",
        "Q/A Format",
        "Anagrams",
        "Crossword",
        "Word Snake",
        "Word Search"
    )

    private fun initializeWordValidator() {
        lifecycleScope.launch {
            try {
                localWordValidator = LocalWordValidator.getInstance(this@CreatePuzzleActivity)
                isValidatorInitialized = true
                Log.d("CreatePuzzle", "Dictionary validator initialized")
            } catch (e: Exception) {
                Log.w("CreatePuzzle", "Dictionary validator failed to initialize", e)
                // Continue without dictionary validation
            }
        }
    }

    // Create a comprehensive list of all suggested topics
    private fun getAllSuggestedTopics(): Set<String> {
        val allTopics = mutableSetOf<String>()
        val selectedFormat = formatDropdown.selectedItem?.toString() ?: "Image Puzzle"
        val categories = getTopicCategoriesForFormat(selectedFormat)

        categories.forEach { category ->
            allTopics.addAll(category.topics)
        }

        return allTopics
    }

    // Dynamic topic categories based on puzzle type
    private fun getTopicCategoriesForFormat(format: String): List<TopicCategory> {
        return when {
            format.contains("Image Puzzle") -> getImagePuzzleTopics()
            format.contains("Music Puzzle") -> getMusicPuzzleTopics()
            format.contains("Multiple Choice") ||
                    format.contains("Q/A Format") ||
                    format.contains("Crossword") ||
                    format.contains("Word Search") ||
                    format.contains("Word Snake") ||
                    format.contains("Anagram") -> getGeneralPuzzleTopics()
            else -> getGeneralPuzzleTopics()
        }
    }

    private fun getImagePuzzleTopics(): List<TopicCategory> {
        return listOf(
            TopicCategory(
                "🌅 Nature Scenes",
                listOf("Mountain Landscape", "Ocean Sunset", "Forest Path", "Waterfall", "Desert Dunes", "Cherry Blossoms"),
                intArrayOf(Color.parseColor("#4ECDC4"), Color.parseColor("#44A08D")),
                "🌅"
            ),
            TopicCategory(
                "🏛️ Architecture",
                listOf("Ancient Castle", "Modern Skyscraper", "Gothic Cathedral", "Japanese Temple", "Art Deco Building", "Futuristic City"),
                intArrayOf(Color.parseColor("#FFE066"), Color.parseColor("#FF9472")),
                "🏛️"
            ),
            TopicCategory(
                "🐾 Animals",
                listOf("Majestic Lion", "Colorful Butterfly", "Wise Owl", "Playful Dolphins", "Arctic Wolf", "Tropical Birds"),
                intArrayOf(Color.parseColor("#A8E6CF"), Color.parseColor("#7FD8BE")),
                "🐾"
            ),
            TopicCategory(
                "🎨 Artistic Styles",
                listOf("Van Gogh Style", "Watercolor Art", "Digital Fantasy", "Abstract Colors", "Impressionist", "Pixel Art"),
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E8E")),
                "🎨"
            ),
            TopicCategory(
                "🌌 Fantasy Worlds",
                listOf("Magical Forest", "Space Galaxy", "Underwater City", "Floating Islands", "Crystal Cave", "Dragon's Lair"),
                intArrayOf(Color.parseColor("#A29BFE"), Color.parseColor("#6C5CE7")),
                "🌌"
            ),
            TopicCategory(
                "🏞️ Scenic Views",
                listOf("Country Cottage", "City Skyline", "Beach Paradise", "Snowy Mountains", "Autumn Valley", "Starry Night"),
                intArrayOf(Color.parseColor("#74B9FF"), Color.parseColor("#0984E3")),
                "🏞️"
            )
        )
    }

    private fun getMusicPuzzleTopics(): List<TopicCategory> {
        return listOf(
            TopicCategory(
                "🎤 Famous Artists",
                listOf(
                    // ✅ These artists are explicitly supported by the server
                    "Michael Jackson",
                    "Elvis Presley",
                    "Madonna",
                    "Beatles",
                    "Queen",
                    "Whitney Houston"
                ),
                intArrayOf(Color.parseColor("#FFE066"), Color.parseColor("#FF9472")),
                "🎤"
            ),
            TopicCategory(
                "🌍 World Music",
                listOf(
                    // ✅ These country/language patterns are supported by the server
                    "Spanish Music",
                    "French Music",
                    "K-Pop",
                    "Latin Music",
                    "Bollywood Music"
                ),
                intArrayOf(Color.parseColor("#4ECDC4"), Color.parseColor("#44A08D")),
                "🌍"
            ),
            TopicCategory(
                "🎸 Music Genres",
                listOf(
                    // ✅ These general music keywords are supported
                    "Pop Music",
                    "Rock Music",
                    "Jazz Music",
                    "Classical Music"
                ),
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E8E")),
                "🎸"
            ),
            TopicCategory(
                "🎵 Music Eras",
                listOf(
                    // ✅ These contain music keywords and should work
                    "80s Music",
                    "90s Music",
                    "2000s Music",
                    "Classic Hits"
                ),
                intArrayOf(Color.parseColor("#74B9FF"), Color.parseColor("#0984E3")),
                "🎵"
            )
        )
    }

    // ✅ Add a validation helper for music topics to guide users
    private fun validateMusicTopic(topic: String): ValidationResult {
        val topicLower = topic.lowercase().trim()

        // Check if it's a supported famous artist
        val supportedArtists = listOf(
            "michael jackson", "elvis presley", "madonna", "beatles", "queen",
            "whitney houston", "prince", "bob dylan", "aretha franklin",
            "stevie wonder", "elton john", "david bowie"
        )

        for (artist in supportedArtists) {
            if (topicLower.contains(artist)) {
                return ValidationResult(true, "")
            }
        }

        // Check if it's a supported country/language pattern
        val supportedPatterns = listOf(
            "spanish music", "french music", "k-pop", "latin music",
            "bollywood", "pop music", "rock music", "jazz music",
            "classical music", "80s music", "90s music", "music"
        )

        for (pattern in supportedPatterns) {
            if (topicLower.contains(pattern)) {
                return ValidationResult(true, "")
            }
        }

        // If none of the supported patterns match, suggest alternatives
        return ValidationResult(
            false,
            "For music puzzles, try: famous artists (e.g., 'Michael Jackson'), genres (e.g., 'Pop Music'), or regions (e.g., 'Spanish Music')"
        )
    }

    private fun getGeneralPuzzleTopics(): List<TopicCategory> {
        return listOf(
            TopicCategory(
                "🎬 Entertainment",
                listOf("Disney Movies", "Marvel Heroes", "Harry Potter", "Star Wars"),
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E8E")),
                "🎬"
            ),
            TopicCategory(
                "🔬 Science",
                listOf("Space", "Animals", "Human Body", "Chemistry"),
                intArrayOf(Color.parseColor("#4ECDC4"), Color.parseColor("#44A08D")),
                "🔬"
            ),
            TopicCategory(
                "🏛️ History",
                listOf("Ancient Egypt", "World Wars", "Famous Leaders", "Inventions"),
                intArrayOf(Color.parseColor("#FFE066"), Color.parseColor("#FF9472")),
                "🏛️"
            ),
            TopicCategory(
                "⚽ Sports",
                listOf("Soccer", "Basketball", "Olympics", "Tennis"),
                intArrayOf(Color.parseColor("#00B894"), Color.parseColor("#00CEC9")),
                "⚽"
            ),
            TopicCategory(
                "🍕 Food",
                listOf("Italian Cuisine", "Desserts", "Healthy Foods", "World Foods"),
                intArrayOf(Color.parseColor("#FDCB6E"), Color.parseColor("#E17055")),
                "🍕"
            ),
            TopicCategory(
                "🌍 Geography",
                listOf("Countries", "Capital Cities", "Famous Landmarks", "Natural Wonders"),
                intArrayOf(Color.parseColor("#74B9FF"), Color.parseColor("#0984E3")),
                "🌍"
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_puzzle)

        supportActionBar?.hide()

        initializeViews()
        setupModernUI()
        setupListeners()
        setupTopicCategories() // Will use default format initially
        setupKeyboardHandling()
        checkAndShowTutorial()
    }

    private fun checkAndShowTutorial() {
        val hasSeenTutorial = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("create_puzzle_tutorial_seen", false)

        if (!hasSeenTutorial) {
            Handler(Looper.getMainLooper()).postDelayed({
                startTutorial()
            }, 500)
        }
    }

    private fun initializeViews() {
        topicInput = findViewById(R.id.topicInput)
        categoriesContainer = findViewById(R.id.sampleTopicsContainer)
        numPuzzlesCounter = findViewById(R.id.numPuzzlesCounter)
        incrementButton = findViewById(R.id.incrementButton)
        decrementButton = findViewById(R.id.decrementButton)
        formatDropdown = findViewById(R.id.formatDropdown)
        generateButton = findViewById(R.id.generateButton)
        backButton = findViewById(R.id.backButton)

        // Create quick actions container for better UX
        quickActionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 20, 20, 20)
            }
            setPadding(20, 20, 20, 20)
            background = createElevatedCard(Color.WHITE, 16)
            visibility = View.GONE
        }

        // Add quick actions container to the layout immediately after topic input
        val parent = topicInput.parent as? ViewGroup
        parent?.addView(quickActionsContainer, parent.indexOfChild(topicInput) + 1)
    }

    private fun setupModernUI() {
        // Set background gradient for the main layout
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView?.background = createGradientBackground(
            Color.parseColor("#F8F9FF"),
            Color.parseColor("#FFFFFF")
        )

        // Style the back button
        backButton.apply {
            background = createElevatedCard(Color.parseColor("#F8F9FF"), 12)
            text = "←"
            textSize = 18f
            setTextColor(Color.parseColor("#667eea"))
            setPadding(12, 12, 12, 12)
        }

        // Enhanced topic input with better styling
        topicInput.apply {
            background = createElevatedCard(Color.WHITE, 16)
            setPadding(60, 40, 60, 40)
            textSize = 16f
            hint = "✨ Enter topic (max 3 words) or tap categories below..."
            setHintTextColor(Color.parseColor("#9E9E9E"))
            setTextColor(Color.parseColor("#2E3440"))
            imeOptions = EditorInfo.IME_ACTION_DONE
            maxLines = 1
        }

        // Style the counter display
        numPuzzlesCounter.apply {
            background = createVibrantGradientCard(
                Color.parseColor("#667eea"),
                Color.parseColor("#764ba2")
            )
            setPadding(32, 24, 32, 24)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        // Style increment/decrement buttons
        incrementButton.apply {
            background = createVibrantGradientCard(
                Color.parseColor("#00B894"),
                Color.parseColor("#00CEC9")
            )
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            text = "+"
        }

        decrementButton.apply {
            background = createVibrantGradientCard(
                Color.parseColor("#FF7675"),
                Color.parseColor("#FD79A8")
            )
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            text = "-"
        }

        // Enhanced generate button
        generateButton.apply {
            background = createModernGradientButton()
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(60, 40, 60, 40)
            text = "🖼️ Generate Image Puzzle"
        }

        // Setup dropdown with image puzzle as default
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, puzzleFormats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatDropdown.adapter = adapter

        formatDropdown.background = createVibrantGradientCard(
            Color.parseColor("#74B9FF"),
            Color.parseColor("#0984E3")
        )
        formatDropdown.setSelection(0) // Set image puzzle as default
        formatDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedFormat = parent.getItemAtPosition(position).toString()

                when {
                    selectedFormat.contains("Crossword") -> {
                        generateButton.text = "🧩 Generate Crossword"
                        updatePuzzleCounterForFormat("crossword")
                    }
                    selectedFormat.contains("Word Search") -> {
                        generateButton.text = "🔍 Generate Word Search"
                        updatePuzzleCounterForFormat("wordsearch")
                    }
                    selectedFormat.contains("Anagram") -> {
                        generateButton.text = "🔤 Generate Anagrams"
                        updatePuzzleCounterForFormat("anagram")
                    }
                    selectedFormat.contains("Image Puzzle") -> {
                        generateButton.text = "🖼️ Generate Image Puzzle"
                        updatePuzzleCounterForFormat("image")
                    }
                    selectedFormat.contains("Music Puzzle") -> {
                        generateButton.text = "🎵 Generate Music Puzzle"
                        updatePuzzleCounterForFormat("music")
                    }
                    else -> {
                        generateButton.text = "🚀 Generate Puzzle"
                        updatePuzzleCounterForFormat("default")
                    }
                }

                // Update topic categories based on selected format
                setupTopicCategories()
                handleTutorialAction("format_selected")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                generateButton.text = "🖼️ Generate Image Puzzle"
            }
        }
    }



    private fun showImagePuzzleHint() {
        Toast.makeText(this, "🖼️ Image puzzles create beautiful jigsaw puzzles from AI-generated images!", Toast.LENGTH_LONG).show()
    }

    private fun setupKeyboardHandling() {
        topicInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard()

                if (topicInput.text.toString().trim().isNotEmpty()) {
                    showQuickActions()
                }
                true
            } else {
                false
            }
        }

        // Add real-time validation as user types
        topicInput.addTextChangedListener { editable ->
            // Reset the suggested topic flag when user manually types
            // Only reset if they're actually changing the text (not just setting it programmatically)
            if (topicInput.hasFocus()) {
                isFromSuggestedTopic = false
            }

            val topic = editable.toString().trim()

            if (topic.isNotEmpty()) {
                validateTopicRealTime(topic)
                showQuickActions()
                handleTutorialAction("topic_entered")
            } else {
                hideValidationWarning()
                hideQuickActions()
                isTopicValid = false
            }

            updateGenerateButtonState()
        }

        // Disable keyboard suggestions to prevent "kxtyqh" issue
        topicInput.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun validateTopicRealTime(topic: String) {
        val cleanTopic = topic.trim()

        // Skip validation for suggested topics
        val suggestedTopics = getAllSuggestedTopics()
        if (suggestedTopics.contains(cleanTopic) || isFromSuggestedTopic) {
            Log.d("CreatePuzzle", "Bypassing validation for suggested topic: $cleanTopic")
            hideValidationWarning()
            isTopicValid = true
            isFromSuggestedTopic = false // Reset flag
            return
        }

        // Quick validation first
        when {
            cleanTopic.length < 2 -> {
                showValidationWarning("Topic needs at least 2 characters", WarningLevel.INFO)
                isTopicValid = false
                return
            }
            cleanTopic.length > 100 -> {
                showValidationWarning("Topic too long (max 100 characters)", WarningLevel.ERROR)
                isTopicValid = false
                return
            }
            cleanTopic.split("\\s+".toRegex()).filter { it.isNotBlank() }.size > 3 -> {
                showValidationWarning("Maximum 3 words allowed", WarningLevel.ERROR)
                isTopicValid = false
                return
            }
            !cleanTopic.any { it.isLetter() } -> {
                showValidationWarning("Topic must contain letters", WarningLevel.ERROR)
                isTopicValid = false
                return
            }
            containsInappropriateContent(cleanTopic) -> {
                showValidationWarning("Topic contains inappropriate content", WarningLevel.ERROR)
                isTopicValid = false
                return
            }
        }

        // Enhanced validation using dictionary
        if (isValidatorInitialized && localWordValidator != null) {
            validateWithDictionary(cleanTopic)
        } else {
            // Fallback to basic validation
            validateWithoutDictionary(cleanTopic)
        }
    }

    private fun validateWithDictionary(topic: String) {
        lifecycleScope.launch {
            try {
                val words = topic.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val validationResults = mutableListOf<String>()
                var allWordsValid = true
                var hasRealWords = false

                for (word in words) {
                    val cleanWord = word.lowercase().filter { it.isLetter() }

                    // Skip very short words or obvious particles
                    if (cleanWord.length < 2 || cleanWord in listOf("a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "but")) {
                        continue
                    }

                    // Check if word exists in dictionary
                    val isValid = localWordValidator?.isValidWord(cleanWord) == true

                    if (isValid) {
                        hasRealWords = true
                    } else {
                        // Check for common typos and suggest corrections
                        val suggestions = localWordValidator?.getSuggestions(cleanWord, 3) ?: emptyList()
                        if (suggestions.isNotEmpty()) {
                            validationResults.add("Did you mean: ${suggestions.joinToString(", ")}?")
                            allWordsValid = false
                        } else {
                            // Check if it's likely random text
                            if (isLikelyRandomText(cleanWord)) {
                                validationResults.add("'$word' looks like random text")
                                allWordsValid = false
                            } else {
                                // Might be a proper noun or specialized term
                                validationResults.add("'$word' not found in dictionary")
                            }
                        }
                    }
                }

                // Provide feedback
                when {
                    !hasRealWords && words.size == 1 && isLikelyRandomText(words[0]) -> {
                        showValidationWarning("This looks like random text. Try a real topic!", WarningLevel.ERROR)
                        isTopicValid = false
                    }
                    !hasRealWords && validationResults.isNotEmpty() -> {
                        showValidationWarning(validationResults.first(), WarningLevel.WARNING)
                        isTopicValid = false
                    }
                    !allWordsValid && validationResults.size == 1 -> {
                        showValidationWarning(validationResults.first(), WarningLevel.WARNING)
                        // Allow submission but show warning
                        isTopicValid = true
                    }
                    !allWordsValid && validationResults.size > 1 -> {
                        showValidationWarning("Multiple words not recognized", WarningLevel.WARNING)
                        isTopicValid = false
                    }
                    else -> {
                        // Additional format-specific validation
                        validateFormatSpecific(topic)
                    }
                }

            } catch (e: Exception) {
                Log.w("CreatePuzzle", "Dictionary validation failed", e)
                validateWithoutDictionary(topic)
            }
        }
    }

    // Format-specific validation (especially for music)
    private fun validateFormatSpecific(topic: String) {
        val selectedFormat = formatDropdown.selectedItem.toString()

        if (selectedFormat.contains("Music Puzzle")) {
            val musicValidation = validateMusicTopic(topic)
            if (!musicValidation.isValid) {
                showValidationWarning(musicValidation.errorMessage, WarningLevel.WARNING)
                isTopicValid = false
            } else {
                hideValidationWarning()
                isTopicValid = true
            }
        } else {
            hideValidationWarning()
            isTopicValid = true
        }
    }

    // Fallback validation without dictionary
    private fun validateWithoutDictionary(topic: String) {
        when {
            isLikelyRandomText(topic) -> {
                showValidationWarning("This looks like random text. Try a real topic!", WarningLevel.WARNING)
                isTopicValid = false
            }
            else -> {
                // Format-specific validation
                validateFormatSpecific(topic)
            }
        }
    }

    // Enhanced random text detection
    private fun isLikelyRandomText(text: String): Boolean {
        val cleanText = text.lowercase().replace("\\s+".toRegex(), "")

        return when {
            // Specific known random strings
            cleanText in listOf("kxtyqh", "asdf", "qwerty", "test", "testing", "abc", "xyz", "zzzz") -> true

            // No vowels (like "kxtyqh") - but allow some exceptions
            cleanText.length > 3 && !cleanText.any { it in "aeiou" } &&
                    !cleanText.matches(Regex(".*[bcdfghjklmnpqrstvwxyz]{2}.*")) -> true

            // Too many consonants in a row
            cleanText.contains(Regex("[bcdfghjklmnpqrstvwxyz]{5,}")) -> true

            // Repeating patterns
            cleanText.length > 2 && cleanText.all { it == cleanText[0] } -> true

            // Very high consonant to vowel ratio
            cleanText.length > 4 && run {
                val vowelCount = cleanText.count { it in "aeiou" }
                val consonantCount = cleanText.count { it in "bcdfghjklmnpqrstvwxyz" }
                consonantCount > 0 && vowelCount.toFloat() / consonantCount < 0.2f
            } -> true

            // Keyboard patterns
            cleanText.matches(Regex(".*[qwertyuiop]{3,}.*")) ||
                    cleanText.matches(Regex(".*[asdfghjkl]{3,}.*")) ||
                    cleanText.matches(Regex(".*[zxcvbnm]{3,}.*")) -> true

            else -> false
        }
    }



    private fun containsInappropriateContent(topic: String): Boolean {
        val blockedWords = listOf(
            "sexual", "violence", "drugs", "hate", "nazi", "terrorist",
            "porn", "xxx", "kill", "murder", "bomb", "weapon", "suicide",
            "death", "blood", "gore", "rape", "abuse", "torture"
        )

        val topicLower = topic.lowercase()
        return blockedWords.any { topicLower.contains(it) }
    }

    enum class WarningLevel {
        INFO, WARNING, ERROR
    }

    private fun showValidationWarning(message: String, level: WarningLevel) {
        hideValidationWarning() // Remove existing warning first

        val color = when (level) {
            WarningLevel.INFO -> Color.parseColor("#2196F3")
            WarningLevel.WARNING -> Color.parseColor("#FF9800")
            WarningLevel.ERROR -> Color.parseColor("#F44336")
        }

        validationWarningView = TextView(this).apply {
            text = message
            textSize = 12f
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 8, 20, 8)
            gravity = Gravity.CENTER
            background = createElevatedCard(Color.parseColor("#FFF3E0"), 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 8, 20, 0)
            }
        }

        // Add warning view after topic input
        val parent = topicInput.parent as? ViewGroup
        val topicIndex = parent?.indexOfChild(topicInput) ?: -1
        if (topicIndex >= 0) {
            parent?.addView(validationWarningView, topicIndex + 1)
        }
    }

    private fun hideValidationWarning() {
        validationWarningView?.let { warning ->
            (warning.parent as? ViewGroup)?.removeView(warning)
        }
        validationWarningView = null
    }

    // Add method to update generate button state based on validation
    private fun updateGenerateButtonState() {
        val topic = topicInput.text.toString().trim()
        val hasText = topic.isNotEmpty()

        generateButton.apply {
            isEnabled = hasText && isTopicValid
            alpha = if (isEnabled) 1.0f else 0.6f

            if (!isEnabled && hasText && !isTopicValid) {
                // Show visual feedback that validation is failing
                background = createVibrantGradientCard(
                    Color.parseColor("#BDBDBD"),
                    Color.parseColor("#9E9E9E")
                )
            } else if (isEnabled) {
                background = createModernGradientButton()
            }
        }

        // Also update quick actions if visible
        if (quickActionsContainer.visibility == View.VISIBLE && quickActionsContainer.childCount > 0) {
            val quickGenerateButton = quickActionsContainer.getChildAt(0) as? Button
            quickGenerateButton?.apply {
                isEnabled = hasText && isTopicValid
                alpha = if (isEnabled) 1.0f else 0.6f
            }
        }
    }

    private fun showQuickActions() {
        quickActionsContainer.removeAllViews()

        val selectedFormat = formatDropdown.selectedItem.toString()
        val buttonText = when {
            selectedFormat.contains("Word Search") -> "🔍 Quick Generate Word Search (${numPuzzles} puzzles)"
            selectedFormat.contains("Crossword") -> "🧩 Quick Generate Crossword (${numPuzzles} puzzles)"
            selectedFormat.contains("Anagram") -> "🔤 Quick Generate Anagrams (${numPuzzles} puzzles)"
            selectedFormat.contains("Image Puzzle") -> "🖼️ Quick Generate Image Puzzle (${numPuzzles} ${if (numPuzzles == 1) "puzzle" else "puzzles"})"
            selectedFormat.contains("Music Puzzle") -> "🎵 Quick Generate Music Puzzle (${numPuzzles} ${if (numPuzzles == 1) "puzzle" else "puzzles"})"
            else -> "🎯 Quick Generate (${numPuzzles} puzzles)"
        }

        val quickGenerateButton = Button(this).apply {
            text = buttonText
            background = createVibrantGradientCard(
                Color.parseColor("#00B894"),
                Color.parseColor("#00CEC9")
            )
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 30, 40, 30)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }

            setOnClickListener {
                hideKeyboard()
                generatePuzzle()
            }
        }

        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val quickDecrementButton = Button(this).apply {
            background = createVibrantGradientCard(
                Color.parseColor("#FF7675"),
                Color.parseColor("#FD79A8")
            )
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            text = "-"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            setPadding(32, 16, 32, 16)

            setOnClickListener {
                val selectedFormat = formatDropdown.selectedItem.toString()
                val minPuzzles = 1
                numPuzzles = (numPuzzles - 1).coerceAtLeast(minPuzzles)
                updateCounterDisplays()
                animateButton(this)
            }
        }

        val quickCounterDisplay = TextView(this).apply {
            background = createVibrantGradientCard(
                Color.parseColor("#667eea"),
                Color.parseColor("#764ba2")
            )
            setPadding(32, 24, 32, 24)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = numPuzzles.toString()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 0, 16, 0)
            }
        }

        val quickIncrementButton = Button(this).apply {
            background = createVibrantGradientCard(
                Color.parseColor("#00B894"),
                Color.parseColor("#00CEC9")
            )
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            text = "+"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 0, 0, 0)
            }
            setPadding(32, 16, 32, 16)

            setOnClickListener {
                val selectedFormat = formatDropdown.selectedItem.toString()
                val maxPuzzles = when {
                    selectedFormat.contains("Image Puzzle") -> 3
                    selectedFormat.contains("Music Puzzle") -> 5
                    else -> 10
                }
                numPuzzles = (numPuzzles + 1).coerceAtMost(maxPuzzles)
                updateCounterDisplays()
                animateButton(this)
                handleTutorialAction("counter_used")
            }
        }

        numberRow.addView(quickDecrementButton)
        numberRow.addView(quickCounterDisplay)
        numberRow.addView(quickIncrementButton)

        quickActionsContainer.addView(quickGenerateButton)
        quickActionsContainer.addView(numberRow)

        if (quickActionsContainer.visibility != View.VISIBLE) {
            quickActionsContainer.visibility = View.VISIBLE
            val slideIn = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            quickActionsContainer.startAnimation(slideIn)
        }
    }

    private fun updateCounterDisplays() {
        numPuzzlesCounter.text = numPuzzles.toString()

        if (quickActionsContainer.visibility == View.VISIBLE && quickActionsContainer.childCount > 1) {
            val numberRow = quickActionsContainer.getChildAt(1) as? LinearLayout
            val quickCounter = numberRow?.getChildAt(1) as? TextView
            quickCounter?.text = numPuzzles.toString()

            val selectedFormat = formatDropdown.selectedItem.toString()
            val buttonText = when {
                selectedFormat.contains("Word Search") -> "🔍 Quick Generate Word Search (${numPuzzles} puzzles)"
                selectedFormat.contains("Crossword") -> "🧩 Quick Generate Crossword (${numPuzzles} puzzles)"
                selectedFormat.contains("Anagram") -> "🔤 Quick Generate Anagrams (${numPuzzles} puzzles)"
                selectedFormat.contains("Image Puzzle") -> "🖼️ Quick Generate Image Puzzle (${numPuzzles} ${if (numPuzzles == 1) "puzzle" else "puzzles"})"
                selectedFormat.contains("Music Puzzle") -> "🎵 Quick Generate Music Puzzle (${numPuzzles} ${if (numPuzzles == 1) "puzzle" else "puzzles"})"
                else -> "🎯 Quick Generate (${numPuzzles} puzzles)"
            }

            val quickGenerateButton = quickActionsContainer.getChildAt(0) as? Button
            quickGenerateButton?.text = buttonText
        }
    }

    private fun animateButton(button: Button) {
        button.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
            }
    }

    private fun hideQuickActions() {
        quickActionsContainer.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(topicInput.windowToken, 0)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        incrementButton.setOnClickListener {
            val selectedFormat = formatDropdown.selectedItem.toString()
            val maxPuzzles = when {
                selectedFormat.contains("Image Puzzle") -> 3
                selectedFormat.contains("Music Puzzle") -> 5
                else -> 10
            }
            numPuzzles = (numPuzzles + 1).coerceAtMost(maxPuzzles)
            updateCounterDisplays()
            animateButton(incrementButton)
            handleTutorialAction("counter_used")
        }

        decrementButton.setOnClickListener {
            val minPuzzles = 1
            numPuzzles = (numPuzzles - 1).coerceAtLeast(minPuzzles)
            updateCounterDisplays()
            animateButton(decrementButton)
            handleTutorialAction("counter_used")
        }

        generateButton.setOnClickListener {
            hideKeyboard()
            handleTutorialAction("generate_clicked")
            generatePuzzle()
        }
    }

    private fun setupTopicCategories() {
        categoriesContainer.removeAllViews()

        val selectedFormat = formatDropdown.selectedItem?.toString() ?: "🖼️ Image Puzzle"
        val topicCategories = getTopicCategoriesForFormat(selectedFormat)

        for (category in topicCategories) {
            val categorySection = createCompactCategorySection(category)
            categoriesContainer.addView(categorySection)
        }
    }

    private fun createCompactCategorySection(category: TopicCategory): LinearLayout {
        val sectionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 8, 20, 8) // Reduced margins
            }
        }

        // Compact header showing just category name and topic count
        val categoryHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 16, 20, 16) // Reduced padding
            background = createVibrantGradientCard(category.colors[0], category.colors[1])
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            elevation = 2f // Reduced elevation

            // Emoji
            val emoji = TextView(this@CreatePuzzleActivity).apply {
                text = category.emoji
                textSize = 20f // Reduced size
                setPadding(0, 0, 12, 0)
            }
            addView(emoji)

            // Category name with topic count
            val nameText = TextView(this@CreatePuzzleActivity).apply {
                text = "${category.name.substringAfter(" ")} (${category.topics.size} topics)"
                textSize = 16f // Reduced size
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            addView(nameText)

            // Tap to expand indicator
            val expandIcon = TextView(this@CreatePuzzleActivity).apply {
                text = "▼"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(12, 0, 0, 0)
            }
            addView(expandIcon)
        }

        // Create topics container (initially hidden)
        val topicsContainer = createCompactTopicsContainer(category).apply {
            visibility = View.GONE
        }

        // Header click listener
        var isExpanded = false
        categoryHeader.setOnClickListener {
            isExpanded = !isExpanded

            val expandIcon = categoryHeader.getChildAt(2) as TextView
            expandIcon.animate()
                .rotation(if (isExpanded) 180f else 0f)
                .setDuration(200)
                .start()

            if (isExpanded) {
                topicsContainer.visibility = View.VISIBLE
                val slideDown = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
                topicsContainer.startAnimation(slideDown)
            } else {
                val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
                slideUp.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                    override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                    override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                        topicsContainer.visibility = View.GONE
                    }
                })
                topicsContainer.startAnimation(slideUp)
            }
        }

        sectionContainer.addView(categoryHeader)
        sectionContainer.addView(topicsContainer)

        return sectionContainer
    }

    private fun createCompactTopicsContainer(category: TopicCategory): HorizontalScrollView {
        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 5, 0, 5) // Reduced margins
            }
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 10) // Reduced padding
        }

        val topicsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 0, 20, 0)
        }

        for (topic in category.topics) {
            val topicChip = createCompactTopicChip(topic, category.colors)
            topicsRow.addView(topicChip)
        }

        scrollView.addView(topicsRow)
        return scrollView
    }

    private fun createCompactTopicChip(topic: String, colors: IntArray): CardView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 4, 8, 4)
            }
            radius = 20f
            cardElevation = 4f
        }

        val gradientBackground = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            cornerRadius = 20f
        }

        cardView.background = gradientBackground

        val textView = TextView(this).apply {
            text = topic
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER

            setOnClickListener {
                // Set flag to bypass validation for suggested topics
                isFromSuggestedTopic = true

                topicInput.setText(topic)
                hideKeyboard()
                showQuickActions()
                handleTutorialAction("category_selected")

                // Animation
                cardView.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(150)
                    .withEndAction {
                        cardView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                    }
            }
        }

        cardView.addView(textView)
        return cardView
    }

    private fun createElevatedCard(color: Int, cornerRadius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(
                cornerRadius.toFloat(), cornerRadius.toFloat(),
                cornerRadius.toFloat(), cornerRadius.toFloat(),
                cornerRadius.toFloat(), cornerRadius.toFloat(),
                cornerRadius.toFloat(), cornerRadius.toFloat()
            )
        }
    }

    private fun createVibrantGradientCard(startColor: Int, endColor: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = 16f
        }
    }

    private fun createGradientBackground(startColor: Int, endColor: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, endColor)
        )
    }

    private fun createModernGradientButton(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.parseColor("#667eea"),
                Color.parseColor("#764ba2")
            )
        ).apply {
            cornerRadius = 20f
        }
    }

    private fun generatePuzzle() {
        val topic = topicInput.text.toString().trim()

        if (topic.isEmpty()) {
            Toast.makeText(this, "Please enter a topic to generate a puzzle.", Toast.LENGTH_SHORT).show()
            return
        }

        val topicValidation = validateTopic(topic)
        if (!topicValidation.isValid) {
            Toast.makeText(this, topicValidation.errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        val selectedFormat = formatDropdown.selectedItem.toString()
        val currentUser = FirebaseAuth.getInstance().currentUser?.displayName ?: "Guest"
        val userId = FirebaseAuth.getInstance().currentUser?.displayName ?: "guest_user"

        val mediaType = "application/json".toMediaTypeOrNull()

        val requestBody = JSONObject().apply {
            put("topic", topic)
            put("format", selectedFormat)
            put("numPuzzles", numPuzzles)
            put("userId", userId)
        }

        generateButton.apply {
            text = when {
                selectedFormat.contains("Word Search") -> "🔍 Creating Word Search..."
                selectedFormat.contains("Crossword") -> "🧩 Creating Crossword..."
                selectedFormat.contains("Anagram") -> "🔤 Creating Anagrams..."
                selectedFormat.contains("Image Puzzle") -> "🖼️ Generating AI Image..."
                selectedFormat.contains("Music Puzzle") -> "🎵 Creating Music Quiz..."
                else -> "🔄 Creating Puzzle..."
            }
            isEnabled = false
            background = createVibrantGradientCard(
                Color.parseColor("#BDBDBD"),
                Color.parseColor("#9E9E9E")
            )

            animate()
                .rotation(360f)
                .setDuration(1000)
                .withEndAction {
                    animate()
                        .rotation(0f)
                        .setDuration(0)
                }
        }

        val url = "https://puzzleverseai.com/generate-riddle"
        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(mediaType, requestBody.toString()))
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CreatePuzzleActivity, "⌛ Error generating puzzle", Toast.LENGTH_SHORT).show()
                    resetGenerateButton()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody)
                val jobId = json.optString("jobId", "")

                val resultIntent = Intent().apply {
                    putExtra("jobId", jobId)
                }

                runOnUiThread {
                    val successMessage = when {
                        selectedFormat.contains("Word Search") -> "🔍 Word Search creation started!"
                        selectedFormat.contains("Crossword") -> "🧩 Crossword creation started!"
                        selectedFormat.contains("Anagram") -> "🔤 Anagram creation started!"
                        selectedFormat.contains("Image Puzzle") -> "🖼️ AI Image Puzzle generation started! This may take 2-3 minutes."
                        selectedFormat.contains("Music Puzzle") -> "🎵 Music Quiz creation started!"
                        else -> "🎉 Puzzle creation started!"
                    }
                    Toast.makeText(this@CreatePuzzleActivity, successMessage, Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        })
    }

    private fun resetGenerateButton() {
        generateButton.apply {
            val selectedFormat = formatDropdown.selectedItem.toString()
            text = when {
                selectedFormat.contains("Crossword") -> "🧩 Generate Crossword"
                selectedFormat.contains("Word Search") -> "🔍 Generate Word Search"
                selectedFormat.contains("Anagram") -> "🔤 Generate Anagrams"
                selectedFormat.contains("Image Puzzle") -> "🖼️ Generate Image Puzzle"
                selectedFormat.contains("Music Puzzle") -> "🎵 Generate Music Puzzle"
                else -> "🚀 Generate Puzzle"
            }
            isEnabled = true
            background = createModernGradientButton()
            clearAnimation()
        }
    }

    private fun validateTopic(topic: String): ValidationResult {
        val cleanTopic = topic.trim()

        // Basic validation (length, characters, etc.)
        if (cleanTopic.length < 2) {
            return ValidationResult(false, "Topic must be at least 2 characters long")
        }
        if (cleanTopic.length > 100) {
            return ValidationResult(false, "Topic must be less than 100 characters")
        }

        val words = cleanTopic.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size > 3) {
            return ValidationResult(false, "Topic can only have 3 words maximum. Please use a shorter topic.")
        }

        // Check for inappropriate content (existing logic)
        val blockedWords = listOf(
            "sexual", "violence", "drugs", "hate", "nazi", "terrorist",
            "porn", "xxx", "kill", "murder", "bomb", "weapon", "suicide",
            "death", "blood", "gore", "rape", "abuse", "torture"
        )

        val topicLower = cleanTopic.lowercase()
        for (blockedWord in blockedWords) {
            if (topicLower.contains(blockedWord)) {
                return ValidationResult(false, "Topic contains inappropriate content")
            }
        }

        // ✅ SPECIAL VALIDATION FOR MUSIC PUZZLES
        val selectedFormat = formatDropdown.selectedItem.toString()
        if (selectedFormat.contains("Music Puzzle")) {
            return validateMusicTopic(cleanTopic)
        }

        // For non-music puzzles, use existing validation
        val allowedPattern = Regex("^[a-zA-Z0-9\\s\\-_'.&çğıöşüÇĞIİÖŞÜ]+$")
        if (!allowedPattern.matches(cleanTopic)) {
            return ValidationResult(false, "Topic contains invalid characters. Only letters, numbers, and basic punctuation allowed.")
        }

        if (!cleanTopic.any { it.isLetter() }) {
            return ValidationResult(false, "Topic must contain at least one letter")
        }

        return ValidationResult(true, "")
    }

    // ✅ Update showMusicPuzzleHint to provide better guidance
    private fun showMusicPuzzleHint() {
        Toast.makeText(
            this,
            "🎵 Music puzzles work best with: Famous artists (Michael Jackson), Music genres (Pop Music), or World music (Spanish Music)",
            Toast.LENGTH_LONG
        ).show()
    }

    // ✅ Update the counter limits for music puzzles to match server capabilities
    private fun updatePuzzleCounterForFormat(format: String) {
        when (format) {
            "image" -> {
                if (numPuzzles > 3) {
                    numPuzzles = 1
                    updateCounterDisplays()
                }
                showImagePuzzleHint()
            }
            "music" -> {
                // ✅ Music puzzles: limit to 1-5 as per server capability
                if (numPuzzles > 5) {
                    numPuzzles = 1
                    updateCounterDisplays()
                }
                showMusicPuzzleHint()
            }
            else -> {
                // For other formats, standard behavior
            }
        }
    }

    // Tutorial methods (simplified versions)
    private fun startTutorial() {
        showTutorial = true
        currentTutorialStep = 0
        showTutorialStep()
    }

    private fun showTutorialStep() {
        val steps = tutorialManager.getTutorialSteps()
        if (currentTutorialStep >= steps.size) {
            endTutorial()
            return
        }

        val step = steps[currentTutorialStep]
        showTutorialOverlay(step)
        highlightTargetComponent(step.targetComponent)
    }

    private fun nextTutorialStep() {
        currentTutorialStep++
        showTutorialStep()
    }

    private fun endTutorial() {
        showTutorial = false
        tutorialOverlay?.visibility = View.GONE
        clearAllHighlights()

        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("create_puzzle_tutorial_seen", true)
            .apply()
    }

    private fun showTutorialOverlay(step: CreatePuzzleTutorialStep) {
        tutorialOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        tutorialOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createElevatedCard(Color.parseColor("#E3F2FD"), 16)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 20, 20, 20)
            }
            elevation = 12f
        }

        val stepIndicator = TextView(this).apply {
            text = "Step ${currentTutorialStep + 1} of ${tutorialManager.getTutorialSteps().size}"
            textSize = 12f
            setTextColor(Color.parseColor("#1976D2"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val titleText = TextView(this).apply {
            text = step.title
            textSize = 18f
            setTextColor(Color.parseColor("#1976D2"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

        val descriptionText = TextView(this).apply {
            text = step.description
            textSize = 14f
            setTextColor(Color.parseColor("#424242"))
            setPadding(0, 0, 0, 16)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val skipButton = android.widget.Button(this).apply {
            text = getString(R.string.skip_tutorial)
            textSize = 12f
            background = createElevatedCard(android.graphics.Color.parseColor("#F5F5F5"), 8)
            setTextColor(android.graphics.Color.parseColor("#757575"))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 0)
            }

            setOnClickListener { endTutorial() }
        }

        val nextButton = android.widget.Button(this).apply {
            text = if (step.id.isEmpty()) getString(R.string.got_it) else getString(R.string.continue_label)
            textSize = 12f
            background = createVibrantGradientCard(
                android.graphics.Color.parseColor("#4CAF50"),
                android.graphics.Color.parseColor("#66BB6A")
            )
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 8, 20, 8)

            setOnClickListener {
                if (step.id.isEmpty()) {
                    nextTutorialStep()
                }
            }
        }

        buttonsRow.addView(skipButton)
        buttonsRow.addView(nextButton)

        tutorialOverlay?.addView(stepIndicator)
        tutorialOverlay?.addView(titleText)
        tutorialOverlay?.addView(descriptionText)
        tutorialOverlay?.addView(buttonsRow)

        val mainLayout = findViewById<ScrollView>(R.id.scrollView)
        val parentLayout = mainLayout.parent as ViewGroup
        parentLayout.addView(tutorialOverlay, 0)
    }

    private fun highlightTargetComponent(targetComponent: String) {
        clearAllHighlights()

        val highlightColor = Color.parseColor("#7B1FA2")
        val highlightBorder = createHighlightBorder(highlightColor)

        when (targetComponent) {
            "topic_input" -> topicInput.background = highlightBorder
            "categories" -> categoriesContainer.background = highlightBorder
            "format_dropdown" -> formatDropdown.background = highlightBorder
            "counter" -> {
                numPuzzlesCounter.background = highlightBorder
                incrementButton.background = highlightBorder
                decrementButton.background = highlightBorder
            }
            "generate_button" -> generateButton.background = highlightBorder
        }
    }

    private fun clearAllHighlights() {
        topicInput.background = createElevatedCard(Color.WHITE, 16)
        categoriesContainer.background = null
        formatDropdown.background = createVibrantGradientCard(
            Color.parseColor("#74B9FF"),
            Color.parseColor("#0984E3")
        )
        numPuzzlesCounter.background = createVibrantGradientCard(
            Color.parseColor("#667eea"),
            Color.parseColor("#764ba2")
        )
        incrementButton.background = createVibrantGradientCard(
            Color.parseColor("#00B894"),
            Color.parseColor("#00CEC9")
        )
        decrementButton.background = createVibrantGradientCard(
            Color.parseColor("#FF7675"),
            Color.parseColor("#FD79A8")
        )
        generateButton.background = createModernGradientButton()
    }

    private fun createHighlightBorder(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(6, color)
            cornerRadius = 16f
        }
    }

    private fun handleTutorialAction(action: String) {
        if (!showTutorial) return

        val steps = tutorialManager.getTutorialSteps()
        val currentStep = steps.getOrNull(currentTutorialStep)

        if (currentStep?.id == action) {
            nextTutorialStep()
        }
    }
}