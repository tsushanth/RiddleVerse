package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Antonym Balloon puzzles
 */
class AntonymBalloonTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Antonym Balloons!",
            description = "Learn how to match opposite words by popping balloon pairs. Let's get started!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "balloons",
            title = "Look at the Balloons",
            description = "Each balloon contains a word. Your goal is to find balloons with opposite meanings (antonyms).",
            targetComponent = "balloons"
        ),
        TutorialStep(
            id = "select_first",
            title = "Select Your First Balloon",
            description = "Tap any balloon to select it. Let's try tapping a balloon with a word you recognize!",
            targetComponent = "balloons",
            interactionRequired = true,
            expectedAction = "select_balloon"
        ),
        TutorialStep(
            id = "find_match",
            title = "Find Its Opposite",
            description = "Great! Now find the balloon with the opposite meaning and tap it to make a match.",
            targetComponent = "balloons",
            interactionRequired = true,
            expectedAction = "match_balloons"
        ),
        TutorialStep(
            id = "progress",
            title = "Watch Your Progress",
            description = "When you make a correct match, both balloons will pop! Keep matching until all pairs are found.",
            targetComponent = "progress"
        ),
        TutorialStep(
            id = "timer",
            title = "Beat the Clock",
            description = "Watch the timer at the top! Complete all matches before time runs out. Good luck!",
            targetComponent = "timer"
        )
    )
}
