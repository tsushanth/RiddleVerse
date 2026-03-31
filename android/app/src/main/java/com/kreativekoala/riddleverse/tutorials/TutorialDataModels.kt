package com.kreativekoala.riddleverse

/**
 * Tutorial configuration for different puzzle types
 */
data class TutorialConfig(
    val puzzleType: String,
    val steps: List<TutorialStep>,
    val sampleData: TutorialSampleData? = null
)

/**
 * Sample data for tutorial demonstrations
 */
data class TutorialSampleData(
    val dividend: Int? = null,
    val divisor: Int? = null,
    val correctAnswer: Int? = null,
    val question: String? = null,
    val options: List<String>? = null
)