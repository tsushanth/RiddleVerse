package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Math Estimation puzzles
 */
class MathEstimationTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Math Estimation!",
            description = "Learn to estimate sums by dragging your finger on an interactive chart. Let's get started!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "chart_explanation",
            title = "The Estimation Chart",
            description = "This chart shows data points with values. Your goal is to estimate the total sum of all these values.",
            targetComponent = "chart"
        ),
        TutorialStep(
            id = "data_points",
            title = "Understanding Data Points",
            description = "Each purple square represents a number. Look at the values shown next to each point to help estimate the total.",
            targetComponent = "chart"
        ),
        TutorialStep(
            id = "drag_to_estimate",
            title = "Drag to Set Your Estimate",
            description = "Drag your finger up and down on the chart to set your estimate. Watch the green line and number change!",
            targetComponent = "chart",
            interactionRequired = true,
            expectedAction = "drag_estimate"
        ),
        TutorialStep(
            id = "current_estimate",
            title = "Your Current Estimate",
            description = "See your current estimate displayed below the chart. This updates as you drag!",
            targetComponent = "estimate_display"
        ),
        TutorialStep(
            id = "instructions_card",
            title = "Helpful Instructions",
            description = "The instruction card reminds you how to interact with the chart using finger gestures.",
            targetComponent = "instructions"
        ),
        TutorialStep(
            id = "submit_answer",
            title = "Submit Your Estimate",
            description = "When you're happy with your estimate, tap the submit button to check how close you were!",
            targetComponent = "submit",
            interactionRequired = true,
            expectedAction = "submit_estimate"
        )
    )
}