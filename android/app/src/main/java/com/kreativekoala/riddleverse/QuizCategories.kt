package com.kreativekoala.riddleverse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class QuizCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: String,
    val filterCategory: String
)

object QuizCategories {
    val allCategories = listOf(
        // Math
        QuizCategory("Math", "Mathematical Challenges", Icons.Default.Calculate, "math", "Math"),
        QuizCategory("Average", "Calculate Averages", Icons.Default.BarChart, "average", "Math"),
        QuizCategory("Division", "Master Division", Icons.Default.Percent, "division", "Math"),
        QuizCategory("Estimation", "Chart Estimation", Icons.Default.TrendingUp, "mathestimation", "Math"),
        QuizCategory("Percentage", "Percentage Calculations", Icons.Default.Percent, "percentages", "Math"),
        QuizCategory("Discounts", "Price Ordering", Icons.Default.LocalOffer, "discounts", "Math"),
        QuizCategory("Purchasing", "Subscription Calculations", Icons.Default.CreditCard, "purchasing", "Math"),
        QuizCategory("Conversion", "Unit Comparisons", Icons.Default.SwapHoriz, "conversion", "Math"),
        QuizCategory("Subtraction", "Find the difference", Icons.Default.Remove, "subtraction", "Math"),
        QuizCategory("Tip Calculation", "Calculate tips", Icons.Default.RestaurantMenu, "mathtipping", "Math"),
        QuizCategory("Number Sum", "Add Numbers to Target", Icons.Default.Calculate, "numbersum", "Math"),
        QuizCategory("Math Expression", "Complete the Equations", Icons.Default.Calculate, "mathexpression", "Focus"),
        QuizCategory("Math Comparison", "Compare mathematical expressions", Icons.Default.Calculate, "mathcomparison", "Math"),
        QuizCategory("Math Crossword", "Solve intersecting math equations in crossword format", Icons.Default.GridOn, "mathcrossword", "Math"),


        // English/Language
        QuizCategory("Story Puzzle", "Narrative Mysteries", Icons.Default.MenuBook, "storyPuzzle", "English"),
        QuizCategory("Anagram", "Word Scrambles", Icons.Default.TextFields, "anagram", "English"),
        QuizCategory("Antonyms", "Match Opposite Words", Icons.Default.BubbleChart, "antonyms", "English"),
        QuizCategory("Synonyms", "Match Same Words", Icons.Default.Link, "synonyms", "English"),
        QuizCategory("Crossword", "Word Crosswords", Icons.Default.GridView, "crossword", "English"),
        QuizCategory("Word Connotations", "Sort words by tone", Icons.Default.Psychology, "connotationwords", "English"),
        QuizCategory("Word Prefixes", "Improve your vocabulary", Icons.Default.Subtitles, "wordprefix", "English"),
        QuizCategory("Word Search", "Find Words in Grid", Icons.Default.Search, "wordsearch", "English"),
        QuizCategory("Word Snake", "Trace Snake-like Word Paths", Icons.Default.Timeline, "wordsnake", "English"),
        QuizCategory("Letter Search", "Hunt for words in set of letters", Icons.Default.Search, "letterset", "English"),

        // Memory
        QuizCategory("Visual Memory", "Remember patterns", Icons.Default.GridView, "memorysquares", "Memory"),
        QuizCategory("Story Recall", "Listen and remember key items", Icons.Default.RecordVoiceOver, "memorystory", "Memory"),
        QuizCategory("Timeline Puzzle", "Sequence events in chronological order", Icons.Default.Timeline, "memorysequencing", "Memory"),
        QuizCategory("Listen & Learn", "Absorb facts and sort by categories", Icons.Default.Headphones, "memoryretention", "Memory"),
        QuizCategory("Pinball Physics", "Predict ball path through deflectors", Icons.Default.Sports, "pinballdeflector", "Memory"),
        QuizCategory("Memory Match", "Remember and compare symbols in sequence", Icons.Default.Psychology, "memoryprevioussingle", "Memory"),
        QuizCategory("Image Vortex", "Identify New Images", Icons.Default.Visibility, "imagevortex", "Memory"),
        QuizCategory("Memory Pairs", "Recall Previous Screen", Icons.Default.RememberMe, "memorypreviouspair", "Memory"),
        QuizCategory("Triangle Memory", "Pattern recognition", Icons.Default.ChangeHistory, "triangledotmemory", "Memory"),
        QuizCategory("Color Match", "Text vs meaning", Icons.Default.Palette, "colortextmatching", "Memory"),


        // Geography
        QuizCategory("City Geography", "Place cities on world map", Icons.Default.LocationCity, "geography_cities", "Geography"),
        QuizCategory("Country Geography", "Place countries on world map", Icons.Default.Public, "geography_countries", "Geography"),

        QuizCategory("Word Crypto", "Decode secret messages using number substitution", Icons.Default.Pin, "crypto", "Puzzles"),
        //QuizCategory("Find Differences", "Spot the differences", Icons.Default.Search, "find_differences", "Visual"),
        QuizCategory("Find Object", "Find Object", Icons.Default.Search, "find_object", "Visual"),

        QuizCategory("Image Questions", "Image Questions", Icons.Default.Photo, "imagequestion", "Visual"),


        QuizCategory("Waldo Puzzle", "Waldo Puzzle", Icons.Default.CheckCircle, "waldopuzzle", "Visual"),

        QuizCategory("Music Match", "Multi-Match Music Quiz", Icons.Default.MusicNote, "musicidentification", "Audio"),

        QuizCategory("Image Puzzle", "Image Puzzle", Icons.Default.CheckCircle, "imagepuzzle", "Visual"),


        // Focus
        QuizCategory("Unique Object", "Find the Odd One Out", Icons.Default.VisibilityOff, "uniqueobject", "Focus"),
        QuizCategory("Color Shape Match", "Does the text match the shape color?", Icons.Default.Palette, "colormatching", "Focus"),

        QuizCategory("Symmetry", "Copy patterns from left grid to right grid", Icons.Default.GridOn, "symmetry", "Visual"),

        // Logic
        QuizCategory("Dual Card Logic", "Check if numbers are even or letters are vowels", Icons.Default.CheckCircle, "dualcard", "Logic"),
        QuizCategory("Number Sequence", "Tap numbers in ascending order", Icons.Default.FilterList, "numbersequence", "Logic"),
        //QuizCategory("Flow Puzzle", "Connect the Dots", Icons.Default.GridOn, "flowpuzzle", "Logic"),


        // Reaction
        QuizCategory("Symbol Swipe", "Swipe symbols in the correct direction", Icons.Default.SwipeRight, "symbolswipe", "Reaction"),
        QuizCategory("Context Switch", "Remember items through distracting tasks", Icons.Default.Psychology, "contextswitch", "Memory"),

        QuizCategory("Sentence Transitions", "Complete sentences with the right connecting words", Icons.Default.Link, "sentenceTransitions", "Language"),





        // General
        //QuizCategory("Image Match", "match image to text", Icons.Default.Palette, "imagematch", "General"),
        QuizCategory("Progressive Reveal", "guess the blurred image", Icons.Default.Visibility, "progressiverevelation", "Visual"),
        QuizCategory("Real or AI", "Real or AI", Icons.Default.CheckCircle, "realorai", "General"),


        QuizCategory("Trivia", "General Knowledge", Icons.Default.Quiz, "trivia", "General")
    )

    /**
     * Get all available puzzle types as a list of category strings
     */
    fun getAllAvailablePuzzleTypes(): List<String> = allCategories.map { it.category }

    /**
     * Get QuizCategory for a specific puzzle type with intelligent fallback
     */
    fun getQuizCategoryForType(puzzleType: String): QuizCategory? {
        // First try direct lookup from our comprehensive list
        allCategories.find { it.category == puzzleType }?.let { return it }

        // If not found, use the original when-based mapping for backward compatibility
        return when (puzzleType) {
            "math" -> QuizCategory("Math", "Mathematical Challenges", Icons.Default.Calculate, "math", "Math")
            "storyPuzzle" -> QuizCategory("Story Puzzle", "Narrative Mysteries", Icons.Default.MenuBook, "storyPuzzle", "English")
            "anagram" -> QuizCategory("Anagram", "Word Scrambles", Icons.Default.TextFields, "anagram", "English")
            "antonyms" -> QuizCategory("Antonyms", "Match Opposite Words", Icons.Default.BubbleChart, "antonyms", "English")
            "synonyms" -> QuizCategory("Synonyms", "Match Same Words", Icons.Default.Link, "synonyms", "English")
            "memorysquares" -> QuizCategory("Visual Memory", "Remember patterns", Icons.Default.GridView, "memorysquares", "Memory")
            "crossword" -> QuizCategory("Crossword", "Word Crosswords", Icons.Default.GridView, "crossword", "English")
            "memorystory" -> QuizCategory("Story Recall", "Listen and remember key items", Icons.Default.RecordVoiceOver, "memorystory", "Memory")
            "memorysequencing" -> QuizCategory("Timeline Puzzle", "Sequence events in chronological order", Icons.Default.Timeline, "memorysequencing", "Memory")
            "memoryretention" -> QuizCategory("Listen & Learn", "Absorb facts and sort by categories", Icons.Default.Headphones, "memoryretention", "Memory")
            "pinballdeflector" -> QuizCategory("Pinball Physics", "Predict ball path through deflectors", Icons.Default.Sports, "pinballdeflector", "Memory")
            "trivia" -> QuizCategory("Trivia", "General Knowledge", Icons.Default.Quiz, "trivia", "General")
            "average" -> QuizCategory("Average", "Calculate Averages", Icons.Default.BarChart, "average", "Math")
            "division" -> QuizCategory("Division", "Master Division", Icons.Default.Percent, "division", "Math")
            "mathestimation" -> QuizCategory("Estimation", "Chart Estimation", Icons.Default.TrendingUp, "mathestimation", "Math")
            "percentages" -> QuizCategory("Percentage", "Percentage Calculations", Icons.Default.Percent, "percentages", "Math")
            "discounts" -> QuizCategory("Discounts", "Price Ordering", Icons.Default.LocalOffer, "discounts", "Math")
            "purchasing" -> QuizCategory("Purchasing", "Subscription Calculations", Icons.Default.CreditCard, "purchasing", "Math")
            "conversion" -> QuizCategory("Conversion", "Unit Comparisons", Icons.Default.SwapHoriz, "conversion", "Math")
            "connotationwords" -> QuizCategory("Word Connotations", "Sort words by tone", Icons.Default.Psychology, "connotationwords", "English")
            "wordprefix" -> QuizCategory("Word Prefixes", "Improve your vocabulary", Icons.Default.Subtitles, "wordprefix", "English")
            "subtraction" -> QuizCategory("Subtraction", "Find the difference", Icons.Default.Remove, "subtraction", "Math")
            "mathtipping" -> QuizCategory("Tip Calculation", "Calculate tips", Icons.Default.RestaurantMenu, "mathtipping", "Math")
            "geography_cities" -> QuizCategory("City Geography", "Place cities on world map", Icons.Default.LocationCity, "geography_cities", "Geography")
            "geography_countries" -> QuizCategory("Country Geography", "Place countries on world map", Icons.Default.Public, "geography_countries", "Geography")
            else -> {
                // Fallback for new/unmapped puzzle types
                when {
                    puzzleType.contains("math", ignoreCase = true) ->
                        QuizCategory(puzzleType.replaceFirstChar { it.uppercase() }, "Mathematical Challenge", Icons.Default.Calculate, puzzleType, "Math")
                    puzzleType.contains("memory", ignoreCase = true) ->
                        QuizCategory(puzzleType.replaceFirstChar { it.uppercase() }, "Memory Challenge", Icons.Default.Psychology, puzzleType, "Memory")
                    puzzleType.contains("word", ignoreCase = true) ->
                        QuizCategory(puzzleType.replaceFirstChar { it.uppercase() }, "Word Challenge", Icons.Default.TextFields, puzzleType, "English")
                    puzzleType.contains("geography", ignoreCase = true) ->
                        QuizCategory(puzzleType.replaceFirstChar { it.uppercase() }, "Geography Challenge", Icons.Default.Public, puzzleType, "Geography")
                    else ->
                        QuizCategory(puzzleType.replaceFirstChar { it.uppercase() }, "Brain Challenge", Icons.Default.Extension, puzzleType, "General")
                }
            }
        }
    }

    /**
     * Get categories filtered by filter category
     */
    fun getCategoriesByFilter(filterCategory: String): List<QuizCategory> {
        return if (filterCategory == "All") {
            allCategories
        } else {
            allCategories.filter { it.filterCategory == filterCategory }
        }
    }

    /**
     * Get all unique filter categories
     */
    fun getFilterCategories(): List<String> {
        return listOf("All") + allCategories.map { it.filterCategory }.distinct().sorted()
    }

    /**
     * Get smart fallback recent puzzles that include newer types
     */
    fun getSmartFallbackRecentPuzzles(): List<String> {
        val allTypes = getAllAvailablePuzzleTypes()
        // Mix of popular classics and some newer types including geography
        val priorityTypes = listOf(
            "memoryprevioussingle",
            "memorypreviouspair",
            "pinballdeflector",
            "wordsearch",
            "memorystory",
            "memorysequencing",
            "memoryretention",
            "geography_cities",
            "crypto",
            "geography_countries",
            "find_object",
            "imagepuzzle"
        )
        val availablePriority = priorityTypes.filter { it in allTypes }

        return if (availablePriority.size >= 3) {
            availablePriority.take(3)
        } else {
            // Fill with random selection if not enough priority types
            (availablePriority + allTypes.shuffled()).take(3).distinct()
        }
    }

    /**
     * Check if a puzzle type is new/recently added
     */
    fun isPuzzleTypeNew(puzzleType: String): Boolean {
        val newPuzzleTypes = listOf(
            "find_object",
            "pinballdeflector", // Recently added
            "memoryprevioussingle",
            "memorypreviouspair",
            "wordsearch",
            "crypto",
            "imagepuzzle",
            "geography_cities",    // Geography puzzles are new
            "geography_countries"  // Geography puzzles are new
            // Add other new types here
        )
        return puzzleType in newPuzzleTypes
    }

    fun isOfflinePuzzleType(puzzleType: String): Boolean {
        // Define which puzzle types can work without internet connection
        // These are the specific offline puzzle types provided
        val offlinePuzzleTypes = setOf(
            "uniqueobject",
            "discounts",
            "division",
            "contextswitch",
            "mathcrossword",
            "symmetry",
            "trainrouting",
            "pinballdeflector",
            "memorypreviouspair",
            "memoryprevioussingle",
            "geography_countries",
            "geography_cities",
            "colortextmatching",
            "conversion",
            "triangledotmemory",
            "numbersequence",
            "dualcard",
            "mathestimation",
            "mathtipping",
            "percentages",
            "numbersum",
            "symbolswipe",
            "colormatching",
            "imagevortex",
            "memorysquares",
            "mathexpression",
            "purchasing",
            "subtraction",
            "mathcomparison"
        )

        return offlinePuzzleTypes.contains(puzzleType)
    }
}