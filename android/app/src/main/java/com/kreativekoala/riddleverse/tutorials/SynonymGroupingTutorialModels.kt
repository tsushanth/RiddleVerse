package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Synonym Grouping puzzles
 */
class SynonymGroupingTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Synonym Grouping!",
            description = "Learn to group words with similar meanings. Match each word to its correct synonym group!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "instructions",
            title = "How It Works",
            description = "You'll see a word in the center and colored groups below. Tap the group that contains synonyms of the center word.",
            targetComponent = "instructions"
        ),
        TutorialStep(
            id = "word_display",
            title = "The Word to Group",
            description = "This is the word you need to categorize. Look at it carefully and think about its meaning.",
            targetComponent = "word_display"
        ),
        TutorialStep(
            id = "synonym_groups",
            title = "Synonym Groups",
            description = "These colored circles represent different synonym groups. Each contains words with similar meanings.",
            targetComponent = "synonym_groups"
        ),
        TutorialStep(
            id = "make_selection",
            title = "Make Your Choice",
            description = "Tap the group that you think contains synonyms of the word above. Let's try one!",
            targetComponent = "synonym_groups",
            interactionRequired = true,
            expectedAction = "select_group"
        ),
        TutorialStep(
            id = "timer_lives",
            title = "Timer & Lives",
            description = "Watch the timer at the top and your heart lives. Wrong answers cost lives, so think carefully!",
            targetComponent = "timer_lives"
        ),
        TutorialStep(
            id = "scoring",
            title = "Keep Scoring",
            description = "Correct answers earn points! Group all words before time runs out to win. Good luck!",
            targetComponent = "scoring"
        )
    )
}