package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Memory Squares puzzles
 */
class MemorySquaresTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Memory Squares!",
            description = "Test your memory by remembering patterns of colored squares. Let's learn how to play!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "grid",
            title = "The Memory Grid",
            description = "This is your memory grid. During the game, some squares will light up in a pattern that you need to remember.",
            targetComponent = "grid"
        ),
        TutorialStep(
            id = "countdown",
            title = "Get Ready Phase",
            description = "The game starts with a countdown. Use this time to focus and prepare to memorize the pattern!",
            targetComponent = "countdown"
        ),
        TutorialStep(
            id = "memorize",
            title = "Memorization Phase",
            description = "Watch carefully! Squares will light up in cyan. You have a few seconds to memorize which squares are highlighted.",
            targetComponent = "grid"
        ),
        TutorialStep(
            id = "recall",
            title = "Recall Phase",
            description = "Now it's your turn! Tap any 4 squares to practice selecting. In the real game, you'll need to remember the exact pattern.",
            targetComponent = "grid",
            interactionRequired = true,
            expectedAction = "select_squares"
        ),
        TutorialStep(
            id = "lives",
            title = "Lives System",
            description = "You have 3 lives (hearts). If you tap wrong squares, you'll lose lives. Be careful!",
            targetComponent = "lives"
        ),
        TutorialStep(
            id = "submit",
            title = "Submit Your Answer",
            description = "When you think you've selected all the correct squares, tap 'Submit' to check your answer!",
            targetComponent = "submit"
        )
    )
}