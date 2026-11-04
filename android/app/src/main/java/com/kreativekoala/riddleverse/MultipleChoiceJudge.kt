package com.kreativekoala.riddleverse

/**
 * Single place that decides whether a multiple-choice answer is right.
 *
 * Why this exists: the screen used to hand `true` to its `onContinue` callback and the wrappers then
 * compared that value to the answer string (`true == "42"`), so every multiple-choice result (Math,
 * Trivia, Q&A, Sentence Transitions) was reported as wrong. The screen now passes the tapped option and
 * both the on-screen highlight and the result use this function.
 */
object MultipleChoiceJudge {

    /** [selected] is whatever the screen's onContinue handed over; anything but the tapped option text is wrong. */
    fun isCorrect(selected: Any?, correct: String): Boolean {
        val s = (selected as? String) ?: return false
        return sameAnswer(s, correct)
    }

    /**
     * Trim + case-insensitive, and numbers compare by value ("42" == "42.0" == "42.00"), because server-made
     * math options and answer keys are not always formatted the same way. Never treats two blanks as a match.
     */
    fun sameAnswer(a: String, b: String): Boolean {
        val x = a.trim()
        val y = b.trim()
        if (x.isEmpty() || y.isEmpty()) return false
        if (x.equals(y, ignoreCase = true)) return true
        val nx = x.replace(",", "").toDoubleOrNull()
        val ny = y.replace(",", "").toDoubleOrNull()
        return nx != null && ny != null && nx == ny
    }
}
