package com.kreativekoala.riddleverse

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

// Data classes for geography country puzzle
data class GeographyCountry(
    val id: String,
    val name: String,
    val continent: Continent,
    val latitude: Double,
    val longitude: Double,
    val isLandlocked: Boolean,
    val flag: String,
    val capital: String
)

data class CountryPlacementResult(
    val score: Int,
    val distance: Double,
    val accuracy: String
)


// Updated GeographyCountryGridSystem with new mappings
object GeographyCountryGridSystem {
    // Updated to use consistent 8x6 grid for all continents (from your script output)
    private val gridDimensions = mapOf(
        Continent.NORTH_AMERICA to Pair(6, 8),
        Continent.SOUTH_AMERICA to Pair(6, 8),
        Continent.EUROPE to Pair(6, 8),
        Continent.AFRICA to Pair(6, 8),
        Continent.ASIA to Pair(6, 8),
        Continent.OCEANIA to Pair(6, 8)
    )

    // Generated mappings from your Vision API script - replace this with the actual output from GeographyCountryGridSystem.kt
    private val countryGridMapping = mapOf(
        // NORTH_AMERICA
        "canada" to listOf(GridCell(0, 2), GridCell(0, 3), GridCell(1, 2), GridCell(1, 3), GridCell(1, 4), GridCell(1, 5)),
        "united_states" to listOf(GridCell(2, 2), GridCell(2, 3), GridCell(2, 4), GridCell(3, 2), GridCell(3, 3), GridCell(3, 4)),
        "mexico" to listOf(GridCell(4, 2), GridCell(4, 3), GridCell(5, 2), GridCell(5, 3)),
        "guatemala" to listOf(GridCell(5, 4)),
        "belize" to listOf(GridCell(5, 5)),
        "honduras" to listOf(GridCell(5, 6)),
        "el_salvador" to listOf(GridCell(5, 7)),
        "nicaragua" to listOf(GridCell(4, 5)),
        "costa_rica" to listOf(GridCell(4, 6)),
        "panama" to listOf(GridCell(4, 7)),
        "jamaica" to listOf(GridCell(3, 1)),
        "cuba" to listOf(GridCell(2, 1), GridCell(2, 2)),
        "haiti" to listOf(GridCell(3, 0)),
        "dominican_republic" to listOf(GridCell(3, 1)),
        "bahamas" to listOf(GridCell(2, 0)),
        "barbados" to listOf(GridCell(3, 2)),
        "trinidad_and_tobago" to listOf(GridCell(4, 0)),

        // SOUTH_AMERICA
        "brazil" to listOf(GridCell(1, 3), GridCell(1, 4), GridCell(1, 5), GridCell(2, 3), GridCell(2, 4), GridCell(2, 5), GridCell(3, 3), GridCell(3, 4)),
        "argentina" to listOf(GridCell(3, 3), GridCell(3, 4), GridCell(4, 3), GridCell(4, 4), GridCell(5, 3)),
        "chile" to listOf(GridCell(3, 2), GridCell(4, 2), GridCell(5, 2)),
        "peru" to listOf(GridCell(2, 2), GridCell(3, 2)),
        "colombia" to listOf(GridCell(1, 2), GridCell(2, 2)),
        "venezuela" to listOf(GridCell(1, 1), GridCell(1, 2)),
        "ecuador" to listOf(GridCell(2, 1)),
        "bolivia" to listOf(GridCell(3, 3), GridCell(3, 4)),
        "paraguay" to listOf(GridCell(4, 4)),
        "uruguay" to listOf(GridCell(5, 4)),
        "guyana" to listOf(GridCell(1, 3)),
        "suriname" to listOf(GridCell(1, 4)),

        // EUROPE
        "russia" to listOf(GridCell(0, 5), GridCell(0, 6), GridCell(1, 5), GridCell(1, 6)),
        "germany" to listOf(GridCell(2, 3), GridCell(2, 4)),
        "united_kingdom" to listOf(GridCell(2, 2)),
        "france" to listOf(GridCell(3, 2), GridCell(3, 3)),
        "italy" to listOf(GridCell(3, 4), GridCell(4, 4)),
        "spain" to listOf(GridCell(4, 2), GridCell(4, 3)),
        "poland" to listOf(GridCell(2, 5)),
        "romania" to listOf(GridCell(3, 5)),
        "netherlands" to listOf(GridCell(2, 3)),
        "belgium" to listOf(GridCell(3, 3)),
        "greece" to listOf(GridCell(4, 5)),
        "portugal" to listOf(GridCell(4, 2)),
        "sweden" to listOf(GridCell(1, 3)),
        "norway" to listOf(GridCell(1, 2)),
        "finland" to listOf(GridCell(1, 4)),
        "ukraine" to listOf(GridCell(2, 6)),
        "czech_republic" to listOf(GridCell(3, 4)),
        "hungary" to listOf(GridCell(3, 5)),
        "austria" to listOf(GridCell(3, 4)),
        "switzerland" to listOf(GridCell(3, 3)),
        "bulgaria" to listOf(GridCell(4, 5)),
        "serbia" to listOf(GridCell(4, 4)),
        "croatia" to listOf(GridCell(4, 4)),
        "ireland" to listOf(GridCell(2, 1)),

        // AFRICA
        "nigeria" to listOf(GridCell(2, 3), GridCell(2, 4)),
        "egypt" to listOf(GridCell(1, 5), GridCell(1, 6)),
        "south_africa" to listOf(GridCell(4, 4), GridCell(5, 4)),
        "morocco" to listOf(GridCell(0, 2)),
        "algeria" to listOf(GridCell(1, 2), GridCell(1, 3)),
        "kenya" to listOf(GridCell(3, 5)),
        "ethiopia" to listOf(GridCell(2, 5)),
        "tanzania" to listOf(GridCell(3, 5), GridCell(3, 6)),
        "libya" to listOf(GridCell(1, 4)),
        "sudan" to listOf(GridCell(2, 4), GridCell(2, 5)),
        "ghana" to listOf(GridCell(2, 2)),
        "madagascar" to listOf(GridCell(4, 5)),
        "democratic_republic_of_congo" to listOf(GridCell(3, 4)),
        "angola" to listOf(GridCell(4, 3)),
        "zambia" to listOf(GridCell(4, 4)),
        "mozambique" to listOf(GridCell(4, 5)),
        "cameroon" to listOf(GridCell(2, 3)),
        "ivory_coast" to listOf(GridCell(2, 2)),
        "mali" to listOf(GridCell(1, 2)),
        "niger" to listOf(GridCell(1, 3)),
        "chad" to listOf(GridCell(2, 4)),

        // ASIA
        "china" to listOf(GridCell(1, 3), GridCell(1, 4), GridCell(2, 3), GridCell(2, 4)),
        "india" to listOf(GridCell(2, 3), GridCell(3, 3)),
        "japan" to listOf(GridCell(1, 6)),
        "indonesia" to listOf(GridCell(3, 5), GridCell(4, 5)),
        "thailand" to listOf(GridCell(2, 4)),
        "iran" to listOf(GridCell(2, 2)),
        "turkey" to listOf(GridCell(1, 1)),
        "saudi_arabia" to listOf(GridCell(3, 2)),
        "pakistan" to listOf(GridCell(2, 3)),
        "afghanistan" to listOf(GridCell(2, 2)),
        "mongolia" to listOf(GridCell(1, 3)),
        "south_korea" to listOf(GridCell(1, 5)),
        "north_korea" to listOf(GridCell(1, 5)),
        "vietnam" to listOf(GridCell(2, 5)),
        "philippines" to listOf(GridCell(3, 6)),
        "malaysia" to listOf(GridCell(3, 5)),
        "singapore" to listOf(GridCell(3, 5)),
        "myanmar" to listOf(GridCell(2, 4)),
        "bangladesh" to listOf(GridCell(2, 4)),
        "nepal" to listOf(GridCell(2, 3)),
        "kazakhstan" to listOf(GridCell(1, 2)),
        "iraq" to listOf(GridCell(2, 1)),
        "israel" to listOf(GridCell(2, 0)),

        // OCEANIA
        "australia" to listOf(GridCell(3, 0), GridCell(3, 1), GridCell(3, 2), GridCell(4, 0), GridCell(4, 1), GridCell(4, 2)),
        "new_zealand" to listOf(GridCell(4, 4), GridCell(4, 5), GridCell(5, 4), GridCell(5, 5)),
        "papua_new_guinea" to listOf(GridCell(2, 1), GridCell(2, 2)),
        "fiji" to listOf(GridCell(2, 5)),
        "solomon_islands" to listOf(GridCell(1, 3), GridCell(1, 4)),
        "vanuatu" to listOf(GridCell(2, 4)),
        "samoa" to listOf(GridCell(1, 6)),
        "tonga" to listOf(GridCell(3, 6)),
    )

    fun getGridDimensions(continent: Continent): Pair<Int, Int> {
        return gridDimensions[continent] ?: Pair(6, 8)
    }

    fun getCorrectGridCells(countryId: String): List<GridCell>? {
        return countryGridMapping[countryId]
    }

    /**
     * Enhanced scoring system that's much more forgiving
     * Accounts for neighboring cells and mapping inaccuracies
     */
    fun calculateGridScore(countryId: String, selectedCells: List<GridCell>): GridPlacementResult {
        val correctCells = getCorrectGridCells(countryId) ?: return GridPlacementResult(
            score = 0,
            accuracy = "Country not found in grid system",
            isCorrect = false
        )

        val correctSet = correctCells.toSet()
        val selectedSet = selectedCells.toSet()

        // Calculate exact matches
        val exactMatches = selectedSet.intersect(correctSet).size
        val totalSelected = selectedSet.size
        val totalCorrect = correctSet.size

        // Calculate neighboring cell matches (very forgiving)
        val neighborMatches = calculateNeighborMatches(selectedSet, correctSet)

        // Calculate "close enough" matches (adjacent or nearby cells)
        val closeMatches = calculateCloseMatches(selectedSet, correctSet)

        // Forgiving scoring algorithm
        val score = when {
            // Perfect match
            selectedSet == correctSet -> 50

            // Most cells correct + some neighbors
            exactMatches >= (totalCorrect * 0.7).toInt() -> 45

            // Majority correct or very close
            (exactMatches + neighborMatches) >= (totalCorrect * 0.6).toInt() -> 40

            // Half correct or reasonably close
            (exactMatches + neighborMatches + closeMatches) >= (totalCorrect * 0.5).toInt() -> 35

            // Some correct cells or good neighboring
            exactMatches >= (totalCorrect * 0.4).toInt() || neighborMatches >= 2 -> 30

            // At least one correct or several close
            exactMatches >= 1 || (neighborMatches + closeMatches) >= 3 -> 25

            // Close but not quite (neighboring/adjacent)
            neighborMatches >= 1 || closeMatches >= 2 -> 20

            // In the general area (within 2 cells of correct location)
            hasNearbyPlacements(selectedSet, correctSet, maxDistance = 2) -> 15

            // Somewhere on the continent (within 3 cells)
            hasNearbyPlacements(selectedSet, correctSet, maxDistance = 3) -> 10

            else -> 5 // Participation points
        }

        // Generate encouraging feedback
        val accuracyText = when {
            score >= 45 -> "Excellent placement!"
            score >= 35 -> "Very good!"
            score >= 25 -> "Good job!"
            score >= 15 -> "Getting close!"
            score >= 10 -> "In the right area!"
            else -> "Keep trying!"
        }

        val isExact = selectedSet == correctSet

        return GridPlacementResult(
            score = score,
            accuracy = accuracyText,
            isCorrect = isExact
        )
    }

    /**
     * Calculate matches in immediately adjacent cells (8-directional neighbors)
     */
    private fun calculateNeighborMatches(selectedSet: Set<GridCell>, correctSet: Set<GridCell>): Int {
        var neighborMatches = 0

        selectedSet.forEach { selectedCell ->
            correctSet.forEach { correctCell ->
                if (areAdjacent(selectedCell, correctCell)) {
                    neighborMatches++
                }
            }
        }

        return neighborMatches
    }

    /**
     * Calculate matches within 2 cells distance (more forgiving)
     */
    private fun calculateCloseMatches(selectedSet: Set<GridCell>, correctSet: Set<GridCell>): Int {
        var closeMatches = 0

        selectedSet.forEach { selectedCell ->
            correctSet.forEach { correctCell ->
                val distance = getCellDistance(selectedCell, correctCell)
                if (distance <= 2 && distance > 1) { // Close but not adjacent
                    closeMatches++
                }
            }
        }

        return closeMatches
    }

    /**
     * Check if any selected cells are near correct cells within max distance
     */
    private fun hasNearbyPlacements(selectedSet: Set<GridCell>, correctSet: Set<GridCell>, maxDistance: Int): Boolean {
        return selectedSet.any { selectedCell ->
            correctSet.any { correctCell ->
                getCellDistance(selectedCell, correctCell) <= maxDistance
            }
        }
    }

    /**
     * Check if two cells are adjacent (8-directional)
     */
    private fun areAdjacent(cell1: GridCell, cell2: GridCell): Boolean {
        val rowDiff = kotlin.math.abs(cell1.row - cell2.row)
        val colDiff = kotlin.math.abs(cell1.col - cell2.col)
        return (rowDiff <= 1 && colDiff <= 1) && !(rowDiff == 0 && colDiff == 0)
    }

    /**
     * Calculate Manhattan distance between two cells
     */
    private fun getCellDistance(cell1: GridCell, cell2: GridCell): Int {
        return kotlin.math.abs(cell1.row - cell2.row) + kotlin.math.abs(cell1.col - cell2.col)
    }

    /**
     * Get expanded valid area for a country (includes neighboring cells)
     */
    fun getExpandedValidArea(countryId: String): List<GridCell> {
        val correctCells = getCorrectGridCells(countryId) ?: return emptyList()
        val expandedCells = mutableSetOf<GridCell>()

        // Add all correct cells
        expandedCells.addAll(correctCells)

        // Add all neighboring cells
        correctCells.forEach { correctCell ->
            for (deltaRow in -1..1) {
                for (deltaCol in -1..1) {
                    val neighborRow = correctCell.row + deltaRow
                    val neighborCol = correctCell.col + deltaCol

                    if (neighborRow >= 0 && neighborRow < 6 &&
                        neighborCol >= 0 && neighborCol < 8) {
                        expandedCells.add(GridCell(neighborRow, neighborCol))
                    }
                }
            }
        }

        return expandedCells.toList()
    }

    fun getCountryDifficulty(countryId: String): String {
        val cells = getCorrectGridCells(countryId) ?: return "Unknown"
        return when (cells.size) {
            1 -> "Easy"
            2, 3 -> "Medium"
            4, 5, 6 -> "Hard"
            else -> "Expert"
        }
    }


}

// Keep your existing GeographyCountryDatabase - it's comprehensive
object GeographyCountryDatabase {
    val countries = listOf(
        // NORTH AMERICA (23 countries)
        GeographyCountry("united_states", "United States", Continent.NORTH_AMERICA, 39.8283, -98.5795, false, "🇺🇸", "Washington D.C."),
        GeographyCountry("canada", "Canada", Continent.NORTH_AMERICA, 56.1304, -106.3468, false, "🇨🇦", "Ottawa"),
        GeographyCountry("mexico", "Mexico", Continent.NORTH_AMERICA, 23.6345, -102.5528, false, "🇲🇽", "Mexico City"),
        GeographyCountry("guatemala", "Guatemala", Continent.NORTH_AMERICA, 15.7835, -90.2308, false, "🇬🇹", "Guatemala City"),
        GeographyCountry("belize", "Belize", Continent.NORTH_AMERICA, 17.1899, -88.4976, false, "🇧🇿", "Belmopan"),
        GeographyCountry("honduras", "Honduras", Continent.NORTH_AMERICA, 15.2, -86.2419, false, "🇭🇳", "Tegucigalpa"),
        GeographyCountry("el_salvador", "El Salvador", Continent.NORTH_AMERICA, 13.7942, -88.8965, false, "🇸🇻", "San Salvador"),
        GeographyCountry("nicaragua", "Nicaragua", Continent.NORTH_AMERICA, 12.2658, -85.2072, false, "🇳🇮", "Managua"),
        GeographyCountry("costa_rica", "Costa Rica", Continent.NORTH_AMERICA, 9.7489, -83.7534, false, "🇨🇷", "San José"),
        GeographyCountry("panama", "Panama", Continent.NORTH_AMERICA, 8.538, -80.7821, false, "🇵🇦", "Panama City"),
        GeographyCountry("jamaica", "Jamaica", Continent.NORTH_AMERICA, 18.1096, -77.2975, false, "🇯🇲", "Kingston"),
        GeographyCountry("cuba", "Cuba", Continent.NORTH_AMERICA, 21.5218, -77.7812, false, "🇨🇺", "Havana"),
        GeographyCountry("haiti", "Haiti", Continent.NORTH_AMERICA, 18.9712, -72.2852, false, "🇭🇹", "Port-au-Prince"),
        GeographyCountry("dominican_republic", "Dominican Republic", Continent.NORTH_AMERICA, 18.7357, -70.1627, false, "🇩🇴", "Santo Domingo"),
        GeographyCountry("bahamas", "Bahamas", Continent.NORTH_AMERICA, 25.0343, -77.3963, false, "🇧🇸", "Nassau"),
        GeographyCountry("barbados", "Barbados", Continent.NORTH_AMERICA, 13.1939, -59.5432, false, "🇧🇧", "Bridgetown"),
        GeographyCountry("trinidad_and_tobago", "Trinidad and Tobago", Continent.NORTH_AMERICA, 10.6918, -61.2225, false, "🇹🇹", "Port of Spain"),

        // SOUTH AMERICA (12 countries)
        GeographyCountry("brazil", "Brazil", Continent.SOUTH_AMERICA, -14.2350, -51.9253, false, "🇧🇷", "Brasília"),
        GeographyCountry("argentina", "Argentina", Continent.SOUTH_AMERICA, -38.4161, -63.6167, false, "🇦🇷", "Buenos Aires"),
        GeographyCountry("chile", "Chile", Continent.SOUTH_AMERICA, -35.6751, -71.5430, false, "🇨🇱", "Santiago"),
        GeographyCountry("peru", "Peru", Continent.SOUTH_AMERICA, -9.1900, -75.0152, false, "🇵🇪", "Lima"),
        GeographyCountry("colombia", "Colombia", Continent.SOUTH_AMERICA, 4.5709, -74.2973, false, "🇨🇴", "Bogotá"),
        GeographyCountry("venezuela", "Venezuela", Continent.SOUTH_AMERICA, 6.4238, -66.5897, false, "🇻🇪", "Caracas"),
        GeographyCountry("ecuador", "Ecuador", Continent.SOUTH_AMERICA, -1.8312, -78.1834, false, "🇪🇨", "Quito"),
        GeographyCountry("bolivia", "Bolivia", Continent.SOUTH_AMERICA, -16.2902, -63.5887, true, "🇧🇴", "Sucre"),
        GeographyCountry("paraguay", "Paraguay", Continent.SOUTH_AMERICA, -23.4425, -58.4438, true, "🇵🇾", "Asunción"),
        GeographyCountry("uruguay", "Uruguay", Continent.SOUTH_AMERICA, -32.5228, -55.7658, false, "🇺🇾", "Montevideo"),
        GeographyCountry("guyana", "Guyana", Continent.SOUTH_AMERICA, 4.8604, -58.9302, false, "🇬🇾", "Georgetown"),
        GeographyCountry("suriname", "Suriname", Continent.SOUTH_AMERICA, 3.9193, -56.0278, false, "🇸🇷", "Paramaribo"),

        // EUROPE (24 countries - key European countries)
        GeographyCountry("russia", "Russia", Continent.EUROPE, 61.5240, 105.3188, false, "🇷🇺", "Moscow"),
        GeographyCountry("germany", "Germany", Continent.EUROPE, 51.1657, 10.4515, false, "🇩🇪", "Berlin"),
        GeographyCountry("united_kingdom", "United Kingdom", Continent.EUROPE, 55.3781, -3.4360, false, "🇬🇧", "London"),
        GeographyCountry("france", "France", Continent.EUROPE, 46.6034, 2.2137, false, "🇫🇷", "Paris"),
        GeographyCountry("italy", "Italy", Continent.EUROPE, 41.8719, 12.5674, false, "🇮🇹", "Rome"),
        GeographyCountry("spain", "Spain", Continent.EUROPE, 40.4637, -3.7492, false, "🇪🇸", "Madrid"),
        GeographyCountry("poland", "Poland", Continent.EUROPE, 51.9194, 19.1451, false, "🇵🇱", "Warsaw"),
        GeographyCountry("romania", "Romania", Continent.EUROPE, 45.9432, 24.9668, false, "🇷🇴", "Bucharest"),
        GeographyCountry("netherlands", "Netherlands", Continent.EUROPE, 52.1326, 5.2913, false, "🇳🇱", "Amsterdam"),
        GeographyCountry("belgium", "Belgium", Continent.EUROPE, 50.5039, 4.4699, false, "🇧🇪", "Brussels"),
        GeographyCountry("greece", "Greece", Continent.EUROPE, 39.0742, 21.8243, false, "🇬🇷", "Athens"),
        GeographyCountry("portugal", "Portugal", Continent.EUROPE, 39.3999, -8.2245, false, "🇵🇹", "Lisbon"),
        GeographyCountry("sweden", "Sweden", Continent.EUROPE, 60.1282, 18.6435, false, "🇸🇪", "Stockholm"),
        GeographyCountry("norway", "Norway", Continent.EUROPE, 60.4720, 8.4689, false, "🇳🇴", "Oslo"),
        GeographyCountry("finland", "Finland", Continent.EUROPE, 61.9241, 25.7482, false, "🇫🇮", "Helsinki"),
        GeographyCountry("ukraine", "Ukraine", Continent.EUROPE, 48.3794, 31.1656, false, "🇺🇦", "Kyiv"),
        GeographyCountry("czech_republic", "Czech Republic", Continent.EUROPE, 49.8175, 15.4730, true, "🇨🇿", "Prague"),
        GeographyCountry("hungary", "Hungary", Continent.EUROPE, 47.1625, 19.5033, true, "🇭🇺", "Budapest"),
        GeographyCountry("austria", "Austria", Continent.EUROPE, 47.5162, 14.5501, true, "🇦🇹", "Vienna"),
        GeographyCountry("switzerland", "Switzerland", Continent.EUROPE, 46.8182, 8.2275, true, "🇨🇭", "Bern"),
        GeographyCountry("bulgaria", "Bulgaria", Continent.EUROPE, 42.7339, 25.4858, false, "🇧🇬", "Sofia"),
        GeographyCountry("serbia", "Serbia", Continent.EUROPE, 44.0165, 21.0059, true, "🇷🇸", "Belgrade"),
        GeographyCountry("croatia", "Croatia", Continent.EUROPE, 45.1000, 15.2000, false, "🇭🇷", "Zagreb"),
        GeographyCountry("ireland", "Ireland", Continent.EUROPE, 53.4129, -8.2439, false, "🇮🇪", "Dublin"),

        // AFRICA (21 countries)
        GeographyCountry("nigeria", "Nigeria", Continent.AFRICA, 9.0820, 8.6753, false, "🇳🇬", "Abuja"),
        GeographyCountry("egypt", "Egypt", Continent.AFRICA, 26.0975, 31.4637, false, "🇪🇬", "Cairo"),
        GeographyCountry("south_africa", "South Africa", Continent.AFRICA, -30.5595, 22.9375, false, "🇿🇦", "Cape Town"),
        GeographyCountry("morocco", "Morocco", Continent.AFRICA, 31.7917, -7.0926, false, "🇲🇦", "Rabat"),
        GeographyCountry("algeria", "Algeria", Continent.AFRICA, 28.0339, 1.6596, false, "🇩🇿", "Algiers"),
        GeographyCountry("kenya", "Kenya", Continent.AFRICA, -0.0236, 37.9062, false, "🇰🇪", "Nairobi"),
        GeographyCountry("ethiopia", "Ethiopia", Continent.AFRICA, 9.1450, 40.4897, true, "🇪🇹", "Addis Ababa"),
        GeographyCountry("tanzania", "Tanzania", Continent.AFRICA, -6.3690, 34.8888, false, "🇹🇿", "Dodoma"),
        GeographyCountry("libya", "Libya", Continent.AFRICA, 26.3351, 17.2283, false, "🇱🇾", "Tripoli"),
        GeographyCountry("sudan", "Sudan", Continent.AFRICA, 12.8628, 30.2176, false, "🇸🇩", "Khartoum"),
        GeographyCountry("ghana", "Ghana", Continent.AFRICA, 7.9465, -1.0232, false, "🇬🇭", "Accra"),
        GeographyCountry("madagascar", "Madagascar", Continent.AFRICA, -18.7669, 46.8691, false, "🇲🇬", "Antananarivo"),
        GeographyCountry("democratic_republic_of_congo", "Democratic Republic of Congo", Continent.AFRICA, -4.0383, 21.7587, false, "🇨🇩", "Kinshasa"),
        GeographyCountry("angola", "Angola", Continent.AFRICA, -11.2027, 17.8739, false, "🇦🇴", "Luanda"),
        GeographyCountry("zambia", "Zambia", Continent.AFRICA, -13.1339, 27.8493, true, "🇿🇲", "Lusaka"),
        GeographyCountry("mozambique", "Mozambique", Continent.AFRICA, -18.2861, 35.2045, false, "🇲🇿", "Maputo"),
        GeographyCountry("cameroon", "Cameroon", Continent.AFRICA, 7.3697, 12.3547, false, "🇨🇲", "Yaoundé"),
        GeographyCountry("ivory_coast", "Ivory Coast", Continent.AFRICA, 7.5400, -5.5471, false, "🇨🇮", "Yamoussoukro"),
        GeographyCountry("mali", "Mali", Continent.AFRICA, 17.5707, -3.9962, true, "🇲🇱", "Bamako"),
        GeographyCountry("niger", "Niger", Continent.AFRICA, 17.6078, 8.0817, true, "🇳🇪", "Niamey"),
        GeographyCountry("chad", "Chad", Continent.AFRICA, 15.4542, 18.7322, true, "🇹🇩", "N'Djamena"),

        // ASIA (23 countries)
        GeographyCountry("china", "China", Continent.ASIA, 35.8617, 104.1954, false, "🇨🇳", "Beijing"),
        GeographyCountry("india", "India", Continent.ASIA, 20.5937, 78.9629, false, "🇮🇳", "New Delhi"),
        GeographyCountry("japan", "Japan", Continent.ASIA, 36.2048, 138.2529, false, "🇯🇵", "Tokyo"),
        GeographyCountry("indonesia", "Indonesia", Continent.ASIA, -0.7893, 113.9213, false, "🇮🇩", "Jakarta"),
        GeographyCountry("thailand", "Thailand", Continent.ASIA, 15.8700, 100.9925, false, "🇹🇭", "Bangkok"),
        GeographyCountry("iran", "Iran", Continent.ASIA, 32.4279, 53.6880, false, "🇮🇷", "Tehran"),
        GeographyCountry("turkey", "Turkey", Continent.ASIA, 38.9637, 35.2433, false, "🇹🇷", "Ankara"),
        GeographyCountry("saudi_arabia", "Saudi Arabia", Continent.ASIA, 23.8859, 45.0792, false, "🇸🇦", "Riyadh"),
        GeographyCountry("pakistan", "Pakistan", Continent.ASIA, 30.3753, 69.3451, false, "🇵🇰", "Islamabad"),
        GeographyCountry("afghanistan", "Afghanistan", Continent.ASIA, 33.9391, 67.7100, true, "🇦🇫", "Kabul"),
        GeographyCountry("mongolia", "Mongolia", Continent.ASIA, 46.8625, 103.8467, true, "🇲🇳", "Ulaanbaatar"),
        GeographyCountry("south_korea", "South Korea", Continent.ASIA, 35.9078, 127.7669, false, "🇰🇷", "Seoul"),
        GeographyCountry("north_korea", "North Korea", Continent.ASIA, 40.3399, 127.5101, false, "🇰🇵", "Pyongyang"),
        GeographyCountry("vietnam", "Vietnam", Continent.ASIA, 14.0583, 108.2772, false, "🇻🇳", "Hanoi"),
        GeographyCountry("philippines", "Philippines", Continent.ASIA, 12.8797, 121.7740, false, "🇵🇭", "Manila"),
        GeographyCountry("malaysia", "Malaysia", Continent.ASIA, 4.2105, 101.9758, false, "🇲🇾", "Kuala Lumpur"),
        GeographyCountry("singapore", "Singapore", Continent.ASIA, 1.3521, 103.8198, false, "🇸🇬", "Singapore"),
        GeographyCountry("myanmar", "Myanmar", Continent.ASIA, 21.9162, 95.9560, false, "🇲🇲", "Naypyidaw"),
        GeographyCountry("bangladesh", "Bangladesh", Continent.ASIA, 23.6850, 90.3563, false, "🇧🇩", "Dhaka"),
        GeographyCountry("nepal", "Nepal", Continent.ASIA, 28.3949, 84.1240, true, "🇳🇵", "Kathmandu"),
        GeographyCountry("kazakhstan", "Kazakhstan", Continent.ASIA, 48.0196, 66.9237, true, "🇰🇿", "Nur-Sultan"),
        GeographyCountry("iraq", "Iraq", Continent.ASIA, 33.2232, 43.6793, false, "🇮🇶", "Baghdad"),
        GeographyCountry("israel", "Israel", Continent.ASIA, 31.0461, 34.8516, false, "🇮🇱", "Jerusalem"),

        // OCEANIA (8 countries)
        GeographyCountry("australia", "Australia", Continent.OCEANIA, -25.2744, 133.7751, false, "🇦🇺", "Canberra"),
        GeographyCountry("new_zealand", "New Zealand", Continent.OCEANIA, -40.9006, 174.8860, false, "🇳🇿", "Wellington"),
        GeographyCountry("papua_new_guinea", "Papua New Guinea", Continent.OCEANIA, -6.3150, 143.9555, false, "🇵🇬", "Port Moresby"),
        GeographyCountry("fiji", "Fiji", Continent.OCEANIA, -16.5780, 179.4144, false, "🇫🇯", "Suva"),
        GeographyCountry("solomon_islands", "Solomon Islands", Continent.OCEANIA, -9.6457, 160.1562, false, "🇸🇧", "Honiara"),
        GeographyCountry("vanuatu", "Vanuatu", Continent.OCEANIA, -15.3767, 166.9592, false, "🇻🇺", "Port Vila"),
        GeographyCountry("samoa", "Samoa", Continent.OCEANIA, -13.7590, -172.1046, false, "🇼🇸", "Apia"),
        GeographyCountry("tonga", "Tonga", Continent.OCEANIA, -21.1789, -175.1982, false, "🇹🇴", "Nuku'alofa")
    )

    fun getRandomCountry(): GeographyCountry = countries.random()
    fun getCountriesByContinent(continent: Continent): List<GeographyCountry> = countries.filter { it.continent == continent }
    fun getCountryById(id: String): GeographyCountry? = countries.find { it.id == id }
    fun searchCountries(query: String): List<GeographyCountry> {
        val lowerQuery = query.lowercase()
        return countries.filter {
            it.name.lowercase().contains(lowerQuery) ||
                    it.capital.lowercase().contains(lowerQuery) ||
                    it.continent.displayName.lowercase().contains(lowerQuery)
        }
    }
    fun getTotalCountries(): Int = countries.size
    fun getLandlockedCountries(): List<GeographyCountry> = countries.filter { it.isLandlocked }
}

// Keep your existing composable functions, just update the main screen function signature
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GeographyCountryDragDropPuzzleScreen(
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
    val TAG = "GeographyCountryDragDrop"

    // Game state
    var gameStage by remember { mutableStateOf(GameStage.INSTRUCTIONS) }
    var currentCountry by remember { mutableStateOf<GeographyCountry?>(null) }
    var selectedContinent by remember { mutableStateOf<Continent?>(null) }
    var currentScore by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var questionsAnswered by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<CountryPlacementResult?>(null) }

    // Grid validation info
    var gridValidationInfo by remember { mutableStateOf<String?>(null) }

    val maxQuestions = when (difficulty.lowercase()) {
        "easy" -> 8
        "medium" -> 12
        "hard" -> 15
        else -> 10
    }
    var timeRemaining by remember { mutableStateOf(maxQuestions * 30) }

    // Timer logic
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

    // Country selection and validation
    LaunchedEffect(gameStage, questionsAnswered) {
        if (gameStage == GameStage.CONTINENT_SELECTION && currentCountry == null) {
            // Get random country that has grid mapping
            val countriesWithGrids = GeographyCountryDatabase.countries.filter { country ->
                GeographyCountryGridSystem.getCorrectGridCells(country.id) != null
            }

            currentCountry = if (countriesWithGrids.isNotEmpty()) {
                countriesWithGrids.random()
            } else {
                GeographyCountryDatabase.getRandomCountry()
            }

            // Validate country has grid mapping
            currentCountry?.let { country ->
                val gridMapping = GeographyCountryGridSystem.getCorrectGridCells(country.id)
                if (gridMapping == null) {
                    Log.w(TAG, "⚠️ No grid mapping found for ${country.name} (${country.id})")
                    gridValidationInfo = "Grid mapping missing for ${country.name}"
                } else {
                    Log.d(TAG, "✅ Grid mapping found for ${country.name}: ${gridMapping.size} cells")
                    gridValidationInfo = "Grid cells: ${gridMapping.size}, Difficulty: ${GeographyCountryGridSystem.getCountryDifficulty(country.id)}"
                }
            }

            Log.d(TAG, "New country: ${currentCountry?.name} in ${currentCountry?.continent?.displayName}")
        }
    }

    fun moveToNextQuestion() {
        questionsAnswered++
        if (questionsAnswered >= maxQuestions) {
            gameStage = GameStage.COMPLETED
        } else {
            // Get next country with grid mapping
            val countriesWithGrids = GeographyCountryDatabase.countries.filter { country ->
                GeographyCountryGridSystem.getCorrectGridCells(country.id) != null
            }

            currentCountry = if (countriesWithGrids.isNotEmpty()) {
                countriesWithGrids.random()
            } else {
                GeographyCountryDatabase.getRandomCountry()
            }

            selectedContinent = null
            gameStage = GameStage.CONTINENT_SELECTION
            currentScore = 0
            gridValidationInfo = null
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
        // Header with enhanced info
        if (gameStage != GameStage.GRID_TESTING) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // (Grid validation dev info is no longer rendered to players.)
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(modifier = Modifier.weight(1f).widthIn(max = 1000.dp).fillMaxWidth()) {
        when (gameStage) {
            GameStage.INSTRUCTIONS -> {
                CountryInstructionsScreen(
                    onStartGame = {
                        gameStage = GameStage.CONTINENT_SELECTION
                        Log.d(TAG, "Starting country geography game with improved grid system")
                    },
                    onEnterTestingMode = {
                        gameStage = GameStage.GRID_TESTING
                        Log.d(TAG, "Entering grid testing mode")
                    }
                )
            }

            GameStage.GRID_TESTING -> {
                CountryGridTestingScreen(
                    onBack = { gameStage = GameStage.INSTRUCTIONS },
                    onExportMappings = { mappingCode ->
                        Log.d(TAG, "Grid testing completed")
                    }
                )
            }

            GameStage.CONTINENT_SELECTION -> {
                currentCountry?.let { country ->
                    CountryContinentSelectionScreen(
                        country = country,
                        showFeedback = showFeedback,
                        feedbackMessage = feedbackMessage,
                        questionsAnswered = questionsAnswered,
                        maxQuestions = maxQuestions,
                        onContinentSelected = { continentSelected ->
                            if (continentSelected == country.continent) {
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
                                feedbackMessage = "Wrong continent. ${country.name} is in ${country.continent.displayName}."
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
                currentCountry?.let { country ->
                    selectedContinent?.let { continent ->
                        CountryPrecisePlacementScreen(
                            country = country,
                            continent = continent,
                            showFeedback = showFeedback,
                            lastResult = lastResult,
                            questionsAnswered = questionsAnswered,
                            maxQuestions = maxQuestions,
                            onPlacementComplete = { result ->
                                lastResult = result
                                currentScore += result.score
                                totalScore += result.score
                                showFeedback = true

                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(2500)
                                    showFeedback = false
                                    moveToNextQuestion()
                                }
                            }
                        )
                    }
                }
            }

            GameStage.COMPLETED -> {
                CountryCompletionScreen(
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

// Keep all your existing Composable functions - they're already well implemented
// Just add these improved functions for the grid system:

@Composable
private fun CountryInstructionsScreen(
    onStartGame: () -> Unit,
    onEnterTestingMode: () -> Unit = {}
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
                text = stringResource(R.string.country_geography_challenge),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Place countries in their correct locations using AI-powered grid system",
                fontSize = 16.sp,
                color = RvInkSoft.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🤖 AI-Enhanced Geography Game",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val features = listOf(
                        "✅ GPT-4 Vision powered grid mappings",
                        "🎯 Mobile-optimized grid sizes per continent",
                        "🏆 Intelligent difficulty scaling",
                        "📱 Touch-friendly country selection",
                        "🌍 Realistic continent shapes"
                    )

                    features.forEach { feature ->
                        Text(
                            text = feature,
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

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onEnterTestingMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RvInkSoft)
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

// [Keep all your other existing Composable functions exactly as they are - they're already well implemented]
// The key changes are in the GeographyCountryGridSystem object with the updated mappings and scoring system.

// Missing Composable definitions:

@Composable
private fun CountryContinentSelectionScreen(
    country: GeographyCountry,
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
                CountryCard(country = country)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Question ${questionsAnswered + 1} of $maxQuestions",
                        color = RvInkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Which continent is ${country.name} located in?",
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
                CountryContinentMap(onContinentSelected = onContinentSelected, modifier = Modifier.fillMaxSize())
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
private fun CountryContinentMap(
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
private fun CountryPrecisePlacementScreen(
    country: GeographyCountry,
    continent: Continent,
    showFeedback: Boolean,
    lastResult: CountryPlacementResult?,
    questionsAnswered: Int,
    maxQuestions: Int,
    onPlacementComplete: (CountryPlacementResult) -> Unit
) {
    var selectedGridCells by remember { mutableStateOf<List<GridCell>>(emptyList()) }
    var gridResult by remember { mutableStateOf<GridPlacementResult?>(null) }
    var isSelectionMode by remember { mutableStateOf(true) }
    var showHints by remember { mutableStateOf(false) }

    val (gridRows, gridCols) = GeographyCountryGridSystem.getGridDimensions(continent)
    val correctGrids = GeographyCountryGridSystem.getCorrectGridCells(country.id)
    val expandedValidArea = GeographyCountryGridSystem.getExpandedValidArea(country.id)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val compact = maxHeight < 600.dp
        val topInfo: @Composable (Modifier) -> Unit = { m ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountryCard(country = country, compact = compact)
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
                            text = "Select grid sections where ${country.name} is located",
                            color = RvInk,
                            fontSize = if (compact) 14.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Selection helpers (Clear / Hints)
                if (isSelectionMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedGridCells.size}",
                            color = RvInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { selectedGridCells = emptyList() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            border = BorderStroke(1.dp, RvCoralEdge)
                        ) {
                            Text(stringResource(R.string.clear), fontSize = 14.sp, color = RvInk, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showHints = !showHints },
                            modifier = Modifier.heightIn(min = 48.dp),
                            border = BorderStroke(if (showHints) 2.dp else 1.dp, if (showHints) RvSunEdge else RvOutline)
                        ) {
                            Text(if (showHints) "Hide Hints" else "Show Hints", fontSize = 14.sp, color = RvInk, maxLines = 1)
                        }
                    }
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

            EnhancedCountryMultiSelectGridOverlay(
                rows = gridRows,
                cols = gridCols,
                selectedCells = selectedGridCells,
                correctCells = if (showFeedback) correctGrids else null,
                expandedValidArea = if (showHints) expandedValidArea else null,
                isSelectionMode = isSelectionMode,
                onCellToggled = { gridCell ->
                    if (isSelectionMode) {
                        selectedGridCells = if (selectedGridCells.contains(gridCell)) {
                            selectedGridCells - gridCell
                        } else {
                            selectedGridCells + gridCell
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
                            result.score >= 25 -> RvSuccess.copy(alpha = 0.8f)
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

                        correctGrids?.let { correct ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Expected: ${correct.size} cells | You selected: ${selectedGridCells.size}",
                                color = RvInkSoft.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            if (!result.isCorrect && result.score >= 15) {
                                Text(
                                    text = "Close enough! Geography is tricky.",
                                    color = RvInkSoft.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
                }
            }
        }
        // Primary action, pinned at the bottom, >= 56dp
        val submitBar: @Composable (Modifier) -> Unit = { m ->
            if (isSelectionMode) {
                Button(
                    onClick = {
                        if (selectedGridCells.isNotEmpty()) {
                            isSelectionMode = false

                            // Use the more forgiving scoring system
                            val result = GeographyCountryGridSystem.calculateGridScore(country.id, selectedGridCells)
                            gridResult = result

                            val placementResult = CountryPlacementResult(
                                score = result.score,
                                distance = if (result.isCorrect) 0.0 else 100.0,
                                accuracy = result.accuracy
                            )

                            CoroutineScope(Dispatchers.Main).launch {
                                delay(2000)
                                onPlacementComplete(placementResult)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvViolet,
                        contentColor = RvOnTone,
                        disabledContainerColor = RvDisabled,
                        disabledContentColor = RvInkSoft
                    ),
                    enabled = selectedGridCells.isNotEmpty(),
                    modifier = m.heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.submit), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topInfo(Modifier.weight(1f))
                    submitBar(Modifier.fillMaxWidth())
                }
                board(Modifier.weight(1.4f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth().align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topInfo(Modifier.fillMaxWidth())
                board(Modifier.weight(1f).fillMaxWidth())
                submitBar(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EnhancedCountryMultiSelectGridOverlay(
    rows: Int,
    cols: Int,
    selectedCells: List<GridCell>,
    correctCells: List<GridCell>?,
    expandedValidArea: List<GridCell>?,
    isSelectionMode: Boolean,
    onCellToggled: (GridCell) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (isSelectionMode) {
                        val cellWidth = size.width / cols
                        val cellHeight = size.height / rows

                        val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                        val row = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)

                        onCellToggled(GridCell(row, col))
                    }
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

        // Show expanded valid area as hints (light green)
        expandedValidArea?.forEach { cell ->
            if (!selectedCells.contains(cell) && correctCells?.contains(cell) != true) {
                val left = cell.col * cellWidth
                val top = cell.row * cellHeight

                drawRect(
                    color = RvSuccess.copy(alpha = 0.2f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                )
            }
        }

        // Highlight selected cells (orange)
        selectedCells.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = Color(0xFFFF9500).copy(alpha = 0.6f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Add border to selected cells
            drawRect(
                color = Color(0xFFFF6600),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }

        // Show correct cells during feedback (green)
        correctCells?.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            val isAlsoSelected = selectedCells.contains(cell)
            val cellColor = if (isAlsoSelected) {
                RvSuccess.copy(alpha = 0.8f) // Correct AND selected
            } else {
                RvSuccess.copy(alpha = 0.4f) // Correct but not selected
            }

            drawRect(
                color = cellColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )

            // Add checkmark for correct cells
            if (!isAlsoSelected) {
                val centerX = left + cellWidth / 2
                val centerY = top + cellHeight / 2
                val checkSize = minOf(cellWidth, cellHeight) * 0.3f

                // Draw checkmark
                drawLine(
                    color = RvSuccess,
                    start = Offset(centerX - checkSize/2, centerY),
                    end = Offset(centerX - checkSize/4, centerY + checkSize/2),
                    strokeWidth = 4.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = RvSuccess,
                    start = Offset(centerX - checkSize/4, centerY + checkSize/2),
                    end = Offset(centerX + checkSize/2, centerY - checkSize/2),
                    strokeWidth = 4.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun CountryCompletionScreen(
    totalScore: Int,
    questionsAnswered: Int,
    maxQuestions: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Country Geography Challenge Complete!",
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
                        text = "Countries Placed: $questionsAnswered",
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
private fun CountryCard(
    country: GeographyCountry,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .widthIn(min = if (compact) 88.dp else 120.dp, max = if (compact) 112.dp else 160.dp)
            .heightIn(min = if (compact) 48.dp else 80.dp),
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
                    text = country.flag,
                    fontSize = if (compact) 16.sp else 24.sp
                )
                Text(
                    text = country.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        text = "Capital: ${country.capital}",
                        fontSize = 12.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CountryGridTestingScreen(
    onBack: () -> Unit,
    onExportMappings: (String) -> Unit
) {
    var selectedContinent by remember { mutableStateOf(Continent.NORTH_AMERICA) }
    var selectedCountry by remember { mutableStateOf<GeographyCountry?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val countriesInContinent = GeographyCountryDatabase.getCountriesByContinent(selectedContinent)
    val (gridRows, gridCols) = GeographyCountryGridSystem.getGridDimensions(selectedContinent)

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
                text = "Country Grid Testing Mode",
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
                        selectedCountry = null
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

        Text(
            text = "Grid Testing: ${selectedContinent.displayName} (${gridRows}×${gridCols})",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Country list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(countriesInContinent) { country ->
                val gridMapping = GeographyCountryGridSystem.getCorrectGridCells(country.id)
                val difficulty = GeographyCountryGridSystem.getCountryDifficulty(country.id)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            gridMapping == null -> RvError
                            difficulty == "Easy" -> RvSuccess
                            difficulty == "Medium" -> RvSun
                            else -> RvError
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(country.flag, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = country.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk
                            )

                            if (gridMapping != null) {
                                Text(
                                    text = "${gridMapping.size} cells | $difficulty",
                                    fontSize = 12.sp,
                                    color = RvInkSoft.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    text = "No grid mapping",
                                    fontSize = 12.sp,
                                    color = RvInkSoft.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Grid Mappings Complete") },
            text = {
                Text("Your grid mappings are already integrated into the game. The system is using the improved mappings generated from your continent images.")
            },
            confirmButton = {
                Button(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun CountryMultiSelectGridOverlay(
    rows: Int,
    cols: Int,
    selectedCells: List<GridCell>,
    correctCells: List<GridCell>?,
    isSelectionMode: Boolean,
    onCellToggled: (GridCell) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (isSelectionMode) {
                        val cellWidth = size.width / cols
                        val cellHeight = size.height / rows

                        val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                        val row = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)

                        onCellToggled(GridCell(row, col))
                    }
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

        // Highlight selected cells
        selectedCells.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            drawRect(
                color = Color(0xFFFF9500).copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
        }

        // Highlight correct cells (during feedback)
        correctCells?.forEach { cell ->
            val left = cell.col * cellWidth
            val top = cell.row * cellHeight

            val isAlsoSelected = selectedCells.contains(cell)
            val cellColor = if (isAlsoSelected) {
                RvSuccess.copy(alpha = 0.7f)
            } else {
                RvSuccess.copy(alpha = 0.3f)
            }

            drawRect(
                color = cellColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
        }
    }
}