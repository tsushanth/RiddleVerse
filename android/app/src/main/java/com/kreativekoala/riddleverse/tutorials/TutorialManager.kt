package com.kreativekoala.riddleverse

/**
 * Manages tutorial steps and configurations for different puzzle types
 */
class TutorialManager(private val puzzleType: String) {

    fun getStepsForPuzzleType(): List<TutorialStep> {
        return when (puzzleType.lowercase()) {
            "division" -> getDivisionSteps()
            "subtraction" -> getSubtractionSteps()
            "math" -> getMathSteps()
            "wordassociation" -> getWordAssociationSteps()
            "synonyms" -> getSynonymSteps()
            "anagram" -> getAnagramSteps()
            "memorysquares" -> getMemorySquaresSteps()
            "memorystory" -> getMemoryStorySteps()
            "trivia" -> getTriviaSteps()
            "imagepuzzle" -> ImagePuzzleTutorialManager().getTutorialSteps()
            else -> getDefaultSteps()
        }
    }

    fun getSampleData(): TutorialSampleData? {
        return when (puzzleType.lowercase()) {
            "division" -> TutorialSampleData(
                dividend = 872,
                divisor = 8,
                correctAnswer = 109
            )
            "math" -> TutorialSampleData(
                question = "What is 15 + 27?",
                options = listOf("42", "41", "43", "40")
            )
            else -> null
        }
    }

    private fun getDivisionSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Division Puzzles!",
            description = "Let's learn how to solve division problems step by step.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "problem",
            title = "Understanding the Problem",
            description = "Here's your division problem: 872 ÷ 8. You need to find what number, when multiplied by 8, equals 872.",
            targetComponent = "problem"
        ),
        TutorialStep(
            id = "valueButtons",
            title = "Building Your Answer",
            description = "Use these value buttons to build your answer. Tap '+' to add values, '-' to subtract them.",
            targetComponent = "valueButtons"
        ),
        TutorialStep(
            id = "tryAdd",
            title = "Try Adding Some Values",
            description = "Let's start by adding 10 tens (100). Tap the '+' button above the 10.",
            targetComponent = "valueButtons",
            highlightArea = "10",
            interactionRequired = true,
            expectedAction = "add_10"
        ),
        TutorialStep(
            id = "seeTotal",
            title = "Watch Your Total",
            description = "Great! See how your total changed? Keep adding values until you reach 109 (the answer to 872 ÷ 8).",
            targetComponent = "answerCircle"
        ),
        TutorialStep(
            id = "submit",
            title = "Submit Your Answer",
            description = "When you think you have the right answer, tap the golden circle to submit!",
            targetComponent = "answerCircle",
            interactionRequired = true,
            expectedAction = "submit"
        )
    )

    private fun getSubtractionSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Subtraction Puzzles!",
            description = "Learn how to solve subtraction problems with our interactive system.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "problem",
            title = "Read the Problem",
            description = "Look at the subtraction problem and think about what the answer should be.",
            targetComponent = "problem"
        ),
        TutorialStep(
            id = "input",
            title = "Enter Your Answer",
            description = "Use the number pad or input field to enter your calculated answer.",
            targetComponent = "input"
        ),
        TutorialStep(
            id = "submit",
            title = "Submit Your Answer",
            description = "Tap the submit button to check if your answer is correct!",
            targetComponent = "submit",
            interactionRequired = true,
            expectedAction = "submit"
        )
    )

    private fun getMathSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Math Puzzles!",
            description = "Ready to challenge your math skills? Let's get started!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "question",
            title = "Read the Question",
            description = "Take your time to understand what the question is asking.",
            targetComponent = "question"
        ),
        TutorialStep(
            id = "answer",
            title = "Choose Your Answer",
            description = "Select the answer you think is correct from the options provided.",
            targetComponent = "options",
            interactionRequired = true,
            expectedAction = "select_option"
        )
    )

    private fun getWordAssociationSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Word Association!",
            description = "Match words with their related concepts. Let's learn how!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "words",
            title = "Look at the Words",
            description = "You'll see words that need to be matched with their associations.",
            targetComponent = "wordList"
        ),
        TutorialStep(
            id = "match",
            title = "Make Connections",
            description = "Drag or tap to connect words that are related to each other.",
            targetComponent = "matchArea",
            interactionRequired = true,
            expectedAction = "make_match"
        )
    )

    private fun getSynonymSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Synonym Puzzles!",
            description = "Group words that have similar meanings together.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "words",
            title = "Examine the Words",
            description = "Look at all the words provided and think about their meanings.",
            targetComponent = "wordGrid"
        ),
        TutorialStep(
            id = "group",
            title = "Group Similar Words",
            description = "Drag words with similar meanings into the same group.",
            targetComponent = "groupArea",
            interactionRequired = true,
            expectedAction = "group_words"
        )
    )

    private fun getAnagramSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Anagram Puzzles!",
            description = "Unscramble letters to form words. Let's learn how!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "letters",
            title = "Look at the Letters",
            description = "You'll see scrambled letters that form a word when rearranged.",
            targetComponent = "letterGrid"
        ),
        TutorialStep(
            id = "unscramble",
            title = "Rearrange the Letters",
            description = "Tap letters in the correct order to spell the word.",
            targetComponent = "inputArea",
            interactionRequired = true,
            expectedAction = "spell_word"
        )
    )

    private fun getMemorySquaresSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Memory Squares!",
            description = "Test your memory by remembering the pattern of squares.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "pattern",
            title = "Watch the Pattern",
            description = "Pay attention to which squares light up and in what order.",
            targetComponent = "grid"
        ),
        TutorialStep(
            id = "repeat",
            title = "Repeat the Pattern",
            description = "Tap the squares in the same order you saw them light up.",
            targetComponent = "grid",
            interactionRequired = true,
            expectedAction = "repeat_pattern"
        )
    )

    private fun getMemoryStorySteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Memory Story!",
            description = "Read the story carefully and answer questions about it.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "read",
            title = "Read the Story",
            description = "Take your time to read and understand the story content.",
            targetComponent = "story"
        ),
        TutorialStep(
            id = "questions",
            title = "Answer Questions",
            description = "Answer questions based on what you remember from the story.",
            targetComponent = "questions",
            interactionRequired = true,
            expectedAction = "answer_question"
        )
    )

    private fun getTriviaSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Trivia!",
            description = "Test your knowledge with interesting trivia questions.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "question",
            title = "Read the Question",
            description = "Carefully read the trivia question and think about the answer.",
            targetComponent = "question"
        ),
        TutorialStep(
            id = "answer",
            title = "Select Your Answer",
            description = "Choose the answer you think is correct from the multiple choices.",
            targetComponent = "options",
            interactionRequired = true,
            expectedAction = "select_answer"
        )
    )

    private fun getDefaultSteps() = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to the Puzzle!",
            description = "Let's learn how to play this puzzle type.",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "instructions",
            title = "How to Play",
            description = "Read the question carefully and select or enter your answer.",
            targetComponent = "question"
        ),
        TutorialStep(
            id = "submit",
            title = "Submit Your Answer",
            description = "When you're ready, submit your answer to see if you're correct!",
            targetComponent = "submit",
            interactionRequired = true,
            expectedAction = "submit"
        )
    )
}