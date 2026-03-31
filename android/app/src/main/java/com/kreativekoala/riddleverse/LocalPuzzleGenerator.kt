package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import org.json.JSONObject

object LocalPuzzleGenerator {
    private const val TAG = "LocalPuzzleGenerator"

    fun generatePuzzle(puzzleType: String, difficulty: String, context: Context): Puzzle {
        Log.d(TAG, "🔄 Generating local puzzle: type=$puzzleType, difficulty=$difficulty")

        return when (puzzleType) {
            "memorypreviouspair" -> {
                Log.d(TAG, "🧠 Generating local Memory Previous Pair puzzle")
                val (questionJson, answerJson) = MemoryPreviousPairGenerator.generatePuzzle(difficulty)

                Puzzle(
                    puzzleId = "memory_previous_pair_${System.currentTimeMillis()}",
                    puzzleType = "memorypreviouspair",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Remember objects from the previous screen and tap the one that appeared before",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "memorysquares" -> {
                Log.d(TAG, "🧠 Generating local Memory Squares puzzle")
                val (questionJson, answerJson) = MemorySquaresGenerator.generatePuzzle(difficulty)

                Puzzle(
                    puzzleId = "memory_squares_${System.currentTimeMillis()}",
                    puzzleType = "memorysquares",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Memorize the positions of highlighted squares and recreate the pattern",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "memoryprevioussingle" -> {
                Log.d(TAG, "🧠 Generating local Memory Previous Single puzzle")
                val (questionJson, answerJson) = MemoryPreviousSingleGenerator.generatePuzzle(difficulty)

                Puzzle(
                    puzzleId = "memory_previous_single_${System.currentTimeMillis()}",
                    puzzleType = "memoryprevioussingle",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Remember only the previous object and compare it with the current one",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "pinballdeflector" -> {
                Log.d(TAG, "🎯 Generating local Pinball Deflector puzzle")
                val (questionJson, answerJson) = PinballDeflectorGenerator.generatePuzzle(difficulty)

                Puzzle(
                    puzzleId = "pinball_deflector_${System.currentTimeMillis()}",
                    puzzleType = "pinballdeflector",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Memorize deflector positions and predict ball path",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "symmetry" -> {
                Log.d(TAG, "🔄 Generating local Symmetry puzzle")
                val (questionJson, answerJson) = SymmetryPuzzleGenerator.generatePuzzle(difficulty, context)

                Puzzle(
                    puzzleId = "symmetry_${System.currentTimeMillis()}",
                    puzzleType = "symmetry",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Copy the exact pattern from the left grid to the right grid",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "mathcrossword" -> {
                Log.d(TAG, "🧮 Generating local Math Crossword puzzle")
                val (questionJson, answerJson) = MathCrosswordGenerator.generatePuzzle(difficulty)

                Puzzle(
                    puzzleId = "math_crossword_${System.currentTimeMillis()}",
                    puzzleType = "mathcrossword",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Complete the math equations by placing the missing numbers in the crossword grid",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "contextswitch" -> {
                Log.d(TAG, "🧠 Generating local Context Switch puzzle")
                val (questionJson, answerJson) = ContextSwitchPuzzleGenerator.generatePuzzle(difficulty, context)

                Puzzle(
                    puzzleId = "context_switch_${System.currentTimeMillis()}",
                    puzzleType = "contextswitch",
                    question = questionJson,
                    answer = answerJson,
                    hint = "Study the items, complete the task, then identify the original items",
                    difficulty = difficulty,
                    options = emptyList()
                )
            }

            "geography_countries", "geography_cities" -> Puzzle(
                puzzleId = "geography_${System.currentTimeMillis()}",
                puzzleType = "geography",
                question = "Solve geographical challenges by dragging items to their correct locations",
                answer = "completed",
                hint = "Drag items to their matching locations on the map",
                difficulty = difficulty,
                options = emptyList()
            )

            "mathexpression" -> Puzzle(
                puzzleId = "math_expression_${System.currentTimeMillis()}",
                puzzleType = "mathexpression",
                question = "Solve mathematical expressions by dragging the missing numbers and operators",
                answer = "completed",
                hint = "Drag numbers and operators to complete the equations",
                difficulty = difficulty,
                options = emptyList()
            )

            "dualcard" -> Puzzle(
                puzzleId = "dual_card_${System.currentTimeMillis()}",
                puzzleType = "dualcard",
                question = "Check if number is even or letter is vowel",
                answer = "completed",
                hint = "Alternate between checking numbers and letters",
                difficulty = difficulty,
                options = emptyList()
            )

            "crypto" -> Puzzle(
                puzzleId = "crypto_${System.currentTimeMillis()}",
                puzzleType = "crypto",
                question = "Where there is love",
                answer = "completed",
                hint = "Alternate between checking numbers and letters",
                difficulty = difficulty,
                options = emptyList()
            )

            "trainrouting" -> Puzzle(
                puzzleId = "train_routing_${System.currentTimeMillis()}",
                puzzleType = "trainrouting",
                question = "Route trains to their matching colored stations",
                answer = "completed",
                hint = "Tap junctions to switch track directions",
                difficulty = difficulty,
                options = emptyList()
            )

            "numbersequence" -> Puzzle(
                puzzleId = "number_sequence_${System.currentTimeMillis()}",
                puzzleType = "numbersequence",
                question = generateNumberSequenceConfig(difficulty),
                answer = "completed",
                hint = "Tap numbers in ascending order from 1 to N",
                difficulty = difficulty,
                options = emptyList()
            )

            "numbersum" -> Puzzle(
                puzzleId = "number_sum_${System.currentTimeMillis()}",
                puzzleType = "numbersum",
                question = "Add up all the highlighted numbers",
                answer = "completed",
                hint = "Calculate the sum of all selected numbers",
                difficulty = difficulty,
                options = emptyList()
            )

            "symbolswipe" -> Puzzle(
                puzzleId = "symbol_swipe_${System.currentTimeMillis()}",
                puzzleType = "symbolswipe",
                question = generateSymbolSwipeConfig(difficulty),
                answer = "completed",
                hint = "Swipe symbols in the direction shown by the corner arrows",
                difficulty = difficulty,
                options = emptyList()
            )

            "colormatching" -> Puzzle(
                puzzleId = "color_matching_${System.currentTimeMillis()}",
                puzzleType = "colormatching",
                question = generateColorMatchingConfig(difficulty),
                answer = "completed",
                hint = "Does the text match the shape color?",
                difficulty = difficulty,
                options = emptyList()
            )

            "imagevortex" -> Puzzle(
                puzzleId = "image_vortex_${System.currentTimeMillis()}",
                puzzleType = "imagevortex",
                question = generateImageVortexConfig(difficulty),
                answer = "completed",
                hint = "Identify new images that haven't appeared before",
                difficulty = difficulty,
                options = emptyList()
            )

            "memorysquares" -> Puzzle(
                puzzleId = "memory_squares_${System.currentTimeMillis()}",
                puzzleType = "memorysquares",
                question = "Remember the pattern of highlighted squares",
                answer = "completed",
                hint = "Watch the sequence carefully and recreate it",
                difficulty = difficulty,
                options = emptyList()
            )

            "triangledotmemory" -> Puzzle(
                puzzleId = "triangle_dot_memory_${System.currentTimeMillis()}",
                puzzleType = "triangledotmemory",
                question = generateTriangleDotConfig(difficulty),
                answer = "generated",
                hint = "Remember the dot positions in the triangle",
                difficulty = difficulty,
                options = emptyList()
            )

            "colortextmatching" -> Puzzle(
                puzzleId = "color_text_matching_${System.currentTimeMillis()}",
                puzzleType = "colortextmatching",
                question = generateColorTextMatchingConfig(difficulty),
                answer = "generated",
                hint = "Match text colors with their meanings",
                difficulty = difficulty,
                options = emptyList()
            )

            else -> Puzzle(
                puzzleId = "local_${puzzleType}_${System.currentTimeMillis()}",
                puzzleType = puzzleType,
                question = "Default local puzzle",
                answer = "completed",
                hint = "Complete the puzzle",
                difficulty = difficulty,
                options = emptyList()
            )
        }
    }

    private fun generateNumberSequenceConfig(difficulty: String): String {
        val numberCount = when(difficulty.lowercase()) {
            "easy" -> 6
            "medium" -> 8
            "hard" -> 10
            else -> 8
        }
        return """{"numberCount": $numberCount}"""
    }

    private fun generateSymbolSwipeConfig(difficulty: String): String {
        val symbolCount = when(difficulty.lowercase()) {
            "easy" -> 15
            "medium" -> 20
            "hard" -> 25
            else -> 20
        }
        val timePerSymbol = when(difficulty.lowercase()) {
            "easy" -> 3.0
            "medium" -> 2.5
            "hard" -> 2.0
            else -> 2.5
        }
        return """{"symbolCount": $symbolCount, "timePerSymbol": $timePerSymbol}"""
    }

    private fun generateColorMatchingConfig(difficulty: String): String {
        val challengeCount = when(difficulty.lowercase()) {
            "easy" -> 8
            "medium" -> 10
            "hard" -> 12
            else -> 10
        }
        val timePerChallenge = when(difficulty.lowercase()) {
            "easy" -> 3.5
            "medium" -> 3.0
            "hard" -> 2.5
            else -> 3.0
        }
        return """{"challengeCount": $challengeCount, "timePerChallenge": $timePerChallenge}"""
    }

    private fun generateImageVortexConfig(difficulty: String): String {
        val targetCount = when(difficulty.lowercase()) {
            "easy" -> 8
            "medium" -> 10
            "hard" -> 12
            else -> 10
        }
        val timeLimit = when(difficulty.lowercase()) {
            "easy" -> 180
            "medium" -> 150
            "hard" -> 120
            else -> 150
        }
        return """{"targetCount": $targetCount, "timeLimit": $timeLimit}"""
    }

    private fun generateTriangleDotConfig(difficulty: String): String {
        val totalQuestions = when(difficulty.lowercase()) {
            "easy" -> 8
            "medium" -> 12
            "hard" -> 16
            else -> 12
        }
        return """{"difficulty": "$difficulty", "totalQuestions": $totalQuestions}"""
    }

    private fun generateColorTextMatchingConfig(difficulty: String): String {
        val totalQuestions = when(difficulty.lowercase()) {
            "easy" -> 10
            "medium" -> 15
            "hard" -> 20
            else -> 15
        }
        return """{"difficulty": "$difficulty", "totalQuestions": $totalQuestions, "gameType": "colorTextMatching"}"""
    }
}