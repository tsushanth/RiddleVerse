package com.kreativekoala.riddleverse

/** Start-screen display name, category and benefits for the grid, classic, spatial and word games. */
object NewGameInfo {
    class Info(val name: String, val category: String, val benefits: List<String>)

    private val logic = "Logic Games"
    private val spatial = "Spatial Games"
    private val word = "Word Games"
    private val classic = "Classic Games"

    private val table: Map<String, Info> = mapOf(
        "sudoku" to Info("Sudoku", logic, listOf("Sharpen deductive reasoning.", "Build focus and patience.")),
        "binarygrid" to Info("Binary Grid", logic, listOf("Practice rule-based logic.", "Improve pattern recognition.")),
        "cagemath" to Info("Cage Math", logic, listOf("Strengthen mental arithmetic.", "Combine logic with number sense.")),
        "signgrid" to Info("Sign Grid", logic, listOf("Reason about order and comparison.", "Improve step-by-step deduction.")),
        "tilemerge" to Info("Tile Merge", classic, listOf("Plan several moves ahead.", "Improve number sense and strategy.")),
        "slidetiles" to Info("Slide Tiles", classic, listOf("Build spatial planning skills.", "Practice solving in stages.")),
        "lightflip" to Info("Light Flip", classic, listOf("Train cause-and-effect reasoning.", "Find efficient solutions.")),
        "safesweep" to Info("Safe Sweep", classic, listOf("Practice probability and deduction.", "Improve careful decision making.")),
        "disctower" to Info("Disc Tower", classic, listOf("Practice recursive thinking.", "Improve planning and sequencing.")),
        "pixellogic" to Info("Pixel Logic", spatial, listOf("Improve visual logic.", "Strengthen pattern recognition.")),
        "tubetint" to Info("Tube Tint", spatial, listOf("Practice sorting and planning.", "Improve working memory.")),
        "piecepack" to Info("Piece Pack", spatial, listOf("Improve spatial reasoning.", "Practice fitting shapes efficiently.")),
        "crateshift" to Info("Crate Shift", spatial, listOf("Build forward planning skills.", "Improve problem-solving.")),
        "conduit" to Info("Conduit", spatial, listOf("Train spatial visualisation.", "Practice connecting systems.")),
        "wordguess" to Info("Word Guess", word, listOf("Grow your vocabulary.", "Practice elimination logic.")),
        "wordladder" to Info("Word Ladder", word, listOf("Expand vocabulary and spelling.", "Improve flexible thinking.")),
        "letterballoons" to Info("Letter Balloons", word, listOf("Improve spelling recall.", "Practice smart letter guessing.")),
        "wordhive" to Info("Word Hive", word, listOf("Grow your vocabulary.", "Spot word patterns faster.")),
        "codecracker" to Info("Code Cracker", logic, listOf("Practice deductive reasoning.", "Improve systematic thinking."))
    )

    fun of(type: String): Info? = table[type.lowercase()]
}
