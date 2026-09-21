package com.kreativekoala.riddleverse


import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.*

// Data classes for geography puzzle
enum class Continent(val displayName: String, val color: Color) {
    NORTH_AMERICA("North America", RvSuccess),
    SOUTH_AMERICA("South America", RvSun),
    EUROPE("Europe", RvSky),
    AFRICA("Africa", Color(0xFFE91E63)),
    ASIA("Asia", RvGrape),
    OCEANIA("Oceania", RvSky)
}

data class GeographyCity(
    val id: String,
    val name: String,
    val country: String,
    val continent: Continent,
    val latitude: Double,
    val longitude: Double,
    val isCapital: Boolean,
    val flag: String
)

data class ContinentBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

enum class GameStage {
    INSTRUCTIONS,
    CONTINENT_SELECTION,
    PRECISE_PLACEMENT,
    GRID_TESTING,
    COMPLETED
}

data class PlacementResult(
    val score: Int,
    val distance: Double,
    val accuracy: String
)

// Sample 50 major cities database
object GeographyCityDatabase {
    val cities = listOf(
        // NORTH AMERICA (10 cities)
        GeographyCity("new_york", "New York", "United States", Continent.NORTH_AMERICA, 40.7128, -74.0060, false, "🇺🇸"),
        GeographyCity("los_angeles", "Los Angeles", "United States", Continent.NORTH_AMERICA, 34.0522, -118.2437, false, "🇺🇸"),
        GeographyCity("mexico_city", "Mexico City", "Mexico", Continent.NORTH_AMERICA, 19.4326, -99.1332, true, "🇲🇽"),
        GeographyCity("toronto", "Toronto", "Canada", Continent.NORTH_AMERICA, 43.6532, -79.3832, false, "🇨🇦"),
        GeographyCity("chicago", "Chicago", "United States", Continent.NORTH_AMERICA, 41.8781, -87.6298, false, "🇺🇸"),
        GeographyCity("washington_dc", "Washington DC", "United States", Continent.NORTH_AMERICA, 38.9072, -77.0369, true, "🇺🇸"),
        GeographyCity("vancouver", "Vancouver", "Canada", Continent.NORTH_AMERICA, 49.2827, -123.1207, false, "🇨🇦"),
        GeographyCity("miami", "Miami", "United States", Continent.NORTH_AMERICA, 25.7617, -80.1918, false, "🇺🇸"),
        GeographyCity("montreal", "Montreal", "Canada", Continent.NORTH_AMERICA, 45.5017, -73.5673, false, "🇨🇦"),
        GeographyCity("guatemala_city", "Guatemala City", "Guatemala", Continent.NORTH_AMERICA, 14.6349, -90.5069, true, "🇬🇹"),

        // SOUTH AMERICA (8 cities)
        GeographyCity("sao_paulo", "São Paulo", "Brazil", Continent.SOUTH_AMERICA, -23.5505, -46.6333, false, "🇧🇷"),
        GeographyCity("rio_janeiro", "Rio de Janeiro", "Brazil", Continent.SOUTH_AMERICA, -22.9068, -43.1729, false, "🇧🇷"),
        GeographyCity("buenos_aires", "Buenos Aires", "Argentina", Continent.SOUTH_AMERICA, -34.6118, -58.3960, true, "🇦🇷"),
        GeographyCity("lima", "Lima", "Peru", Continent.SOUTH_AMERICA, -12.0464, -77.0428, true, "🇵🇪"),
        GeographyCity("bogota", "Bogotá", "Colombia", Continent.SOUTH_AMERICA, 4.7110, -74.0721, true, "🇨🇴"),
        GeographyCity("santiago", "Santiago", "Chile", Continent.SOUTH_AMERICA, -33.4489, -70.6693, true, "🇨🇱"),
        GeographyCity("caracas", "Caracas", "Venezuela", Continent.SOUTH_AMERICA, 10.4806, -66.9036, true, "🇻🇪"),
        GeographyCity("quito", "Quito", "Ecuador", Continent.SOUTH_AMERICA, -0.1807, -78.4678, true, "🇪🇨"),

        // EUROPE (12 cities)
        GeographyCity("london", "London", "United Kingdom", Continent.EUROPE, 51.5074, -0.1278, true, "🇬🇧"),
        GeographyCity("paris", "Paris", "France", Continent.EUROPE, 48.8566, 2.3522, true, "🇫🇷"),
        GeographyCity("berlin", "Berlin", "Germany", Continent.EUROPE, 52.5200, 13.4050, true, "🇩🇪"),
        GeographyCity("rome", "Rome", "Italy", Continent.EUROPE, 41.9028, 12.4964, true, "🇮🇹"),
        GeographyCity("madrid", "Madrid", "Spain", Continent.EUROPE, 40.4168, -3.7038, true, "🇪🇸"),
        GeographyCity("amsterdam", "Amsterdam", "Netherlands", Continent.EUROPE, 52.3702, 4.8952, true, "🇳🇱"),
        GeographyCity("vienna", "Vienna", "Austria", Continent.EUROPE, 48.2082, 16.3738, true, "🇦🇹"),
        GeographyCity("stockholm", "Stockholm", "Sweden", Continent.EUROPE, 59.3293, 18.0686, true, "🇸🇪"),
        GeographyCity("prague", "Prague", "Czech Republic", Continent.EUROPE, 50.0755, 14.4378, true, "🇨🇿"),
        GeographyCity("barcelona", "Barcelona", "Spain", Continent.EUROPE, 41.3851, 2.1734, false, "🇪🇸"),
        GeographyCity("moscow", "Moscow", "Russia", Continent.EUROPE, 55.7558, 37.6173, true, "🇷🇺"),
        GeographyCity("athens", "Athens", "Greece", Continent.EUROPE, 37.9755, 23.7348, true, "🇬🇷"),

        // AFRICA (8 cities)
        GeographyCity("cairo", "Cairo", "Egypt", Continent.AFRICA, 30.0444, 31.2357, true, "🇪🇬"),
        GeographyCity("lagos", "Lagos", "Nigeria", Continent.AFRICA, 6.5244, 3.3792, false, "🇳🇬"),
        GeographyCity("johannesburg", "Johannesburg", "South Africa", Continent.AFRICA, -26.2041, 28.0473, false, "🇿🇦"),
        GeographyCity("casablanca", "Casablanca", "Morocco", Continent.AFRICA, 33.5731, -7.5898, false, "🇲🇦"),
        GeographyCity("nairobi", "Nairobi", "Kenya", Continent.AFRICA, -1.2864, 36.8172, true, "🇰🇪"),
        GeographyCity("addis_ababa", "Addis Ababa", "Ethiopia", Continent.AFRICA, 9.1450, 38.7451, true, "🇪🇹"),
        GeographyCity("cape_town", "Cape Town", "South Africa", Continent.AFRICA, -33.9249, 18.4241, false, "🇿🇦"),
        GeographyCity("algiers", "Algiers", "Algeria", Continent.AFRICA, 36.7378, 3.0869, true, "🇩🇿"),

        // ASIA (10 cities)
        GeographyCity("tokyo", "Tokyo", "Japan", Continent.ASIA, 35.6762, 139.6503, true, "🇯🇵"),
        GeographyCity("beijing", "Beijing", "China", Continent.ASIA, 39.9042, 116.4074, true, "🇨🇳"),
        GeographyCity("mumbai", "Mumbai", "India", Continent.ASIA, 19.0760, 72.8777, false, "🇮🇳"),
        GeographyCity("shanghai", "Shanghai", "China", Continent.ASIA, 31.2304, 121.4737, false, "🇨🇳"),
        GeographyCity("delhi", "Delhi", "India", Continent.ASIA, 28.7041, 77.1025, true, "🇮🇳"),
        GeographyCity("seoul", "Seoul", "South Korea", Continent.ASIA, 37.5665, 126.9780, true, "🇰🇷"),
        GeographyCity("bangkok", "Bangkok", "Thailand", Continent.ASIA, 13.7563, 100.5018, true, "🇹🇭"),
        GeographyCity("singapore", "Singapore", "Singapore", Continent.ASIA, 1.3521, 103.8198, true, "🇸🇬"),
        GeographyCity("hong_kong", "Hong Kong", "Hong Kong", Continent.ASIA, 22.3193, 114.1694, false, "🇭🇰"),
        GeographyCity("kuala_lumpur", "Kuala Lumpur", "Malaysia", Continent.ASIA, 3.1390, 101.6869, true, "🇲🇾"),

        // OCEANIA (2 cities)
        GeographyCity("sydney", "Sydney", "Australia", Continent.OCEANIA, -33.8688, 151.2093, false, "🇦🇺"),
        GeographyCity("melbourne", "Melbourne", "Australia", Continent.OCEANIA, -37.8136, 144.9631, false, "🇦🇺")
    )

    fun getRandomCity(): GeographyCity = cities.random()

    fun getCitiesByContinent(continent: Continent): List<GeographyCity> {
        return cities.filter { it.continent == continent }
    }
}

// Continent bounds for zoomed view
object GeographyContinentBounds {
    val bounds = mapOf(
        Continent.NORTH_AMERICA to ContinentBounds(71.0, 7.0, -30.0, -168.0),
        Continent.SOUTH_AMERICA to ContinentBounds(12.0, -55.0, -34.0, -81.0),
        Continent.EUROPE to ContinentBounds(71.0, 35.0, 40.0, -25.0),
        Continent.AFRICA to ContinentBounds(37.0, -35.0, 51.0, -17.0),
        Continent.ASIA to ContinentBounds(81.0, -10.0, 180.0, 27.0),
        Continent.OCEANIA to ContinentBounds(10.0, -55.0, 180.0, 110.0)
    )
}

// Data classes for grid system
data class GridCell(
    val row: Int,
    val col: Int,
    val id: String = "${row}_${col}"
)

// UPDATED GeographyGridSystem with GPT-4 Vision analyzed mappings
object GeographyGridSystem {
    // Use consistent 6x8 grid for all continents (from the generated data)
    private val gridDimensions = mapOf(
        Continent.NORTH_AMERICA to Pair(6, 8),
        Continent.SOUTH_AMERICA to Pair(6, 8),
        Continent.EUROPE to Pair(6, 8),
        Continent.AFRICA to Pair(6, 8),
        Continent.ASIA to Pair(6, 8),
        Continent.OCEANIA to Pair(6, 8)
    )

    // Updated mapping from the generated grid data with fallbacks
    private val cityGridMapping = mapOf(
        // NORTH_AMERICA
        "vancouver" to GridCell(2, 2),
        "los_angeles" to GridCell(3, 3),
        "mexico_city" to GridCell(4, 3),
        "guatemala_city" to GridCell(4, 4),
        "chicago" to GridCell(2, 4),
        "washington_dc" to GridCell(3, 5),
        "new_york" to GridCell(3, 5),
        "toronto" to GridCell(2, 4),
        "montreal" to GridCell(2, 5),
        "miami" to GridCell(4, 5),

        // SOUTH_AMERICA
        "quito" to GridCell(1, 0),
        "bogota" to GridCell(1, 1),
        "caracas" to GridCell(0, 2),
        "lima" to GridCell(2, 1),
        "santiago" to GridCell(3, 2),
        "buenos_aires" to GridCell(3, 3),
        "sao_paulo" to GridCell(3, 5),
        "rio_janeiro" to GridCell(3, 6), // Fixed key name

        // EUROPE
        "london" to GridCell(1, 2),
        "paris" to GridCell(1, 3),
        "berlin" to GridCell(1, 4),
        "rome" to GridCell(4, 4),
        "madrid" to GridCell(4, 1),
        "amsterdam" to GridCell(3, 3),
        "vienna" to GridCell(2, 4),
        "stockholm" to GridCell(2, 5),
        "prague" to GridCell(3, 4),
        "barcelona" to GridCell(4, 3),
        "moscow" to GridCell(2, 7),
        "athens" to GridCell(5, 5),

        // AFRICA
        "cairo" to GridCell(0, 5),
        "lagos" to GridCell(2, 2),
        "johannesburg" to GridCell(4, 5),
        "casablanca" to GridCell(0, 1),
        "nairobi" to GridCell(3, 6),
        "addis_ababa" to GridCell(2, 6),
        "cape_town" to GridCell(5, 4),
        "algiers" to GridCell(0, 2),

        // ASIA
        "tokyo" to GridCell(1, 5),
        "beijing" to GridCell(2, 4),
        "mumbai" to GridCell(3, 2),
        "shanghai" to GridCell(1, 6),
        "delhi" to GridCell(3, 2),
        "seoul" to GridCell(2, 5),
        "bangkok" to GridCell(3, 4),
        "singapore" to GridCell(4, 4),
        "hong_kong" to GridCell(3, 4),
        "kuala_lumpur" to GridCell(4, 3),

        // OCEANIA
        "sydney" to GridCell(3, 4),
        "melbourne" to GridCell(4, 3)
    )

    /**
     * Get grid dimensions for a continent
     */
    fun getGridDimensions(continent: Continent): Pair<Int, Int> {
        return gridDimensions[continent] ?: Pair(6, 8)
    }

    /**
     * Get correct grid cell for a city with fallback logic
     */
    fun getCorrectGridCell(cityId: String): GridCell? {
        return cityGridMapping[cityId]
    }

    /**
     * Calculate grid-based score with more forgiving algorithm
     * Considers both exact matches and reasonable approximations
     */
    fun calculateGridScore(cityId: String, selectedGrid: GridCell): GridPlacementResult {
        val correctGrid = getCorrectGridCell(cityId)

        if (correctGrid == null) {
            return GridPlacementResult(0, "City not found", false)
        }

        // Calculate Manhattan distance between grids
        val rowDiff = kotlin.math.abs(selectedGrid.row - correctGrid.row)
        val colDiff = kotlin.math.abs(selectedGrid.col - correctGrid.col)
        val totalDistance = rowDiff + colDiff

        // More forgiving scoring system
        val (score, accuracy, isCorrect) = when (totalDistance) {
            0 -> Triple(50, "Perfect!", true)           // Exact match
            1 -> Triple(45, "Excellent!", false)       // Adjacent cell - very close
            2 -> Triple(35, "Very Good!", false)       // 2 cells away - still very good
            3 -> Triple(25, "Good!", false)            // 3 cells away - decent
            4 -> Triple(20, "Close!", false)           // 4 cells away - getting there
            5 -> Triple(15, "Not Bad!", false)         // 5 cells away - okay attempt
            6 -> Triple(10, "Keep Trying!", false)     // 6 cells away - needs work
            else -> Triple(5, "Try Again!", false)     // 7+ cells away - participation points
        }

        return GridPlacementResult(score, accuracy, isCorrect)
    }

    /**
     * Enhanced validation that accepts near-correct answers as valid
     * Useful for making the game more accessible
     */
    fun validateCitySelection(
        continent: Continent,
        cityId: String,
        selectedCell: GridCell,
        toleranceLevel: Int = 1 // Allow 1 cell tolerance by default
    ): Boolean {
        val correctCell = getCorrectGridCell(cityId) ?: return false

        val rowDiff = kotlin.math.abs(selectedCell.row - correctCell.row)
        val colDiff = kotlin.math.abs(selectedCell.col - correctCell.col)
        val totalDistance = rowDiff + colDiff

        return totalDistance <= toleranceLevel
    }

    /**
     * Get cities for a continent - ensures all cities are mappable
     */
    fun getCitiesForContinent(continent: Continent): List<String> {
        return GeographyCityDatabase.cities
            .filter { it.continent == continent }
            .mapNotNull { city ->
                if (getCorrectGridCell(city.id) != null) city.id else null
            }
    }

    /**
     * Get hint cells around the correct location
     * Helps players who are struggling
     */
    fun getCityHints(cityId: String, continent: Continent): List<GridCell> {
        val correctCell = getCorrectGridCell(cityId) ?: return emptyList()
        val (rows, cols) = getGridDimensions(continent)
        val hints = mutableListOf<GridCell>()

        // Add cells in a cross pattern around the correct location
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1) // Up, Down, Left, Right

        directions.forEach { (dr, dc) ->
            val newRow = correctCell.row + dr
            val newCol = correctCell.col + dc
            if (newRow in 0 until rows && newCol in 0 until cols) {
                hints.add(GridCell(newRow, newCol))
            }
        }

        return hints.shuffled().take(2) // Return 2 random hint cells
    }

    /**
     * Check if a cell contains any city (useful for testing mode)
     */
    fun getCellCity(continent: Continent, cell: GridCell): String? {
        return getCitiesForContinent(continent).find { cityId ->
            getCorrectGridCell(cityId)?.let { correctCell ->
                correctCell.row == cell.row && correctCell.col == cell.col
            } ?: false
        }
    }

    /**
     * Get alternative acceptable cells for a city (makes game more forgiving)
     * Returns cells that should be considered "close enough" for less experienced players
     */
    fun getAcceptableCells(cityId: String, toleranceRadius: Int = 1): List<GridCell> {
        val correctCell = getCorrectGridCell(cityId) ?: return emptyList()
        val acceptableCells = mutableListOf<GridCell>()

        for (dr in -toleranceRadius..toleranceRadius) {
            for (dc in -toleranceRadius..toleranceRadius) {
                val newRow = correctCell.row + dr
                val newCol = correctCell.col + dc
                // Only add valid grid positions
                if (newRow >= 0 && newCol >= 0) {
                    acceptableCells.add(GridCell(newRow, newCol))
                }
            }
        }

        return acceptableCells
    }

    /**
     * Adaptive difficulty scoring based on player performance
     * Call this to adjust tolerance based on how well the player is doing
     */
    fun calculateAdaptiveScore(
        cityId: String,
        selectedGrid: GridCell,
        playerSuccessRate: Double // 0.0 to 1.0
    ): GridPlacementResult {
        val baseResult = calculateGridScore(cityId, selectedGrid)

        // If player is struggling (success rate < 0.4), be more generous
        if (playerSuccessRate < 0.4 && baseResult.score < 25) {
            val bonusScore = kotlin.math.min(10, (25 - baseResult.score) / 2)
            return GridPlacementResult(
                score = baseResult.score + bonusScore,
                accuracy = "Good Effort! (Bonus: +$bonusScore)",
                isCorrect = baseResult.isCorrect
            )
        }

        return baseResult
    }
}

// Enhanced data class with better feedback
data class GridPlacementResult(
    val score: Int,
    val accuracy: String,
    val isCorrect: Boolean,
    val distanceFromCorrect: Int = 0, // Manhattan distance for reference
    val encouragement: String = "" // Optional encouraging message
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GeographyDragDropPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "GeographyDragDrop"

    // Game state
    var gameStage by remember { mutableStateOf(GameStage.INSTRUCTIONS) }
    var currentCity by remember { mutableStateOf<GeographyCity?>(null) }
    var selectedContinent by remember { mutableStateOf<Continent?>(null) }
    var currentScore by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var questionsAnswered by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<PlacementResult?>(null) }

    // Drag state (only used for precise placement)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    // Timer state
    val maxQuestions = when (difficulty.lowercase()) {
        "easy" -> 8
        "medium" -> 12
        "hard" -> 15
        else -> 10
    }
    var timeRemaining by remember { mutableStateOf(maxQuestions * 30) } // 30 seconds per question

    LaunchedEffect(gameStage) {
        if (gameStage == GameStage.CONTINENT_SELECTION || gameStage == GameStage.PRECISE_PLACEMENT) {
            while (timeRemaining > 0 && gameStage != GameStage.COMPLETED) {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0) {
                gameStage = GameStage.COMPLETED
            }
        }
    }

    // Generate new city when starting or moving to next question
    LaunchedEffect(gameStage, questionsAnswered) {
        if (gameStage == GameStage.CONTINENT_SELECTION && currentCity == null) {
            currentCity = GeographyCityDatabase.getRandomCity()
            Log.d(TAG, "New city: ${currentCity?.name} in ${currentCity?.continent?.displayName}")
        }
    }

    // Helper function to move to next question
    fun moveToNextQuestion() {
        questionsAnswered++
        if (questionsAnswered >= maxQuestions) {
            gameStage = GameStage.COMPLETED
        } else {
            currentCity = GeographyCityDatabase.getRandomCity()
            selectedContinent = null
            gameStage = GameStage.CONTINENT_SELECTION
            currentScore = 0
        }
    }

    val backLabel = stringResource(R.string.back)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header (only show during gameplay, not in testing mode)
        if (gameStage != GameStage.GRID_TESTING) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RvSky)
                        .clickable { onBack() }
                        .semantics { contentDescription = backLabel }
                        .zIndex(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("||", color = RvInk, fontWeight = FontWeight.Bold)
                }

                // Timer and Score (only show during active gameplay)
                if (gameStage != GameStage.INSTRUCTIONS && gameStage != GameStage.GRID_TESTING) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RvSurface)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "TIME ${String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60)}",
                                color = RvInk,
                                fontSize = 14.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RvSurface)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "SCORE $totalScore",
                                color = RvInk,
                                fontSize = 14.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Empty space to maintain layout balance
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(modifier = Modifier.weight(1f).widthIn(max = 1000.dp).fillMaxWidth()) {
        when (gameStage) {
            GameStage.INSTRUCTIONS -> {
                GeographyInstructionsScreen(
                    onStartGame = {
                        gameStage = GameStage.CONTINENT_SELECTION
                        Log.d(TAG, "Starting geography game")
                    },
                    onEnterTestingMode = {
                        gameStage = GameStage.GRID_TESTING
                        Log.d(TAG, "Entering grid testing mode")
                    }
                )
            }

            GameStage.GRID_TESTING -> {
                GeographyGridTestingScreen(
                    onBack = {
                        gameStage = GameStage.INSTRUCTIONS
                    },
                    onExportMappings = { mappingCode ->
                        // Handle the generated code - you could copy to clipboard,
                        // log it, or display in a dialog
                        Log.d(TAG, "Generated mapping code:\n$mappingCode")

                        // Optional: Show the code in a dialog
                        // You might want to add a state variable to show/hide a dialog with the code
                    }
                )
            }

            GameStage.CONTINENT_SELECTION -> {
                currentCity?.let { city ->
                    ContinentSelectionScreen(
                        city = city,
                        showFeedback = showFeedback,
                        feedbackMessage = feedbackMessage,
                        questionsAnswered = questionsAnswered,
                        maxQuestions = maxQuestions,
                        onContinentSelected = { continentSelected ->
                            if (continentSelected == city.continent) {
                                // Correct continent
                                currentScore += 25
                                totalScore += 25
                                selectedContinent = continentSelected
                                feedbackMessage = "Correct continent! Now place it precisely."
                                showFeedback = true

                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(1500)
                                    showFeedback = false
                                    gameStage = GameStage.PRECISE_PLACEMENT
                                }
                            } else {
                                // Wrong continent
                                feedbackMessage = "Wrong continent. ${city.name} is in ${city.continent.displayName}."
                                showFeedback = true

                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(2000)
                                    showFeedback = false
                                    moveToNextQuestion()
                                }
                            }
                        }
                    )
                }
            }

            GameStage.PRECISE_PLACEMENT -> {
                currentCity?.let { city ->
                    selectedContinent?.let { continent ->
                        PrecisePlacementScreen(
                            city = city,
                            continent = continent,
                            dragOffset = dragOffset,
                            isDragging = isDragging,
                            showFeedback = showFeedback,
                            lastResult = lastResult,
                            questionsAnswered = questionsAnswered,
                            maxQuestions = maxQuestions,
                            onDragStart = {
                                isDragging = true
                                dragOffset = Offset.Zero
                            },
                            onDragEnd = { userLat, userLng ->
                                isDragging = false
                                dragOffset = Offset.Zero

                                // Use the new grid-based scoring system
                                // You'll need to update this to use GridPlacementResult instead
                                val result = calculatePlacementScore(city, userLat, userLng)
                                lastResult = result
                                currentScore += result.score
                                totalScore += result.score
                                showFeedback = true

                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(2500)
                                    showFeedback = false
                                    moveToNextQuestion()
                                }
                            },
                            onDragUpdate = { offset ->
                                dragOffset = offset
                            }
                        )
                    }
                }
            }

            GameStage.COMPLETED -> {
                GeographyCompletionScreen(
                    totalScore = totalScore,
                    questionsAnswered = questionsAnswered,
                    maxQuestions = maxQuestions,
                    onContinue = {
                        val finalScore = (totalScore.toDouble() / (maxQuestions * 75).toDouble()) * 100
                        onSubmitAnswer(finalScore >= 60)
                        fetchNextPuzzle(finalScore.toInt())
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun GeographyInstructionsScreen(
    onStartGame: () -> Unit,
    onEnterTestingMode: () -> Unit = {} // Add this parameter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Geography Challenge",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Place cities in their correct locations on the world map",
                fontSize = 16.sp,
                color = RvInkSoft.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.how_to_play),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val instructions = listOf(
                        "1. Click on the correct continent for the city",
                        "2. If correct, choose the precise grid location",
                        "3. Get points based on accuracy",
                        "4. Complete all questions before time runs out"
                    )

                    instructions.forEach { instruction ->
                        Text(
                            text = instruction,
                            fontSize = 14.sp,
                            color = RvInk,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "💡 Scoring: 25 pts for correct continent + up to 50 pts for accuracy",
                        fontSize = 12.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Buttons section
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RvSuccess)
            ) {
                Text(
                    text = stringResource(R.string.start_challenge),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }

            val showTestingMode = false
            // Testing mode button (only visible in debug builds or with a flag)
            if (showTestingMode) { // Or use a custom debug flag
                Button(
                    onClick = onEnterTestingMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                ) {
                    Text(
                        text = "GRID TESTING MODE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinentSelectionScreen(
    city: GeographyCity,
    showFeedback: Boolean,
    feedbackMessage: String,
    questionsAnswered: Int,
    maxQuestions: Int,
    onContinentSelected: (Continent) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val questionBlock: @Composable (Modifier) -> Unit = { m ->
            Row(
                modifier = m,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CityCard(city = city)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Question ${questionsAnswered + 1} of $maxQuestions",
                        color = RvInkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Which continent is ${city.name} located in?",
                        color = RvInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        val map: @Composable (Modifier) -> Unit = { m ->
            Box(modifier = m) {
                CitiesContinentMap(onContinentSelected = onContinentSelected, modifier = Modifier.fillMaxSize())
                // Feedback overlays the map so the layout never shifts
                androidx.compose.animation.AnimatedVisibility(
                    visible = showFeedback,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (feedbackMessage.contains("Correct"))
                                RvSuccess else RvError
                        )
                    ) {
                        Text(
                            text = feedbackMessage,
                            color = RvInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                questionBlock(Modifier.weight(1f))
                map(Modifier.weight(1.4f).fillMaxHeight().testTag("continent_map"))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth().align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                questionBlock(Modifier.fillMaxWidth())
                map(Modifier.weight(1f).fillMaxWidth().testTag("continent_map"))
            }
        }
    }
}


/** Fit-to-screen world map: 2x3 grid of labelled continent tiles that always fills the given area. */
@Composable
private fun CitiesContinentMap(
    onContinentSelected: (Continent) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf(Continent.NORTH_AMERICA, Continent.EUROPE, Continent.ASIA),
        listOf(Continent.SOUTH_AMERICA, Continent.AFRICA, Continent.OCEANIA)
    )
    Column(
        modifier = modifier
            .background(Color(0xFF87CEEB), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { continent ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .clickable { onContinentSelected(continent) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContinentShape(
                            continent = continent,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Text(
                            text = continent.displayName,
                            color = RvInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomedContinentView(
    continent: Continent,
    modifier: Modifier = Modifier
) {
    val imageRes = when (continent) {
        Continent.NORTH_AMERICA -> R.drawable.north_america
        Continent.SOUTH_AMERICA -> R.drawable.south_america
        Continent.EUROPE -> R.drawable.europe
        Continent.AFRICA -> R.drawable.africa
        Continent.ASIA -> R.drawable.asia
        Continent.OCEANIA -> R.drawable.oceania
    }

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "${continent.displayName} zoomed view",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(continent.color.copy(alpha = 0.1f))
        )
    }
}

// Helper function to calculate relative position of cities within continent bounds
private fun getCityRelativePosition(city: GeographyCity, continent: Continent): Offset {
    val bounds = GeographyContinentBounds.bounds[continent] ?: return Offset(0.5f, 0.5f)

    // Convert lat/lng to relative position (0.0 to 1.0)
    val relativeX = (city.longitude - bounds.west) / (bounds.east - bounds.west)
    val relativeY = (bounds.north - city.latitude) / (bounds.north - bounds.south)

    // Optional tweak for Africa: shift right slightly
    val offsetX = if (continent == Continent.AFRICA || continent == Continent.SOUTH_AMERICA) 0.05f else 0f

    val offsetY = if (continent == Continent.ASIA) 0.05f else 0f

    // Clamp values and apply shift
    val clampedX = (relativeX + offsetX).coerceIn(0.1, 0.9)
    val clampedY = (relativeY+ offsetY).coerceIn(0.1, 0.9)

    return Offset(clampedX.toFloat(), clampedY.toFloat())
}

@Composable
private fun PrecisePlacementScreen(
    city: GeographyCity,
    continent: Continent,
    dragOffset: Offset,
    isDragging: Boolean,
    showFeedback: Boolean,
    lastResult: PlacementResult?,
    questionsAnswered: Int,
    maxQuestions: Int,
    playerSuccessRate: Double = 0.5, // Pass this from your game state
    onDragStart: () -> Unit,
    onDragEnd: (Double, Double) -> Unit,
    onDragUpdate: (Offset) -> Unit
) {
    var selectedGridCell by remember { mutableStateOf<GridCell?>(null) }
    var gridResult by remember { mutableStateOf<GridPlacementResult?>(null) }
    var showHints by remember { mutableStateOf(false) }

    val (gridRows, gridCols) = GeographyGridSystem.getGridDimensions(continent)
    val correctGrid = GeographyGridSystem.getCorrectGridCell(city.id)
    val hintCells = remember(city.id) {
        GeographyGridSystem.getCityHints(city.id, continent)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val compact = maxHeight < 600.dp
        val topInfo: @Composable (Modifier) -> Unit = { m ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EnhancedCityCard(city = city, compact = compact)
                    Column(modifier = Modifier.weight(1f)) {
                        if (!compact) {
                            Text(
                                text = "Question ${questionsAnswered + 1} of $maxQuestions - Precise Placement",
                                color = RvInkSoft,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Click the grid section where ${city.name} is located",
                            color = RvInk,
                            fontSize = if (compact) 14.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Hint button (only show if player is struggling)
                if (playerSuccessRate < 0.6 && !showFeedback) {
                    Button(
                        onClick = { showHints = !showHints },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvSun,
                            contentColor = RvInk
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = if (showHints) "Hide" else "Hint",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }
                }
                if (showHints && hintCells.isNotEmpty()) {
                    Text(
                        text = "💡 ${city.name} is near the highlighted areas",
                        color = RvInk,
                        fontSize = 14.sp
                    )
                }
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            Box(
                modifier = m
                    .background(Color(0xFF87CEEB), RoundedCornerShape(12.dp))
                    .border(3.dp, continent.color, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .testTag("placement_board")
            ) {
                // Continent background image
                ZoomedContinentView(
                    continent = continent,
                    modifier = Modifier.fillMaxSize()
                )

            EnhancedGridOverlay(
                rows = gridRows,
                cols = gridCols,
                selectedCell = selectedGridCell,
                correctCell = if (showFeedback) correctGrid else null,
                hintCells = if (showHints) hintCells else emptyList(),
                acceptableCells = if (showFeedback) GeographyGridSystem.getAcceptableCells(city.id, 1) else emptyList(),
                onCellSelected = { gridCell ->
                    if (!showFeedback) {
                        selectedGridCell = gridCell

                        // Use adaptive scoring based on player performance
                        val result = GeographyGridSystem.calculateAdaptiveScore(
                            city.id,
                            gridCell,
                            playerSuccessRate
                        )
                        gridResult = result

                        // Convert to placement result for compatibility
                        val placementResult = PlacementResult(
                            score = result.score,
                            distance = if (result.isCorrect) 0.0 else 100.0,
                            accuracy = result.accuracy
                        )

                        // Trigger feedback and proceed
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(2000)
                            onDragEnd(city.latitude, city.longitude)
                        }
                    }
                }
            )

                // Feedback overlays the board so the layout never shifts
                androidx.compose.animation.AnimatedVisibility(
                    visible = showFeedback && gridResult != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
            gridResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            result.score >= 40 -> RvSuccess
                            result.score >= 25 -> RvSky
                            result.score >= 15 -> RvSun
                            else -> RvError
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = result.accuracy,
                            color = RvInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${result.score} points",
                            color = RvInk,
                            fontSize = 14.sp
                        )

                        // Show additional context for learning
                        if (!result.isCorrect) {
                            correctGrid?.let { correct ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Exact location: Row ${correct.row + 1}, Column ${correct.col + 1}",
                                    color = RvInkSoft.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )

                                // Show some geographic context
                                Text(
                                    text = "Tip: ${city.name} is ${getGeographicHint(city, continent)}",
                                    color = RvInkSoft.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Encouragement for struggling players
                        if (result.encouragement.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.encouragement,
                                color = RvInkSoft.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
                }
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                topInfo(Modifier.weight(1f).fillMaxHeight())
                board(Modifier.weight(1.4f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth().align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topInfo(Modifier.fillMaxWidth())
                board(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EnhancedCityCard(
    city: GeographyCity,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .widthIn(min = if (compact) 88.dp else 120.dp, max = if (compact) 112.dp else 160.dp)
            .heightIn(min = if (compact) 48.dp else 70.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.padding(if (compact) 4.dp else 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = city.flag,
                    fontSize = if (compact) 16.sp else 22.sp
                )
                Text(
                    text = city.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (city.isCapital && !compact) {
                    Text(
                        text = "Capital",
                        fontSize = 12.sp,
                        color = RvSuccessEdge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedGridOverlay(
    rows: Int,
    cols: Int,
    selectedCell: GridCell?,
    correctCell: GridCell?,
    hintCells: List<GridCell>,
    acceptableCells: List<GridCell>,
    onCellSelected: (GridCell) -> Unit
) {
    val density = LocalDensity.current

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cellWidth = size.width / cols
                    val cellHeight = size.height / rows

                    val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val row = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)

                    onCellSelected(GridCell(row, col))
                }
            }
    ) {
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        // Draw grid lines
        val gridColor = RvInkSoft.copy(alpha = 0.7f)
        val strokeWidth = 1.5.dp.toPx()

        // Vertical lines
        for (i in 1 until cols) {
            val x = cellWidth * i
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }

        // Horizontal lines
        for (i in 1 until rows) {
            val y = cellHeight * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        }

        // Draw hint cells (yellow glow)
        hintCells.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = Color(0xFFFFEB3B).copy(alpha = 0.4f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
        }

        // Draw acceptable cells during feedback (green glow)
        acceptableCells.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = RvSuccess.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
        }

        // Highlight selected cell
        selectedCell?.let { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = Color(0xFFFF9500).copy(alpha = 0.6f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Draw border for selected cell
            drawRect(
                color = Color(0xFFFF9500),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }

        // Highlight correct cell (only shown during feedback)
        correctCell?.let { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = RvSuccess.copy(alpha = 0.4f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Draw border for correct cell
            drawRect(
                color = RvSuccess,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )

            // Draw checkmark in correct cell
            val centerX = left + cellWidth / 2
            val centerY = top + cellHeight / 2
            val checkSize = minOf(cellWidth, cellHeight) * 0.3f

            // Enhanced checkmark
            drawLine(
                color = RvInk,
                start = Offset(centerX - checkSize/2, centerY),
                end = Offset(centerX - checkSize/4, centerY + checkSize/2),
                strokeWidth = 4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = RvInk,
                start = Offset(centerX - checkSize/4, centerY + checkSize/2),
                end = Offset(centerX + checkSize/2, centerY - checkSize/2),
                strokeWidth = 4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Draw grid coordinates (for easier navigation)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val centerX = col * cellWidth + cellWidth / 2
                val centerY = row * cellHeight + cellHeight / 2

                // Draw small coordinate indicators in corners
                if (row == 0 && col < cols) {
                    // Column numbers at top
                    drawCircle(
                        color = RvInkSoft.copy(alpha = 0.6f),
                        radius = 8.dp.toPx(),
                        center = Offset(centerX, 12.dp.toPx())
                    )
                }

                if (col == 0 && row < rows) {
                    // Row numbers at left
                    drawCircle(
                        color = RvInkSoft.copy(alpha = 0.6f),
                        radius = 8.dp.toPx(),
                        center = Offset(12.dp.toPx(), centerY)
                    )
                }
            }
        }
    }
}

// Helper function to provide geographic hints
private fun getGeographicHint(city: GeographyCity, continent: Continent): String {
    return when {
        city.isCapital -> "the capital of ${city.country}"
        city.name == "Los Angeles" -> "on the west coast of the United States"
        city.name == "Miami" -> "in the southeastern United States"
        city.name == "Vancouver" -> "in western Canada"
        city.name == "São Paulo" -> "in southeastern Brazil"
        city.name == "Rio de Janeiro" -> "on Brazil's eastern coast"
        city.name == "Barcelona" -> "on Spain's Mediterranean coast"
        city.name == "Mumbai" -> "on India's western coast"
        city.name == "Shanghai" -> "on China's eastern coast"
        city.name == "Hong Kong" -> "on China's southern coast"
        city.name == "Sydney" -> "on Australia's eastern coast"
        city.name == "Melbourne" -> "in southeastern Australia"
        city.latitude > 50 -> "in the northern part of ${continent.displayName}"
        city.latitude < -20 -> "in the southern part of ${continent.displayName}"
        city.longitude < -100 -> "in the western part of ${continent.displayName}"
        city.longitude > 100 -> "in the eastern part of ${continent.displayName}"
        else -> "located in ${continent.displayName}"
    }
}

// Updated game state management functions
private fun updatePlayerSuccessRate(
    currentSuccessRate: Double,
    wasSuccessful: Boolean,
    questionsAnswered: Int
): Double {
    val weight = 1.0 / (questionsAnswered + 1)
    val newSuccessValue = if (wasSuccessful) 1.0 else 0.0
    return currentSuccessRate * (1 - weight) + newSuccessValue * weight
}

private fun shouldShowEncouragement(
    result: GridPlacementResult,
    playerSuccessRate: Double,
    questionsAnswered: Int
): String {
    return when {
        playerSuccessRate < 0.3 && questionsAnswered > 2 -> "Keep going! Geography takes practice!"
        result.score < 20 && playerSuccessRate < 0.5 -> "You're learning! Each guess helps!"
        result.score >= 35 -> "You're getting the hang of this!"
        else -> ""
    }
}

// Add this new composable for the grid overlay
@Composable
private fun GridOverlay(
    rows: Int,
    cols: Int,
    selectedCell: GridCell?,
    correctCell: GridCell?,
    onCellSelected: (GridCell) -> Unit
) {
    val density = LocalDensity.current

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Calculate which grid cell was tapped
                    val cellWidth = size.width / cols
                    val cellHeight = size.height / rows

                    val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val row = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)

                    onCellSelected(GridCell(row, col))
                }
            }
    ) {
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        // Draw grid lines
        val gridColor = RvInkSoft.copy(alpha = 0.6f)
        val strokeWidth = 2.dp.toPx()

        // Vertical lines
        for (i in 1 until cols) {
            val x = cellWidth * i
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }

        // Horizontal lines
        for (i in 1 until rows) {
            val y = cellHeight * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        }

        // Highlight selected cell
        selectedCell?.let { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = Color(0xFFFF9500).copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Draw border for selected cell
            drawRect(
                color = Color(0xFFFF9500),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }

        // Highlight correct cell (only shown during feedback)
        correctCell?.let { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = RvSuccess.copy(alpha = 0.3f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Draw border for correct cell
            drawRect(
                color = RvSuccess,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )

            // Draw checkmark in correct cell
            val centerX = left + cellWidth / 2
            val centerY = top + cellHeight / 2
            val checkSize = minOf(cellWidth, cellHeight) * 0.3f

            // Simple checkmark using lines
            drawLine(
                color = RvSuccess,
                start = Offset(centerX - checkSize/2, centerY),
                end = Offset(centerX - checkSize/4, centerY + checkSize/2),
                strokeWidth = 6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = RvSuccess,
                start = Offset(centerX - checkSize/4, centerY + checkSize/2),
                end = Offset(centerX + checkSize/2, centerY - checkSize/2),
                strokeWidth = 6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun ContinentShape(
    continent: Continent,
    modifier: Modifier = Modifier
) {
    val imageRes = when (continent) {
        Continent.NORTH_AMERICA -> R.drawable.north_america
        Continent.SOUTH_AMERICA -> R.drawable.south_america
        Continent.EUROPE -> R.drawable.europe
        Continent.AFRICA -> R.drawable.africa
        Continent.ASIA -> R.drawable.asia
        Continent.OCEANIA -> R.drawable.oceania
    }

    Image(
        painter = painterResource(id = imageRes),
        contentDescription = continent.displayName,
        modifier = modifier
    )
}

@Composable
private fun GeographyCompletionScreen(
    totalScore: Int,
    questionsAnswered: Int,
    maxQuestions: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Geography Challenge Complete!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.final_score),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$totalScore",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvSuccess
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Cities Placed: $questionsAnswered",
                        fontSize = 16.sp,
                        color = RvInk
                    )

                    val percentage = (totalScore.toDouble() / (maxQuestions * 75).toDouble() * 100).toInt()
                    Text(
                        text = "${stringResource(R.string.accuracy)}: $percentage%",
                        fontSize = 16.sp,
                        color = RvInk
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RvSuccess)
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvOnTone
            )
        }
    }
}

@Composable
private fun CityCard(
    city: GeographyCity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .widthIn(min = 120.dp)
            .heightIn(min = 60.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = city.flag,
                    fontSize = 20.sp
                )
                Text(
                    text = city.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun calculatePlacementScore(
    city: GeographyCity,
    userLat: Double,
    userLng: Double
): PlacementResult {
    val distance = calculateDistance(city.latitude, city.longitude, userLat, userLng)

    val score = when {
        distance <= 100 -> 50      // Perfect: within 100km
        distance <= 300 -> 40      // Excellent: within 300km
        distance <= 600 -> 25      // Good: within 600km
        distance <= 1000 -> 10     // Fair: within 1000km
        else -> 0                  // Poor: over 1000km
    }

    val accuracy = when {
        distance <= 100 -> "Perfect!"
        distance <= 300 -> "Excellent!"
        distance <= 600 -> "Good!"
        distance <= 1000 -> "Close!"
        else -> "Try again!"
    }

    return PlacementResult(score, distance, accuracy)
}

private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6371.0 // Earth's radius in kilometers

    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

// Add this new composable for testing all cities at once
@Composable
fun GeographyGridTestingScreen(
    onBack: () -> Unit,
    onExportMappings: (String) -> Unit
) {
    var selectedContinent by remember { mutableStateOf(Continent.NORTH_AMERICA) }
    var selectedCity by remember { mutableStateOf<GeographyCity?>(null) }
    var gridMappings by remember { mutableStateOf(mutableMapOf<String, GridCell>()) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Initialize with current mappings
    LaunchedEffect(Unit) {
        GeographyCityDatabase.cities.forEach { city ->
            GeographyGridSystem.getCorrectGridCell(city.id)?.let { gridCell ->
                gridMappings[city.id] = gridCell
            }
        }
    }

    val citiesInContinent = GeographyCityDatabase.getCitiesByContinent(selectedContinent)
    val (gridRows, gridCols) = GeographyGridSystem.getGridDimensions(selectedContinent)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = RvSky)
            ) {
                Text("← Back", color = RvOnTone)
            }

            Text(
                text = "Grid Testing Mode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Button(
                onClick = { showExportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RvSuccess)
            ) {
                Text("Export", color = RvOnTone)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continent selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Continent.values()) { continent ->
                Button(
                    onClick = {
                        selectedContinent = continent
                        selectedCity = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedContinent == continent)
                            continent.color else RvInkSoft
                    )
                ) {
                    Text(
                        text = continent.displayName,
                        fontSize = 12.sp,
                        color = RvInk
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left side - City list
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "Cities in ${selectedContinent.displayName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(citiesInContinent) { city ->
                        val currentGrid = gridMappings[city.id]

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCity = city },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCity?.id == city.id)
                                    Color(0xFFFF9500) else RvInk
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(city.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = city.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedCity?.id == city.id) RvInk else RvInk
                                    )
                                }

                                currentGrid?.let { grid ->
                                    Text(
                                        text = "Grid: R${grid.row + 1}, C${grid.col + 1}",
                                        fontSize = 10.sp,
                                        color = if (selectedCity?.id == city.id)
                                            RvInkSoft else RvInkSoft
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right side - Interactive grid
            Column(
                modifier = Modifier.weight(0.6f)
            ) {
                Text(
                    text = "Click on grid to place ${selectedCity?.name ?: "selected city"}",
                    fontSize = 14.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Color(0xFF87CEEB), RoundedCornerShape(12.dp))
                        .border(3.dp, selectedContinent.color, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    // Continent background
                    ZoomedContinentView(
                        continent = selectedContinent,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive testing grid
                    TestingGridOverlay(
                        rows = gridRows,
                        cols = gridCols,
                        citiesInContinent = citiesInContinent,
                        gridMappings = gridMappings,
                        selectedCity = selectedCity,
                        onCellSelected = { gridCell ->
                            selectedCity?.let { city ->
                                gridMappings[city.id] = gridCell
                            }
                        }
                    )
                }

                // Grid info
                Text(
                    text = "Grid: ${gridRows}×${gridCols} | Selected: ${selectedCity?.name ?: "None"}",
                    fontSize = 12.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Grid Mappings") },
            text = {
                Text("This will generate the updated cityGridMapping code. Copy and replace in your GeographyGridSystem.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val exportText = generateGridMappingCode(gridMappings)
                        onExportMappings(exportText)
                        showExportDialog = false
                    }
                ) {
                    Text("Generate Code")
                }
            },
            dismissButton = {
                Button(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TestingGridOverlay(
    rows: Int,
    cols: Int,
    citiesInContinent: List<GeographyCity>,
    gridMappings: Map<String, GridCell>,
    selectedCity: GeographyCity?,
    onCellSelected: (GridCell) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cellWidth = size.width / cols
                    val cellHeight = size.height / rows

                    val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val row = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)

                    onCellSelected(GridCell(row, col))
                }
            }
    ) {
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        // Draw grid lines
        val gridColor = RvInkSoft.copy(alpha = 0.8f)
        val strokeWidth = 2.dp.toPx()

        // Vertical lines
        for (i in 0..cols) {
            val x = cellWidth * i
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }

        // Horizontal lines
        for (i in 0..rows) {
            val y = cellHeight * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        }

        // Draw city positions
        citiesInContinent.forEach { city ->
            gridMappings[city.id]?.let { gridCell ->
                val centerX = gridCell.col * cellWidth + cellWidth / 2
                val centerY = gridCell.row * cellHeight + cellHeight / 2

                val isSelected = selectedCity?.id == city.id
                val dotColor = if (isSelected) Color(0xFFFF9500) else RvError
                val dotSize = if (isSelected) 16.dp.toPx() else 12.dp.toPx()

                // Draw city dot
                drawCircle(
                    color = dotColor,
                    radius = dotSize / 2,
                    center = Offset(centerX, centerY)
                )

                // Draw white border
                drawCircle(
                    color = RvInk,
                    radius = dotSize / 2,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                // Draw city initial or flag
                // Note: Drawing text in Canvas requires TextMeasurer in newer Compose versions
                // For now, we'll just use colored dots
            }
        }

        // Draw grid coordinates (for reference)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val centerX = col * cellWidth + cellWidth / 2
                val centerY = row * cellHeight + cellHeight / 2

                // Draw small coordinate indicator
                drawCircle(
                    color = RvInkSoft.copy(alpha = 0.3f),
                    radius = 3.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Highlight selected city's grid cell
        selectedCity?.let { city ->
            gridMappings[city.id]?.let { gridCell ->
                val left = gridCell.col * cellWidth
                val top = gridCell.row * cellHeight

                drawRect(
                    color = Color(0xFFFF9500).copy(alpha = 0.3f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                )
            }
        }
    }
}

// Function to generate the updated grid mapping code
private fun generateGridMappingCode(gridMappings: Map<String, GridCell>): String {
    val sb = StringBuilder()
    sb.appendLine("// Updated cityGridMapping - Replace in GeographyGridSystem")
    sb.appendLine("private val cityGridMapping = mapOf(")

    val continentGroups = mapOf(
        "NORTH AMERICA" to listOf("new_york", "los_angeles", "mexico_city", "toronto", "chicago", "washington_dc", "vancouver", "miami", "montreal", "guatemala_city"),
        "SOUTH AMERICA" to listOf("sao_paulo", "rio_janeiro", "buenos_aires", "lima", "bogota", "santiago", "caracas", "quito"),
        "EUROPE" to listOf("london", "paris", "berlin", "rome", "madrid", "amsterdam", "vienna", "stockholm", "prague", "barcelona", "moscow", "athens"),
        "AFRICA" to listOf("cairo", "lagos", "johannesburg", "casablanca", "nairobi", "addis_ababa", "cape_town", "algiers"),
        "ASIA" to listOf("tokyo", "beijing", "mumbai", "shanghai", "delhi", "seoul", "bangkok", "singapore", "hong_kong", "kuala_lumpur"),
        "OCEANIA" to listOf("sydney", "melbourne")
    )

    continentGroups.forEach { (continentName, cityIds) ->
        sb.appendLine("    // $continentName")
        cityIds.forEach { cityId ->
            gridMappings[cityId]?.let { grid ->
                sb.appendLine("    \"$cityId\" to GridCell(${grid.row}, ${grid.col}),")
            }
        }
        sb.appendLine()
    }

    sb.appendLine(")")

    return sb.toString()
}