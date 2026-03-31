// ContextSwitchPuzzleGenerator.kt
package com.kreativekoala.riddleverse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object ContextSwitchPuzzleGenerator {

    // Content database with rotating themes
    private val contentDatabase = mapOf(
        // Geographic
        "european_countries" to listOf("France", "Germany", "Italy", "Spain", "Netherlands", "Belgium", "Austria", "Portugal", "Greece", "Czech Republic", "Hungary", "Poland", "Sweden", "Norway", "Denmark", "Switzerland", "Ireland", "Finland"),
        "asian_cities" to listOf("Tokyo", "Seoul", "Bangkok", "Singapore", "Mumbai", "Shanghai", "Hong Kong", "Jakarta", "Manila", "Kuala Lumpur", "Delhi", "Osaka", "Taipei", "Hanoi", "Colombo"),
        "us_states" to listOf("California", "Texas", "Florida", "New York", "Arizona", "Colorado", "Nevada", "Oregon", "Utah", "Montana", "Alaska", "Hawaii", "Maine", "Vermont", "Wyoming"),

        // Food & Cuisine
        "asian_cuisines" to listOf("Sushi", "Pad Thai", "Kimchi", "Ramen", "Biryani", "Pho", "Dumplings", "Curry", "Teriyaki", "Miso", "Satay", "Laksa", "Bulgogi", "Rendang", "Tandoori"),
        "italian_foods" to listOf("Pizza", "Pasta", "Risotto", "Lasagna", "Gelato", "Tiramisu", "Bruschetta", "Cannoli", "Minestrone", "Focaccia", "Gnocchi", "Carbonara", "Pesto", "Polenta", "Osso Buco"),
        "desserts" to listOf("Chocolate Cake", "Ice Cream", "Cheesecake", "Apple Pie", "Brownies", "Cookies", "Donuts", "Cupcakes", "Pudding", "Sorbet", "Macarons", "Trifle", "Mousse", "Flan", "Baklava"),

        // Technology
        "programming_languages" to listOf("Python", "JavaScript", "Java", "Kotlin", "Swift", "C++", "Go", "Rust", "TypeScript", "PHP", "Ruby", "Scala", "Dart", "C#", "Objective-C"),
        "tech_companies" to listOf("Google", "Apple", "Microsoft", "Amazon", "Meta", "Tesla", "Netflix", "Adobe", "Spotify", "Airbnb", "Uber", "Twitter", "Intel", "Samsung", "Sony"),
        "operating_systems" to listOf("Windows", "macOS", "Linux", "Android", "iOS", "Ubuntu", "Chrome OS", "FreeBSD", "Unix", "Fedora", "Debian", "CentOS", "Red Hat", "SUSE", "Mint"),

        // Nature & Animals
        "dog_breeds" to listOf("Labrador", "Golden Retriever", "Bulldog", "Beagle", "Poodle", "Rottweiler", "Yorkshire", "Boxer", "Husky", "Dachshund", "Shepherd", "Chihuahua", "Collie", "Mastiff", "Spaniel"),
        "birds" to listOf("Eagle", "Sparrow", "Robin", "Cardinal", "Blue Jay", "Hawk", "Owl", "Parrot", "Penguin", "Flamingo", "Hummingbird", "Woodpecker", "Crow", "Swan", "Peacock"),
        "ocean_animals" to listOf("Whale", "Dolphin", "Shark", "Octopus", "Seahorse", "Jellyfish", "Starfish", "Crab", "Lobster", "Turtle", "Seal", "Stingray", "Barracuda", "Angelfish", "Clownfish"),

        // Entertainment
        "musical_instruments" to listOf("Piano", "Guitar", "Violin", "Drums", "Trumpet", "Saxophone", "Flute", "Cello", "Clarinet", "Trombone", "Harp", "Banjo", "Accordion", "Mandolin", "Xylophone"),
        "movie_genres" to listOf("Action", "Comedy", "Drama", "Horror", "Romance", "Thriller", "Adventure", "Animation", "Documentary", "Fantasy", "Mystery", "Musical", "Western", "Biography", "Crime"),
        "sports" to listOf("Soccer", "Basketball", "Tennis", "Baseball", "Football", "Swimming", "Golf", "Boxing", "Running", "Cycling", "Volleyball", "Hockey", "Cricket", "Rugby", "Badminton"),

        // Education & Science
        "school_subjects" to listOf("Math", "Science", "History", "English", "Art", "Music", "Geography", "Physics", "Chemistry", "Biology", "Economics", "Psychology", "Philosophy", "Literature", "Statistics"),
        "planets" to listOf("Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto", "Ceres", "Eris", "Makemake", "Haumea", "Sedna", "Quaoar"),
        "chemical_elements" to listOf("Hydrogen", "Helium", "Lithium", "Carbon", "Nitrogen", "Oxygen", "Fluorine", "Neon", "Sodium", "Magnesium", "Aluminum", "Silicon", "Phosphorus", "Sulfur", "Chlorine")
    )

    // Interference task types with varying complexity
    private val interferenceTypes = listOf(
        "number_sort",
        "word_alphabetize",
        "simple_math",
        "color_sequence",
        "pattern_match",
        "category_sort"
    )

    fun generatePuzzle(difficulty: String, context: Context): Pair<String, String> {
        val puzzleData = createContextSwitchPuzzle(difficulty)
        val answerData = createAnswerData(puzzleData)

        return Pair(puzzleData, answerData)
    }

    private fun createContextSwitchPuzzle(difficulty: String): String {
        // Select random content category
        val category = contentDatabase.keys.random()
        val allItems = contentDatabase[category] ?: contentDatabase.values.first()

        // Determine complexity based on difficulty
        val complexity = when (difficulty.lowercase()) {
            "easy" -> PuzzleComplexity(
                memoryItemCount = 4,
                distractorCount = 2,
                interferenceComplexity = "simple",
                totalChoices = 6
            )
            "medium" -> PuzzleComplexity(
                memoryItemCount = 5,
                distractorCount = 3,
                interferenceComplexity = "moderate",
                totalChoices = 8
            )
            "hard" -> PuzzleComplexity(
                memoryItemCount = 6,
                distractorCount = 4,
                interferenceComplexity = "complex",
                totalChoices = 10
            )
            else -> PuzzleComplexity(5, 3, "moderate", 8)
        }

        // Generate memory items
        val memoryItems = allItems.shuffled().take(complexity.memoryItemCount)

        // Generate smart distractors (same category but not in memory list)
        val availableDistractors = allItems.filter { it !in memoryItems }
        val distractors = availableDistractors.shuffled().take(complexity.distractorCount)

        // Create recognition list (memory + distractors, shuffled)
        val recognitionItems = (memoryItems + distractors).shuffled()

        // Generate interference task
        val interferenceTask = createInterferenceTask(complexity.interferenceComplexity)

        // Build JSON
        return JSONObject().apply {
            put("puzzleType", "context_switch")
            put("category", category)
            put("difficulty", difficulty)
            put("memoryItems", JSONArray(memoryItems))
            put("recognitionItems", JSONArray(recognitionItems))
            put("correctAnswers", JSONArray(memoryItems))
            put("interferenceTask", JSONObject().apply {
                put("type", interferenceTask.type)
                put("items", JSONArray(interferenceTask.items))
                put("instruction", interferenceTask.instruction)
                put("complexity", complexity.interferenceComplexity)
            })
            put("metadata", JSONObject().apply {
                put("memoryItemCount", complexity.memoryItemCount)
                put("distractorCount", complexity.distractorCount)
                put("totalChoices", complexity.totalChoices)
                put("categoryName", category.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                })
            })
        }.toString()
    }

    private fun createInterferenceTask(complexity: String): InterferenceTaskData {
        val taskType = interferenceTypes.random()

        return when (taskType) {
            "number_sort" -> createNumberSortTask(complexity)
            "word_alphabetize" -> createWordAlphabetizeTask(complexity)
            "simple_math" -> createMathTask(complexity)
            "color_sequence" -> createColorSequenceTask(complexity)
            "pattern_match" -> createPatternMatchTask(complexity)
            "category_sort" -> createCategorySortTask(complexity)
            else -> createNumberSortTask(complexity)
        }
    }

    private fun createNumberSortTask(complexity: String): InterferenceTaskData {
        val itemCount = when (complexity) {
            "simple" -> 4
            "moderate" -> 5
            "complex" -> 6
            else -> 5
        }

        val range = when (complexity) {
            "simple" -> 1..20
            "moderate" -> 1..50
            "complex" -> 1..100
            else -> 1..50
        }

        val numbers = range.shuffled().take(itemCount)

        return InterferenceTaskData(
            type = "number_sort",
            items = numbers.map { it.toString() },
            instruction = "Sort these numbers from smallest to largest"
        )
    }

    private fun createWordAlphabetizeTask(complexity: String): InterferenceTaskData {
        val wordSets = mapOf(
            "simple" to listOf("cat", "dog", "bird", "fish", "bear", "wolf"),
            "moderate" to listOf("elephant", "giraffe", "kangaroo", "butterfly", "dragonfly", "hedgehog"),
            "complex" to listOf("chameleon", "rhinoceros", "chimpanzee", "hippopotamus", "orangutan", "crocodile")
        )

        val words = wordSets[complexity] ?: wordSets["moderate"]!!
        val selectedWords = words.shuffled().take(if (complexity == "complex") 6 else 5)

        return InterferenceTaskData(
            type = "word_alphabetize",
            items = selectedWords,
            instruction = "Arrange these words in alphabetical order"
        )
    }

    private fun createMathTask(complexity: String): InterferenceTaskData {
        val problemCount = when (complexity) {
            "simple" -> 3
            "moderate" -> 4
            "complex" -> 5
            else -> 4
        }

        val problems = (1..problemCount).map {
            when (complexity) {
                "simple" -> {
                    val a = Random.nextInt(1, 15)
                    val b = Random.nextInt(1, 10)
                    val op = listOf("+", "-").random()
                    "$a $op $b"
                }
                "moderate" -> {
                    val a = Random.nextInt(10, 30)
                    val b = Random.nextInt(1, 15)
                    val op = listOf("+", "-", "×").random()
                    "$a $op $b"
                }
                "complex" -> {
                    val a = Random.nextInt(15, 50)
                    val b = Random.nextInt(2, 12)
                    val op = listOf("+", "-", "×", "÷").random()
                    if (op == "÷") {
                        val dividend = a * b  // Ensure clean division
                        "$dividend $op $b"
                    } else {
                        "$a $op $b"
                    }
                }
                else -> "${Random.nextInt(10, 30)} + ${Random.nextInt(1, 15)}"
            }
        }

        return InterferenceTaskData(
            type = "simple_math",
            items = problems,
            instruction = "Solve these math problems"
        )
    }

    private fun createColorSequenceTask(complexity: String): InterferenceTaskData {
        val colorSets = mapOf(
            "simple" to listOf("Red", "Blue", "Green", "Yellow"),
            "moderate" to listOf("Red", "Orange", "Yellow", "Green", "Blue"),
            "complex" to listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple")
        )

        val colors = colorSets[complexity] ?: colorSets["moderate"]!!
        val shuffledColors = colors.shuffled()

        return InterferenceTaskData(
            type = "color_sequence",
            items = shuffledColors,
            instruction = "Arrange colors in rainbow order (ROYGBV)"
        )
    }

    private fun createPatternMatchTask(complexity: String): InterferenceTaskData {
        val shapes = listOf("Circle", "Square", "Triangle", "Diamond", "Star", "Heart")
        val selectedShapes = shapes.shuffled().take(when (complexity) {
            "simple" -> 4
            "moderate" -> 5
            "complex" -> 6
            else -> 5
        })

        return InterferenceTaskData(
            type = "pattern_match",
            items = selectedShapes,
            instruction = "Arrange by number of sides (fewest to most)"
        )
    }

    private fun createCategorySortTask(complexity: String): InterferenceTaskData {
        val mixedItems = when (complexity) {
            "simple" -> listOf("Apple", "Dog", "Car", "Book", "Cat", "Orange")
            "moderate" -> listOf("Banana", "Horse", "Truck", "Pencil", "Fish", "Grape", "Bike", "Paper")
            "complex" -> listOf("Strawberry", "Elephant", "Airplane", "Calculator", "Dolphin", "Pineapple", "Train", "Notebook", "Lion", "Helicopter")
            else -> listOf("Apple", "Dog", "Car", "Book", "Cat", "Orange")
        }

        return InterferenceTaskData(
            type = "category_sort",
            items = mixedItems.shuffled(),
            instruction = "Group items by category (tap to group)"
        )
    }

    private fun createAnswerData(puzzleDataJson: String): String {
        val puzzleData = JSONObject(puzzleDataJson)
        val correctAnswers = puzzleData.getJSONArray("correctAnswers")

        return JSONObject().apply {
            put("correctAnswers", correctAnswers)
            put("puzzleType", "context_switch")
            put("category", puzzleData.getString("category"))
        }.toString()
    }

    // Data classes for internal use
    private data class PuzzleComplexity(
        val memoryItemCount: Int,
        val distractorCount: Int,
        val interferenceComplexity: String,
        val totalChoices: Int
    )

    private data class InterferenceTaskData(
        val type: String,
        val items: List<String>,
        val instruction: String
    )

    // Public method for testing difficulty progression
    fun testDifficultyProgression(): Map<String, Any> {
        val difficulties = listOf("easy", "medium", "hard")
        val results = mutableMapOf<String, Any>()

        difficulties.forEach { difficulty ->
            val (questionJson, answerJson) = generatePuzzle(difficulty, null!!)
            val puzzleData = JSONObject(questionJson)
            val metadata = puzzleData.getJSONObject("metadata")

            results[difficulty] = mapOf(
                "memoryItems" to metadata.getInt("memoryItemCount"),
                "distractors" to metadata.getInt("distractorCount"),
                "totalChoices" to metadata.getInt("totalChoices"),
                "category" to metadata.getString("categoryName"),
                "interferenceType" to puzzleData.getJSONObject("interferenceTask").getString("type"),
                "interferenceComplexity" to puzzleData.getJSONObject("interferenceTask").getString("complexity")
            )
        }

        return results
    }
}