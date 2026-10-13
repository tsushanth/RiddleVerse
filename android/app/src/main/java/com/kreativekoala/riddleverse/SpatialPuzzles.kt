package com.kreativekoala.riddleverse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

// --- spatial puzzles (feat/puzzles-spatial) ---
// Single registry for the five spatial puzzle types. Shared files only call into this object.

object SpatialPuzzles {
    const val PIXEL_LOGIC = "pixellogic"
    const val TUBE_TINT = "tubetint"
    const val PIECE_PACK = "piecepack"
    const val CRATE_SHIFT = "crateshift"
    const val CONDUIT = "conduit"

    val TYPES: Set<String> = linkedSetOf(PIXEL_LOGIC, TUBE_TINT, PIECE_PACK, CRATE_SHIFT, CONDUIT)

    fun isSpatial(type: String?): Boolean = type != null && type.lowercase() in TYPES

    /** The "screenType" string stored in intents / matched by PuzzleScreenType.typeName. */
    fun screenOption(type: String): String = when (type.lowercase()) {
        PIXEL_LOGIC -> "Pixel Logic Screen"
        TUBE_TINT -> "Tube Tint Screen"
        PIECE_PACK -> "Piece Pack Screen"
        CRATE_SHIFT -> "Crate Shift Screen"
        CONDUIT -> "Conduit Screen"
        else -> "Default"
    }

    val categories: List<QuizCategory> = listOf(
        QuizCategory("Pixel Logic", "Fill the grid from the number clues", Icons.Default.GridOn, PIXEL_LOGIC, "Logic"),
        QuizCategory("Tube Tint", "Pour colours until each tube is one colour", Icons.Default.Opacity, TUBE_TINT, "Logic"),
        QuizCategory("Piece Pack", "Fit pieces and clear lines", Icons.Default.ViewModule, PIECE_PACK, "Logic"),
        QuizCategory("Crate Shift", "Push every crate onto a target", Icons.Default.Inventory2, CRATE_SHIFT, "Logic"),
        QuizCategory("Conduit", "Rotate pipes to power every lamp", Icons.Default.Timeline, CONDUIT, "Logic")
    )

    private fun hintFor(type: String) = when (type) {
        PIXEL_LOGIC -> "Numbers give the lengths of filled runs in each row and column. Start with the biggest numbers."
        TUBE_TINT -> "Pour the top colour onto the same colour or into an empty tube. Empty tubes are your workspace."
        PIECE_PACK -> "Fill whole rows or columns to clear them. Keep space for the next pieces."
        CRATE_SHIFT -> "You can only push one crate at a time, never pull. Avoid pushing crates into corners."
        else -> "Start from the source and work outwards. Corners and ends have only a few ways to turn."
    }

    /** Builds a normal [Puzzle] whose question JSON carries the seed; the screens regenerate everything from it. */
    fun generatePuzzle(type: String, difficulty: String): Puzzle {
        val t = type.lowercase()
        val seed = System.nanoTime() xor (System.currentTimeMillis() shl 7)
        val q = JSONObject().put("seed", seed).put("difficulty", difficulty).put("game", t).toString()
        return Puzzle(
            puzzleId = "${t}_${System.currentTimeMillis()}",
            puzzleType = t,
            question = q,
            answer = "completed",
            hint = hintFor(t),
            difficulty = difficulty,
            options = emptyList()
        )
    }

    fun seedOf(p: Puzzle): Long = try {
        JSONObject(p.question).getLong("seed")
    } catch (_: Exception) {
        p.puzzleId.hashCode().toLong()
    }

    fun tutorialContent(type: String): SimpleTutorialContent? = when (type.lowercase()) {
        PIXEL_LOGIC -> SimpleTutorialContent(
            title = "Pixel Logic", emoji = "🖼️",
            instruction = "Numbers beside each row and column say how long the runs of filled squares are, in order. Fill the squares that must be filled and cross out the ones that cannot be. Drag to paint a line, pinch or use the magnifier on big grids.",
            tip = "A clue that nearly fills its line tells you exactly where the squares go. Finished lines turn grey.",
            backgroundColor = listOf(Color(0xFF3A7BD5), Color(0xFF00D2FF))
        )
        TUBE_TINT -> SimpleTutorialContent(
            title = "Tube Tint", emoji = "🧪",
            instruction = "Tap a tube, then tap another to pour the top colour across. You can only pour onto the same colour or into an empty tube, and only if there is room. Sort until every tube is a single colour.",
            tip = "Free up an empty tube early. Stuck? Undo, or add one extra tube.",
            backgroundColor = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
        )
        PIECE_PACK -> SimpleTutorialContent(
            title = "Piece Pack", emoji = "🧱",
            instruction = "Drag a piece from the tray onto the board. Complete a full row or column to clear it. Clear the target number of lines before you run out of pieces, and before no piece fits.",
            tip = "Tap a piece, then tap the board if you prefer. Keep the board tidy: holes are hard to fill.",
            backgroundColor = listOf(Color(0xFFFF8008), Color(0xFFFFC837))
        )
        CRATE_SHIFT -> SimpleTutorialContent(
            title = "Crate Shift", emoji = "📦",
            instruction = "Walk into a crate to push it. Push every crate onto a target ring. You cannot pull crates or push two at once. Swipe on the board or use the arrows. Fewer moves earn more stars.",
            tip = "A crate in a corner is stuck for good, so use Undo freely.",
            backgroundColor = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
        )
        CONDUIT -> SimpleTutorialContent(
            title = "Conduit", emoji = "🔌",
            instruction = "Tap a tile to rotate it a quarter turn. Connect pipes so power flows from the glowing source to every lamp, with no open ends left over.",
            tip = "Edges and corners have few options. Fix those first.",
            backgroundColor = listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))
        )
        else -> null
    }
}

@Composable
fun SpatialTutorialScreen(
    type: String,
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit
) {
    val content = SpatialPuzzles.tutorialContent(type) ?: run { onTutorialSkipped(); return }
    SimpleTutorialScreen(content = content, onComplete = onTutorialComplete, onSkip = onTutorialSkipped)
}

/** Dispatch target for PuzzleScreen.kt. */
@Composable
fun SpatialPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val difficulty = remember(currentPuzzle) {
        (currentPuzzle.difficulty?.takeIf { it.isNotBlank() } ?: "Medium")
            .lowercase().replaceFirstChar { it.uppercase() }
    }
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/" +
        "${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"
    val args = remember(currentPuzzle.puzzleId, difficulty, level) {
        SpatialArgs(
            seed = SpatialPuzzles.seedOf(currentPuzzle),
            difficulty = difficulty,
            level = level,
            onFinished = { success, score -> handlePuzzleCompletion(success, true, score) },
            onBack = onBack
        )
    }
    key(currentPuzzle.puzzleId) {
        when (viewModel.screenType) {
            PuzzleScreenType.PIXEL_LOGIC_SCREEN -> PixelLogicScreen(args)
            PuzzleScreenType.TUBE_TINT_SCREEN -> TubeTintScreen(args)
            PuzzleScreenType.PIECE_PACK_SCREEN -> PiecePackScreen(args)
            PuzzleScreenType.CRATE_SHIFT_SCREEN -> CrateShiftScreen(args)
            PuzzleScreenType.CONDUIT_SCREEN -> ConduitScreen(args)
            else -> when (currentPuzzle.puzzleType?.lowercase()) {
                SpatialPuzzles.PIXEL_LOGIC -> PixelLogicScreen(args)
                SpatialPuzzles.TUBE_TINT -> TubeTintScreen(args)
                SpatialPuzzles.PIECE_PACK -> PiecePackScreen(args)
                SpatialPuzzles.CRATE_SHIFT -> CrateShiftScreen(args)
                else -> ConduitScreen(args)
            }
        }
    }
}
