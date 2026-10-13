package com.kreativekoala.riddleverse

import org.json.JSONObject
import java.util.Random

/** Hand-written category word lists for Letter Balloons (original content, no third-party source). */
object LetterBalloonsData {
    val categories: Map<String, List<String>> = linkedMapOf(
        "Animals" to listOf("cat", "dog", "horse", "zebra", "tiger", "camel", "otter", "koala", "panda", "rabbit", "monkey",
            "giraffe", "dolphin", "penguin", "buffalo", "leopard", "hedgehog", "elephant", "kangaroo", "crocodile",
            "chameleon", "porcupine", "armadillo", "flamingo", "squirrel", "octopus", "gorilla", "cheetah", "hamster", "lobster"),
        "Food and Drink" to listOf("bread", "pasta", "pizza", "salad", "apple", "mango", "lemon", "honey", "cheese", "banana",
            "burrito", "noodles", "pancake", "sandwich", "broccoli", "pretzel", "cinnamon", "avocado", "chocolate", "spaghetti",
            "pineapple", "blueberry", "croissant", "lemonade", "dumpling", "cucumber", "mushroom", "cranberry", "vinegar", "tortilla"),
        "Countries" to listOf("peru", "chad", "cuba", "italy", "japan", "india", "spain", "kenya", "egypt", "norway", "brazil",
            "canada", "mexico", "ireland", "germany", "vietnam", "morocco", "portugal", "thailand", "argentina", "australia",
            "indonesia", "venezuela", "singapore", "switzerland", "madagascar", "bangladesh", "luxembourg", "new zealand", "south africa"),
        "Sports" to listOf("golf", "polo", "judo", "rugby", "tennis", "soccer", "hockey", "boxing", "diving", "rowing", "archery",
            "cycling", "fencing", "curling", "cricket", "karate", "skiing", "bowling", "baseball", "swimming", "wrestling",
            "volleyball", "basketball", "gymnastics", "snowboarding", "badminton", "marathon", "triathlon", "skateboarding", "weightlifting"),
        "Space" to listOf("moon", "mars", "star", "orbit", "comet", "venus", "earth", "saturn", "galaxy", "meteor", "planet",
            "nebula", "rocket", "pulsar", "eclipse", "neptune", "asteroid", "telescope", "satellite", "universe", "astronaut",
            "black hole", "milky way", "supernova", "gravity", "cosmos", "jupiter", "mercury", "constellation", "spacecraft"),
        "Around the House" to listOf("door", "lamp", "sofa", "table", "chair", "mirror", "pillow", "carpet", "window", "kitchen",
            "blanket", "curtain", "toaster", "bathtub", "bookcase", "bedroom", "doorbell", "staircase", "wardrobe", "microwave",
            "dishwasher", "television", "refrigerator", "bookshelf", "chandelier", "fireplace", "cupboard", "hallway", "basement", "balcony"),
        "Nature" to listOf("tree", "rain", "river", "cloud", "ocean", "storm", "forest", "meadow", "desert", "volcano", "glacier",
            "thunder", "waterfall", "mountain", "rainbow", "sunshine", "blizzard", "hurricane", "wildflower", "earthquake",
            "canyon", "island", "valley", "lagoon", "tundra", "prairie", "jungle", "coral reef", "tornado", "avalanche"),
        "Jobs" to listOf("chef", "pilot", "nurse", "baker", "tailor", "doctor", "farmer", "artist", "writer", "teacher", "plumber",
            "dentist", "painter", "lawyer", "builder", "musician", "engineer", "scientist", "carpenter", "librarian", "architect",
            "firefighter", "electrician", "photographer", "veterinarian", "journalist", "accountant", "astronomer", "pharmacist", "mechanic"),
        "Music" to listOf("drum", "harp", "flute", "piano", "organ", "tuba", "violin", "guitar", "melody", "rhythm", "chorus",
            "trumpet", "cello", "ukulele", "clarinet", "saxophone", "orchestra", "symphony", "harmonica", "trombone", "accordion",
            "keyboard", "concert", "lullaby", "opera", "harmony", "soprano", "bassoon", "tambourine", "xylophone"),
        "Travel" to listOf("map", "ticket", "hotel", "beach", "plane", "train", "luggage", "passport", "airport", "tourist",
            "backpack", "compass", "voyage", "harbor", "suitcase", "itinerary", "souvenir", "adventure", "campsite", "highway",
            "cruise ship", "road trip", "departure", "customs", "boarding", "journey", "hostel", "terminal", "excursion", "destination"),
        "Weather and Seasons" to listOf("snow", "wind", "fog", "frost", "sunny", "windy", "foggy", "autumn", "winter", "summer",
            "spring", "drizzle", "monsoon", "blizzard", "humidity", "rainfall", "overcast", "heatwave", "snowflake", "forecast",
            "lightning", "temperature", "thunderstorm", "sunrise", "sunset", "equinox", "solstice", "breeze", "downpour", "hailstorm"),
        "Colors and Shapes" to listOf("red", "blue", "green", "pink", "circle", "square", "violet", "orange", "yellow", "purple",
            "crimson", "triangle", "rectangle", "hexagon", "pentagon", "turquoise", "magenta", "diamond", "octagon", "cylinder",
            "burgundy", "lavender", "scarlet", "emerald", "indigo", "ellipse", "spiral", "crescent", "trapezoid", "rhombus")
    )

    fun tier(word: String): String {
        val n = word.count { it != ' ' }
        return when {
            n <= 6 -> "easy"
            n <= 8 -> "medium"
            else -> "hard"
        }
    }

    fun lives(difficulty: String) = when (difficulty.lowercase()) { "easy" -> 8; "hard" -> 6; else -> 7 }
}

/** Pure Letter Balloons (hangman-style) rules. Spaces in a phrase are shown, never guessed. */
data class LetterBalloonsGame(
    val word: String,
    val category: String,
    val maxLives: Int,
    val guessed: Set<Char> = emptySet(),
    val hintsUsed: Int = 0
) {
    val wrongLetters: List<Char> get() = guessed.filter { it !in word }.sorted()
    val livesLeft: Int get() = (maxLives - wrongLetters.size).coerceAtLeast(0)
    val maskedLetters: List<Char?> get() = word.map { if (it == ' ') ' ' else if (it in guessed) it else null }
    val isWon: Boolean get() = word.all { it == ' ' || it in guessed }
    val isLost: Boolean get() = !isWon && livesLeft == 0
    val isOver: Boolean get() = isWon || isLost

    fun guess(c: Char): LetterBalloonsGame {
        val ch = c.lowercaseChar()
        if (isOver || ch !in 'a'..'z' || ch in guessed) return this
        return copy(guessed = guessed + ch)
    }

    /** Reveals one unguessed letter (most frequent in the word first, ties alphabetical). Costs score, not a balloon. */
    fun hint(): LetterBalloonsGame {
        if (isOver) return this
        val hidden = word.filter { it != ' ' && it !in guessed }
        if (hidden.isEmpty()) return this
        val pick = hidden.groupBy { it }.entries.sortedWith(compareBy({ -it.value.size }, { it.key })).first().key
        return copy(guessed = guessed + pick, hintsUsed = hintsUsed + 1)
    }

    fun score(): Int {
        if (!isWon) return 0
        return (50 + livesLeft * 10 - hintsUsed * 20).coerceAtLeast(10)
    }
}

object LetterBalloonsGenerator {
    fun generate(difficulty: String, seed: Long): JSONObject {
        val rnd = Random(seed)
        val cats = LetterBalloonsData.categories.keys.toList()
        val cat = cats[rnd.nextInt(cats.size)]
        val all = LetterBalloonsData.categories.getValue(cat)
        val tier = difficulty.lowercase().let { if (it == "easy" || it == "hard") it else "medium" }
        val words = all.filter { LetterBalloonsData.tier(it) == tier }.ifEmpty { all }
        val word = words[rnd.nextInt(words.size)]
        return JSONObject().apply {
            put("game", "letterballoons")
            put("word", word); put("category", cat)
            put("lives", LetterBalloonsData.lives(difficulty))
            put("seed", seed); put("difficulty", difficulty)
        }
    }
}
