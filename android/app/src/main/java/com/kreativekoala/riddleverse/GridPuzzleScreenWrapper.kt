package com.kreativekoala.riddleverse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Bridges the normal puzzle flow (PuzzleScreen dispatch) to the grid-logic games. */
@Composable
fun GridPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val game = GridGame.fromKey(currentPuzzle.puzzleType) ?: GridGame.SUDOKU
    val seed = remember(currentPuzzle.puzzleId) { GridPuzzleFactory.seedFrom(currentPuzzle) }
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/" +
        "${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"
    GridPuzzleScreen(
        game = game,
        difficulty = currentPuzzle.difficulty ?: "Medium",
        seed = seed,
        level = level,
        onGameComplete = { success, score -> handlePuzzleCompletion(success, true, score) },
        onBack = onBack
    )
}

/** Short tutorial (rules) per grid game, using the shared SimpleTutorialScreen. */
@Composable
fun GridTutorialScreen(
    type: String,
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit
) {
    val game = GridGame.fromKey(type) ?: GridGame.SUDOKU
    val (tip, colors) = when (game) {
        GridGame.SUDOKU -> "Tap a cell, then a digit. Use Notes to pencil in candidates; 3 mistakes ends the puzzle." to
            listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
        GridGame.BINAIRO -> "If two 1s sit side by side, the cells on both ends must be 0." to
            listOf(Color(0xFF232526), Color(0xFF536976))
        GridGame.CAGE_MATH -> "Start with single-cell cages and big products, then use rows and columns to narrow the rest." to
            listOf(Color(0xFFf7971e), Color(0xFFffd200))
        GridGame.SIGN_GRID -> "The biggest digit can never sit at the narrow end of a sign." to
            listOf(Color(0xFF11998e), Color(0xFF38ef7d))
    }
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = game.title,
            emoji = game.emoji,
            instruction = GridPuzzleFactory.rulesFor(game),
            tip = tip,
            backgroundColor = colors
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}
