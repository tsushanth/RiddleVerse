package com.kreativekoala.riddleverse


data class CreatePuzzleTutorialStep(
    val title: String,
    val description: String,
    val targetComponent: String,
    val id: String = "" // Empty for informational, non-empty for interactive
)

class CreatePuzzleTutorialManager {
    fun getTutorialSteps(): List<CreatePuzzleTutorialStep> = listOf(
        CreatePuzzleTutorialStep(
            title = "Welcome to Puzzle Creation! 🎨",
            description = "Create your own custom puzzles on any topic! Let's learn how to use this powerful feature.",
            targetComponent = "header",
            id = "" // Already empty - good
        ),
        CreatePuzzleTutorialStep(
            title = "Enter Your Topic 📝",
            description = "Type your topic here (max 3 words). Be specific - like 'Space Exploration' or 'Marvel Heroes'.",
            targetComponent = "topic_input",
            id = "" // CHANGED: Remove "topic_entered"
        ),
        CreatePuzzleTutorialStep(
            title = "Browse Topic Categories 📂",
            description = "Or tap any category below to explore pre-made topics. Each category has amazing themes to choose from!",
            targetComponent = "categories",
            id = "" // CHANGED: Remove "category_selected"
        ),
        CreatePuzzleTutorialStep(
            title = "Choose Puzzle Format 🎯",
            description = "Select your puzzle type! Try the NEW Crossword feature for an exciting challenge.",
            targetComponent = "format_dropdown",
            id = "" // CHANGED: Remove "format_selected"
        ),
        CreatePuzzleTutorialStep(
            title = "Set Number of Puzzles 🔢",
            description = "Choose how many puzzles to generate (1-10). More puzzles = longer gameplay!",
            targetComponent = "counter",
            id = "" // CHANGED: Remove "counter_used"
        ),
        CreatePuzzleTutorialStep(
            title = "Generate Your Puzzle! 🚀",
            description = "Hit this button to create your custom puzzle set. It takes 30-60 seconds to generate.",
            targetComponent = "generate_button",
            id = "" // CHANGED: Remove "generate_clicked"
        ),
        CreatePuzzleTutorialStep(
            title = "You're Ready! 🎉",
            description = "You can now create unlimited custom puzzles! Share them with friends and challenge yourself.",
            targetComponent = "header",
            id = "" // Already empty - good
        )
    )
}