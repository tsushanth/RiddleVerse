package com.kreativekoala.riddleverse

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Extension functions and utilities for tutorial highlighting
 */

/**
 * Modifier extension for highlighting tutorial components
 */
fun Modifier.tutorialHighlight(
    targetComponent: String,
    currentTarget: String? = null,
    highlightColor: Color = Color(0xFF7B1FA2),
    borderWidth: Int = 3
): Modifier {
    return if (currentTarget == targetComponent) {
        this
            .border(
                width = borderWidth.dp,
                color = highlightColor,
                shape = RoundedCornerShape(8.dp)
            )
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(8.dp)
            )
    } else {
        this
    }
}

/**
 * Modifier for highlighting specific value buttons in division puzzles
 */
fun Modifier.tutorialHighlightValueButton(
    buttonValue: Int,
    highlightValue: Int?,
    isIncrement: Boolean = true,
    highlightColor: Color = Color(0xFF7B1FA2)
): Modifier {
    return if (highlightValue == buttonValue && isIncrement) {
        this.border(
            width = 3.dp,
            color = highlightColor,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        )
    } else {
        this
    }
}

/**
 * Composable state management for tutorial
 */
@Composable
fun rememberTutorialState(): MutableState<TutorialState> {
    return remember { mutableStateOf(TutorialState.NOT_STARTED) }
}

@Composable
fun rememberTutorialManager(puzzleType: String): TutorialManager {
    return remember(puzzleType) { TutorialManager(puzzleType) }
}

@Composable
fun rememberTutorialPreferences(context: android.content.Context): TutorialPreferences {
    return remember { TutorialPreferences(context) }
}

/**
 * Tutorial step controller composable
 */
@Composable
fun TutorialStepController(
    tutorialManager: TutorialManager,
    tutorialState: TutorialState,
    onStepChange: (Int) -> Unit,
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit
) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val steps = tutorialManager.getStepsForPuzzleType()

    LaunchedEffect(tutorialState) {
        if (tutorialState == TutorialState.COMPLETED || tutorialState == TutorialState.SKIPPED) {
            when (tutorialState) {
                TutorialState.COMPLETED -> onTutorialComplete()
                TutorialState.SKIPPED -> onTutorialSkipped()
                else -> {}
            }
        }
    }

    LaunchedEffect(currentStepIndex) {
        onStepChange(currentStepIndex)
    }

    // Return current step information
    DisposableEffect(Unit) {
        onDispose { }
    }
}

/**
 * Tutorial validation utilities
 */
object TutorialValidator {

    fun isValidAction(step: TutorialStep, action: String): Boolean {
        return step.expectedAction == action
    }

    fun shouldShowHighlight(
        tutorialState: TutorialState,
        currentStep: TutorialStep?,
        componentName: String
    ): Boolean {
        return tutorialState == TutorialState.ACTIVE &&
                currentStep?.targetComponent == componentName
    }

    fun getHighlightValue(
        tutorialState: TutorialState,
        currentStep: TutorialStep?
    ): String? {
        return if (tutorialState == TutorialState.ACTIVE) {
            currentStep?.highlightArea
        } else null
    }
}