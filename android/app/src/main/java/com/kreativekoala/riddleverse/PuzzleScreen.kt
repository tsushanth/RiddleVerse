package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun PuzzleScreen(
    viewModel: PuzzleViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    Log.d("PuzzleScreen", "🎯 PuzzleScreen composable called")
    Log.d("PuzzleScreen", "🔄 Flow type: ${if (viewModel.isCustomPuzzleFlow) "CUSTOM" else "AI-GENERATED"}")

    val currentPuzzle = viewModel.getCurrentPuzzle()
    val context = LocalContext.current
    var isLoadingNextPuzzle by remember { mutableStateOf(false) }

    if (currentPuzzle == null) {
        Log.w("PuzzleScreen", "⚠️ No current puzzle available")
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.loading_puzzle))
            }
        }
        return
    }

    // Show loading overlay when fetching next AI puzzle
    if (isLoadingNextPuzzle) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.loading_next_puzzle))
            }
        }
        return
    }

    // Create completion handler
    val completionHandler = remember {
        PuzzleCompletionHandler(viewModel, context, onComplete)
    }

    // Route to appropriate screen based on puzzle type
    when (viewModel.screenType) {
        PuzzleScreenType.SENTENCE_TRANSITIONS_SCREEN -> {
            SentenceTransitionsScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.GEOGRAPHY_COUNTRY_SELECTION_SCREEN -> {
            GeographyCountryScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.GEOGRAPHY_CITY_SELECTION_SCREEN -> {
            GeographyCityScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.CRYPTO_WORD_SCREEN -> {
            CryptoWordScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MULTIPLE_CHOICE -> {
            MultipleChoiceScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.IMAGE_QUESTION_SCREEN -> {
            ImageQuestionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.COLOR_SHAPE_MATCHING_SCREEN -> {
            ColorShapeMatchingScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SYMMETRY_SCREEN -> {
            SymmetryScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SYMBOL_SWIPE_SCREEN -> {
            SymbolSwipeScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.COLOR_TEXT_MATCHING_SCREEN -> {
            ColorTextMatchingScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.CROSSWORD_SCREEN -> {
            CrosswordScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.WORDPREFIX_SCREEN -> {
            WordPrefixScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.IMAGE_MATCH_SCREEN -> {
            SimpleImageMatchPuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.LETTER_SET_SCREEN -> {
            LetterSetScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_PREVIOUS_PAIR_SCREEN -> {
            MemoryPreviousPairScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_PREVIOUS_SINGLE_SCREEN -> {
            MemoryPreviousSingleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.CONTEXT_SWITCH_SCREEN -> {
            ContextSwitchScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MATH_CROSSWORD_SCREEN -> {
            MathCrosswordScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.PINBALL_DEFLECTOR_SCREEN -> {
            PinballDeflectorScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.UNIQUE_OBJECT_SCREEN -> {
            UniqueObjectScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.NUMBER_SEQUENCE_SCREEN -> {
            NumberSequenceScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.NUMBER_SUM_SCREEN -> {
            NumberSumScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MATH_EXPRESSION_SCREEN -> {
            MathExpressionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_SQUARES_SCREEN -> {
            MemorySquaresScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.DUAL_CARD_SCREEN -> {
            DualCardScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.IMAGE_PUZZLE_SCREEN -> {
            ImagePuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.FLOW_SCREEN -> {
            FlowPuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MULTI_MATCH_MUSIC_SCREEN -> {
            SimpleMultiMatchMusicPuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.PROGRESSIVE_REVEAL -> {
            ProgressiveRevealPuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }


        PuzzleScreenType.WALDO_PUZZLE_SCREEN -> {
            WaldoPuzzleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.IMAGE_VORTEX_SCREEN -> {
            ImageVortexScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.REAL_OR_AI -> {
            RealOrAiScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SWIPE_WORD_SCREEN -> {
            SwipeWordScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.JUMBLE_INPUT_SCREEN -> {
            JumbleInputScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.FALLING_GAME_SCREEN -> {
            FallingGameScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.PENDULUM_CHOICE_SCREEN -> {
            PendulumChoiceScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MATCH_SCREEN -> {
            MatchScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                context = context,
                onBack = onBack,
                onComplete = onComplete
            )
        }

        PuzzleScreenType.TRIANGLE_DOT_MEMORY_SCREEN -> {
            TriangleDotMemoryScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MATH_COMPARISON_SCREEN -> {
            MathComparisonScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.WORD_SEARCH_SCREEN -> {
            WordSearchScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.WORD_SNAKE_SCREEN -> {
            WordSnakeScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.FIND_DIFFERENCES -> {
            FindDifferencesScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.FIND_OBJECT -> {
            FindObjectScreenWrapper (
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MATH_ESTIMATION_SCREEN -> {
            MathEstimationScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.AVERAGES_SCREEN -> {
            AveragesScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.DIVISION_SCREEN -> {
            DivisionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.PERCENTAGE_SCREEN -> {
            PercentageScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.DISCOUNT_PRICE_SCREEN -> {
            DiscountPriceScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SUBSCRIPTION_SCREEN -> {
            SubscriptionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.CONVERSION_SCREEN -> {
            ConversionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SUBTRACTION_SCREEN -> {
            SubtractionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.TIP_BUBBLE_SCREEN -> {
            TipBubbleScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.ANTONYM_BALLOON_SCREEN -> {
            AntonymBalloonScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.SYNONYM_GROUPING_SCREEN -> {
            SynonymGroupingScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_STORY_SCREEN -> {
            MemoryStoryScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_SEQUENCING_SCREEN -> {
            MemorySequencingScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        PuzzleScreenType.MEMORY_RETENTION_SCREEN -> {
            MemoryRetentionScreenWrapper(
                currentPuzzle = currentPuzzle,
                viewModel = viewModel,
                onBack = onBack,
                handlePuzzleCompletion = completionHandler::handlePuzzleCompletion
            )
        }

        else -> {
            Log.d("PuzzleScreen", "🎮 Showing default QAPuzzleScreen")
            QAPuzzleScreen(
                puzzle = currentPuzzle,
                onBack = onBack,
                onContinue = {
                    val nextIntent = viewModel.getNextPuzzleIntent(context)
                    if (nextIntent != null) {
                        context.startActivity(nextIntent)
                        (context as? PuzzleActivity)?.finish()
                    } else {
                        onComplete()
                    }
                },
                onCorrectAnswer = { inTime ->
                    viewModel.handlePuzzleCompletion(context, true, inTime)
                }
            )
        }
    }

    // Show hint dialog
    if (viewModel.showHintDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleHintDialog() },
            confirmButton = {
                Button(onClick = { viewModel.toggleHintDialog() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text("💡 Hint") },
            text = { Text(currentPuzzle.hint ?: "No hint available") }
        )
    }
}

