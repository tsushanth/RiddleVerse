package com.kreativekoala.riddleverse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

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
        onBack = onBack,
        starter = StarterPuzzle.isStarter(currentPuzzle)
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
    val colors = when (game) {
        GridGame.SUDOKU -> listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
        GridGame.BINAIRO -> listOf(Color(0xFF232526), Color(0xFF536976))
        GridGame.CAGE_MATH -> listOf(Color(0xFFf7971e), Color(0xFFffd200))
        GridGame.SIGN_GRID -> listOf(Color(0xFF11998e), Color(0xFF38ef7d))
    }
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = PuzzleTypeNames.displayName(game.key),
            emoji = game.emoji,
            instruction = stringResource(GridPuzzleFactory.rulesRes(game)),
            tip = stringResource(GridPuzzleFactory.tipRes(game)),
            backgroundColor = colors
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}
