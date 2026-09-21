package com.kreativekoala.riddleverse

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Group D game screens on the 7 device classes. No scrolling allowed to reach primary controls. */
class DLayoutTest : LayoutMatrixBase() {

    /** Every clickable node must lie fully inside the root (i.e. reachable without scrolling). */
    private fun assertClickablesInside(what: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("$what: no clickable nodes rendered", nodes.isNotEmpty())
        nodes.forEach { n ->
            val b = n.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) return@forEach
            assertTrue("$what: clickable $b outside root $root",
                b.left >= root.left - 1 && b.top >= root.top - 1 &&
                    b.right <= root.right + 1 && b.bottom <= root.bottom + 1)
        }
    }

    private fun assertInside(what: String, node: SemanticsNodeInteraction) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val b = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$what: $b outside root $root",
            b.left >= root.left - 1 && b.top >= root.top - 1 &&
                b.right <= root.right + 1 && b.bottom <= root.bottom + 1)
    }

    // ---- Memory squares (non adaptive) ----
    private fun memorySquares(fs: Float) {
        render(fs) {
            MemorySquaresPuzzleScreen(difficulty = "Hard", timer = "1:30", hearts = 3, level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertOnScreen(compose.onNodeWithTag("memory_board"))
        assertInside("memory board", compose.onNodeWithTag("memory_board"))
        assertClickablesInside("memory squares")
    }

    private fun adaptiveMemorySquares(fs: Float) {
        render(fs) {
            AdaptiveMemorySquaresPuzzleScreen(initialDifficulty = "Hard", timer = "1:30", hearts = 3, level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertOnScreen(compose.onNodeWithTag("memory_board"))
        assertInside("adaptive memory board", compose.onNodeWithTag("memory_board"))
        assertClickablesInside("adaptive memory squares")
    }

    // ---- Average ----
    private fun average(fs: Float) {
        render(fs) {
            AveragePuzzleScreen(difficulty = "Medium", timer = "1:00", numbers = listOf(12, 18, 24, 30, 36),
                correctAverage = 24.0, onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("average")
    }

    private fun adaptiveAverage(fs: Float) {
        render(fs) {
            AdaptiveAveragePuzzleScreen(initialDifficulty = "Medium", timer = "1:00",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("adaptive average")
    }

    // ---- Symmetry ----
    private val symData = """{"gridSize":4,"difficulty":"Medium","questionNumber":1,"isMirror":true,"mirrorType":"horizontal","leftPattern":[[true,false,true,false],[false,true,false,false],[true,true,false,true],[false,false,true,false]]}"""

    private fun symmetry(fs: Float) {
        render(fs) {
            SymmetryPuzzleScreen(difficulty = "Medium", timer = "1:00", hearts = 3, level = "1/5",
                puzzleData = symData, onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("symmetry")
    }

    private fun adaptiveSymmetry(fs: Float) {
        render(fs) {
            AdaptiveSymmetryPuzzleScreen(initialDifficulty = "Medium", timer = "1:00", hearts = 3, level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("adaptive symmetry")
    }

    // ---- Crypto ----
    private fun crypto(fs: Float) {
        val text = "HELLO WORLD"
        val mapping = text.filter { it.isLetter() }.toSet().mapIndexed { i, c -> c to i + 1 }.toMap()
        render(fs) {
            AdaptiveCryptoWordScreenWithTimer(
                initialPuzzleData = CryptoPuzzleData(text, mapping, setOf('L'), null),
                initialDifficulty = "Medium", timer = "2:00", hearts = 3, level = "1/5",
                onBack = {}, onComplete = { _, _ -> })
        }
        assertClickablesInside("crypto")
    }

    // ---- Crossword ----
    private val crossData = """{"matrix":[["C","A","T","_","_"],["_","_","O","_","_"],["D","O","G","S","_"],["_","_","_","_","_"],["_","_","_","_","_"]],"words":[{"word":"CAT","hint":"Pet","startX":0,"startY":0,"direction":"across","length":3},{"word":"TOG","hint":"Wear","startX":2,"startY":0,"direction":"down","length":3},{"word":"DOGS","hint":"Pets","startX":0,"startY":2,"direction":"across","length":4}]}"""

    private fun crossword(fs: Float) {
        render(fs) {
            CrosswordPuzzleScreen(difficulty = "Easy", timer = "5:00", puzzleData = crossData,
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("crossword")
    }

    // ---- Flow ----
    private fun flow(fs: Float) {
        val pairs = listOf(
            mapOf("id" to "a", "color" to "#E53E3E", "label" to "A", "start" to mapOf("x" to 0, "y" to 0), "end" to mapOf("x" to 3, "y" to 0)),
            mapOf("id" to "b", "color" to "#3182CE", "label" to "B", "start" to mapOf("x" to 0, "y" to 1), "end" to mapOf("x" to 3, "y" to 3)),
        )
        render(fs) {
            FlowPuzzleScreen(difficulty = "easy", timer = "2:00", hearts = 3, level = "1/10", gridSize = 4,
                flowPairs = pairs, correctAnswer = "", timeLimit = 120000,
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("flow")
    }

    // ---- Memory story ----
    private val storyData = """{"storyCard":"Sam bought apples, bread and milk.","question":"What did Sam buy?","scenario":"grocery","character":"Sam","itemCount":3,"options":["Apples","Bread","Milk","Eggs","Rice","Fish"]}"""

    private fun memoryStory(fs: Float) {
        render(fs) {
            MemoryStoryPuzzleScreen(difficulty = "Medium", timer = "2:00", hearts = 3, level = "1/5",
                puzzleData = storyData, correctAnswer = """["Apples","Bread","Milk"]""",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertClickablesInside("memory story")
    }

    // ---- Falling game ----
    private fun falling(fs: Float) {
        render(fs) {
            FallingGameScreen(puzzleId = "p1", question = "Which planet is the largest?", difficulty = "Easy",
                options = listOf("Mars", "Jupiter", "Venus", "Earth"), correctAnswer = "Jupiter",
                selectedOption = null, timerSeconds = 60, questionNumber = 1, totalQuestions = 5,
                onOptionSelected = {}, onContinue = {}, onCorrectAnswer = {}, onHint = {}, onBack = {})
        }
        assertClickablesInside("falling")
    }

    // ---- Image puzzle (game screen with fake pieces) ----
    private fun image(fs: Float) {
        val bmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val n = 3
        val pieces = (0 until n * n).map { ImagePuzzlePiece(it, it / n, it % n, bmp) }
        val cfg = generateAdaptiveImagePuzzleConfig(
            DifficultyManager.DifficultyLevel(name = "Medium", index = 2, timeLimit = 240, livesAllowed = 3, basePoints = 100)
        )
        val data = AdaptiveImagePuzzleData("p", "u", "Theme", "Desc", n, n * n, 120000, pieces, bmp, cfg, "Medium")
        render(fs) {
            AdaptiveImagePuzzleGameScreen(puzzleData = data, puzzlePieces = pieces,
                gridSlots = (0 until n * n).map { GridSlot(it / n, it % n) }, selectedPieceId = -1,
                showPreview = false, onPieceSelected = {}, onPiecePlaced = { _, _, _ -> },
                onPieceRemoved = {}, onPieceRotated = {}, onPreviewToggle = {})
        }
        assertClickablesInside("image")
    }

    // --- device rows ---

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun memorySquares_smallPhone() = memorySquares(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun memorySquares_compactPhone() = memorySquares(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun memorySquares_typicalPhone() = memorySquares(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun memorySquares_phoneLandscape() = memorySquares(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun memorySquares_foldableInner() = memorySquares(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun memorySquares_tabletPortrait() = memorySquares(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun memorySquares_tabletLandscape() = memorySquares(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMemorySquares_smallPhone() = adaptiveMemorySquares(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMemorySquares_compactPhone() = adaptiveMemorySquares(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMemorySquares_typicalPhone() = adaptiveMemorySquares(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMemorySquares_phoneLandscape() = adaptiveMemorySquares(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMemorySquares_foldableInner() = adaptiveMemorySquares(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMemorySquares_tabletPortrait() = adaptiveMemorySquares(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMemorySquares_tabletLandscape() = adaptiveMemorySquares(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun average_smallPhone() = average(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun average_compactPhone() = average(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun average_typicalPhone() = average(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun average_phoneLandscape() = average(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun average_foldableInner() = average(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun average_tabletPortrait() = average(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun average_tabletLandscape() = average(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveAverage_smallPhone() = adaptiveAverage(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveAverage_compactPhone() = adaptiveAverage(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveAverage_typicalPhone() = adaptiveAverage(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveAverage_phoneLandscape() = adaptiveAverage(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveAverage_foldableInner() = adaptiveAverage(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveAverage_tabletPortrait() = adaptiveAverage(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveAverage_tabletLandscape() = adaptiveAverage(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun symmetry_smallPhone() = symmetry(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun symmetry_compactPhone() = symmetry(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun symmetry_typicalPhone() = symmetry(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun symmetry_phoneLandscape() = symmetry(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun symmetry_foldableInner() = symmetry(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun symmetry_tabletPortrait() = symmetry(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun symmetry_tabletLandscape() = symmetry(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveSymmetry_smallPhone() = adaptiveSymmetry(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveSymmetry_compactPhone() = adaptiveSymmetry(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveSymmetry_typicalPhone() = adaptiveSymmetry(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveSymmetry_phoneLandscape() = adaptiveSymmetry(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveSymmetry_foldableInner() = adaptiveSymmetry(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveSymmetry_tabletPortrait() = adaptiveSymmetry(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveSymmetry_tabletLandscape() = adaptiveSymmetry(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun crypto_smallPhone() = crypto(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun crypto_compactPhone() = crypto(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun crypto_typicalPhone() = crypto(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun crypto_phoneLandscape() = crypto(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun crypto_foldableInner() = crypto(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun crypto_tabletPortrait() = crypto(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun crypto_tabletLandscape() = crypto(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun crossword_smallPhone() = crossword(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun crossword_compactPhone() = crossword(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun crossword_typicalPhone() = crossword(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun crossword_phoneLandscape() = crossword(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun crossword_foldableInner() = crossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun crossword_tabletPortrait() = crossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun crossword_tabletLandscape() = crossword(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun flow_smallPhone() = flow(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun flow_compactPhone() = flow(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun flow_typicalPhone() = flow(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun flow_phoneLandscape() = flow(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun flow_foldableInner() = flow(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun flow_tabletPortrait() = flow(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun flow_tabletLandscape() = flow(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun memoryStory_smallPhone() = memoryStory(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun memoryStory_compactPhone() = memoryStory(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun memoryStory_typicalPhone() = memoryStory(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun memoryStory_phoneLandscape() = memoryStory(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun memoryStory_foldableInner() = memoryStory(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun memoryStory_tabletPortrait() = memoryStory(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun memoryStory_tabletLandscape() = memoryStory(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun falling_smallPhone() = falling(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun falling_compactPhone() = falling(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun falling_typicalPhone() = falling(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun falling_phoneLandscape() = falling(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun falling_foldableInner() = falling(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun falling_tabletPortrait() = falling(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun falling_tabletLandscape() = falling(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun image_smallPhone() = image(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun image_compactPhone() = image(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun image_typicalPhone() = image(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun image_phoneLandscape() = image(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun image_foldableInner() = image(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun image_tabletPortrait() = image(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun image_tabletLandscape() = image(1f)
}
