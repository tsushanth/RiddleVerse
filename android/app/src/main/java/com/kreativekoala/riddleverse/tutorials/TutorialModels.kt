package com.kreativekoala.riddleverse

/**
 * Represents a single step in the tutorial
 */
data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val targetComponent: String?, // "problem", "valueButtons", "answerCircle"
    val highlightArea: String? = null, // Specific area to highlight (e.g., "10" for the 10 value button)
    val interactionRequired: Boolean = false, // Does this step require user interaction?
    val expectedAction: String? = null // What action should the user perform? ("add_10", "submit", etc.)
)

/**
 * Tutorial state enum
 */
enum class TutorialState {
    NOT_STARTED,
    ACTIVE,
    COMPLETED,
    SKIPPED
}

/**
 * Manages tutorial steps for division puzzles
 */
class DivisionTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Division Puzzles!",
            description = "Let's learn how to solve division problems step by step. We'll use 872 ÷ 8 as our example.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "problem",
            title = "Understanding the Problem",
            description = "Here's your division problem: 872 ÷ 8. You need to find what number equals 872 when divided by 8. Think of it as: what × 8 = 872?",
            targetComponent = "problem"
        ),
        TutorialStep(
            id = "valueButtons",
            title = "Building Your Answer",
            description = "Use these value buttons to build your answer. Each button represents a different place value. Tap '+' to add values, '-' to subtract them.",
            targetComponent = "valueButtons"
        ),
        TutorialStep(
            id = "tryAdd10",
            title = "Let's Start Building 109",
            description = "The answer is 109. Let's build it step by step. First, tap the '+' button above the 10 to add one ten.",
            targetComponent = "valueButtons",
            highlightArea = "10",
            interactionRequired = true,
            expectedAction = "add_10"
        ),
        TutorialStep(
            id = "addMore10",
            title = "Keep Adding Tens",
            description = "Great! Now keep tapping '+' above the 10 until your total reaches 100 (that's 10 tens).",
            targetComponent = "valueButtons",
            highlightArea = "10",
            interactionRequired = true,
            expectedAction = "reach_100"
        ),
        TutorialStep(
            id = "add1s",
            title = "Add the Ones",
            description = "Perfect! Now you have 100. We need 109 total, so add 9 ones. Tap the '+' above the 1 nine times.",
            targetComponent = "valueButtons",
            highlightArea = "1",
            interactionRequired = true,
            expectedAction = "add_ones"
        ),
        TutorialStep(
            id = "seeTotal",
            title = "Check Your Total",
            description = "Excellent! See how your total is now 109 in the golden circle? This is the answer to 872 ÷ 8 = 109.",
            targetComponent = "answerCircle"
        ),
        TutorialStep(
            id = "submit",
            title = "Submit Your Answer",
            description = "Now tap the golden circle to submit your answer and see if you're correct!",
            targetComponent = "answerCircle",
            interactionRequired = true,
            expectedAction = "submit"
        ),
        TutorialStep(
            id = "completed",
            title = "Great Job!",
            description = "You've completed the tutorial! You now know how to use the value buttons to build answers for division problems. Ready to try more?",
            targetComponent = "none"
        )
    )
}