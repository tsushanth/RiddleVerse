package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Conversion Puzzle (Swipeable Blocks)
 */
class ConversionTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Conversion Puzzles!",
            description = "Learn how to compare different units by positioning blocks to show which value is greater. Let's get started!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "scale_overview",
            title = "Understanding the Scale",
            description = "The scale in the middle shows which values are greater or smaller. Higher position means greater value.",
            targetComponent = "scale"
        ),
        TutorialStep(
            id = "blocks_introduction",
            title = "Look at the Conversion Blocks",
            description = "Each block shows a value with its unit (like 5 kg or 10 lbs). Your goal is to compare which represents a greater amount.",
            targetComponent = "blocks"
        ),
        TutorialStep(
            id = "drag_first_block",
            title = "Drag a Block",
            description = "Try dragging one of the blocks up or down to position it on the scale. The block that represents the greater value should be positioned higher.",
            targetComponent = "blocks",
            interactionRequired = true,
            expectedAction = "drag_block"
        ),
        TutorialStep(
            id = "position_comparison",
            title = "Position for Comparison",
            description = "Great! Now position both blocks to show your comparison. The heavier/greater value goes higher, lighter/smaller goes lower.",
            targetComponent = "blocks",
            interactionRequired = true,
            expectedAction = "position_blocks"
        ),
        TutorialStep(
            id = "submit_answer",
            title = "Submit Your Answer",
            description = "Once you've positioned both blocks, tap CONTINUE to submit your comparison and see if you're correct!",
            targetComponent = "continue_button",
            interactionRequired = true,
            expectedAction = "submit_answer"
        ),
        TutorialStep(
            id = "feedback_explanation",
            title = "Learn from Feedback",
            description = "After submitting, you'll get feedback showing whether your comparison was correct and the actual conversion values.",
            targetComponent = "feedback"
        ),
        TutorialStep(
            id = "timer_hearts",
            title = "Watch Timer & Hearts",
            description = "Keep an eye on the timer at the top and your hearts. Complete conversions quickly and accurately to maintain your streak!",
            targetComponent = "timer"
        )
    )
}
