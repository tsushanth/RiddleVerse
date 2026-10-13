package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Piece Pack (block placement) engine. Pure Kotlin.
 *
 * 8x8 board, hand of three pieces. Placing a piece fills cells; every completely filled row or column
 * is cleared (rows and columns that complete at the same moment clear together).
 *
 * Completion rule per puzzle: clear [PiecePackConfig.targetLines] lines using at most
 * [PiecePackConfig.pieceBudget] pieces. The puzzle fails if no piece in hand fits, or the budget runs out.
 */
data class PiecePackConfig(val targetLines: Int, val pieceBudget: Int, val smallBias: Int)

class PieceShape(val id: Int, val name: String, val cells: IntArray /* r*10+c packed pairs */) {
    val rows: Int = cells.maxOf { it / 10 } + 1
    val cols: Int = cells.maxOf { it % 10 } + 1
    val size: Int get() = cells.size
}

object PiecePackEngine {
    const val N = 8

    private fun shape(id: Int, name: String, vararg rc: Pair<Int, Int>) =
        PieceShape(id, name, IntArray(rc.size) { rc[it].first * 10 + rc[it].second })

    private fun bar(id: Int, name: String, len: Int, horizontal: Boolean) =
        shape(id, name, *Array(len) { if (horizontal) 0 to it else it to 0 })

    val SHAPES: List<PieceShape> = listOf(
        shape(0, "dot", 0 to 0),
        bar(1, "pair-h", 2, true), bar(2, "pair-v", 2, false),
        bar(3, "bar3-h", 3, true), bar(4, "bar3-v", 3, false),
        bar(5, "bar4-h", 4, true), bar(6, "bar4-v", 4, false),
        bar(7, "bar5-h", 5, true), bar(8, "bar5-v", 5, false),
        shape(9, "square", 0 to 0, 0 to 1, 1 to 0, 1 to 1),
        shape(10, "corner-a", 0 to 0, 1 to 0, 1 to 1),
        shape(11, "corner-b", 0 to 1, 1 to 0, 1 to 1),
        shape(12, "corner-c", 0 to 0, 0 to 1, 1 to 0),
        shape(13, "corner-d", 0 to 0, 0 to 1, 1 to 1),
        shape(14, "rect-wide", 0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2),
        shape(15, "rect-tall", 0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1),
        shape(16, "hook-a", 0 to 0, 1 to 0, 2 to 0, 2 to 1),
        shape(17, "hook-b", 0 to 1, 1 to 1, 2 to 1, 2 to 0),
        shape(18, "hook-c", 0 to 0, 0 to 1, 1 to 0, 2 to 0),
        shape(19, "hook-d", 0 to 0, 0 to 1, 1 to 1, 2 to 1),
        shape(20, "tee", 0 to 0, 0 to 1, 0 to 2, 1 to 1),
        shape(21, "tee-down", 0 to 1, 1 to 0, 1 to 1, 1 to 2),
        shape(22, "zig", 0 to 0, 0 to 1, 1 to 1, 1 to 2),
        shape(23, "zag", 0 to 1, 0 to 2, 1 to 0, 1 to 1),
        shape(24, "big-corner", 0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2)
    )

    fun configFor(difficulty: String): PiecePackConfig = when (difficulty.lowercase()) {
        "easy" -> PiecePackConfig(targetLines = 4, pieceBudget = 27, smallBias = 3)
        "hard" -> PiecePackConfig(targetLines = 9, pieceBudget = 33, smallBias = 0)
        else -> PiecePackConfig(targetLines = 6, pieceBudget = 30, smallBias = 1)
    }

    /**
     * Maps a pointer position to the board origin (row, col) for dropping [p]. The piece is centred on x and
     * sits [lift] above y (so the finger does not hide it). The result is rounded to the nearest cell and
     * clamped so the whole piece stays on the board when the pointer is within one cell of an edge.
     */
    fun dropOrigin(x: Float, y: Float, p: PieceShape, left: Float, top: Float, cell: Float, lift: Float): IntArray {
        val c = Math.round((x - p.cols * cell / 2f - left) / cell)
        val r = Math.round((y - p.rows * cell - lift - top) / cell)
        return intArrayOf(r.coerceIn(0, N - p.rows), c.coerceIn(0, N - p.cols))
    }

    fun fits(board: IntArray, p: PieceShape, r: Int, c: Int): Boolean {
        if (r < 0 || c < 0 || r + p.rows > N || c + p.cols > N) return false
        for (cell in p.cells) if (board[(r + cell / 10) * N + (c + cell % 10)] != 0) return false
        return true
    }

    fun anyFit(board: IntArray, p: PieceShape): Boolean {
        for (r in 0..N - p.rows) for (c in 0..N - p.cols) if (fits(board, p, r, c)) return true
        return false
    }

    /** Places the piece and clears completed lines. Returns number of lines cleared. */
    fun place(board: IntArray, p: PieceShape, r: Int, c: Int): Int {
        for (cell in p.cells) board[(r + cell / 10) * N + (c + cell % 10)] = p.id + 1
        var rowMask = 0
        var colMask = 0
        for (i in 0 until N) {
            var rf = true; var cf = true
            for (j in 0 until N) {
                if (board[i * N + j] == 0) rf = false
                if (board[j * N + i] == 0) cf = false
            }
            if (rf) rowMask = rowMask or (1 shl i)
            if (cf) colMask = colMask or (1 shl i)
        }
        var lines = 0
        for (i in 0 until N) {
            if ((rowMask shr i) and 1 == 1) { lines++; for (j in 0 until N) board[i * N + j] = 0 }
            if ((colMask shr i) and 1 == 1) { lines++; for (j in 0 until N) board[j * N + i] = 0 }
        }
        return lines
    }

    fun dealPiece(rnd: Random, smallBias: Int): PieceShape {
        // weight small pieces on easier levels
        val weighted = ArrayList<PieceShape>(64)
        for (s in SHAPES) {
            var w = 2
            if (s.size <= 3) w += smallBias
            if (s.size >= 5 && smallBias >= 3) w = 1
            repeat(w) { weighted.add(s) }
        }
        return weighted[rnd.nextInt(weighted.size)]
    }

    fun deal(seed: Long, round: Int, board: IntArray, smallBias: Int): IntArray {
        var last = IntArray(3)
        for (attempt in 0 until 8) {
            val rnd = Random(seed * 7919L + round * 104729L + attempt * 31L)
            val ids = IntArray(3) { dealPiece(rnd, smallBias).id }
            last = ids
            if (ids.any { anyFit(board, SHAPES[it]) }) return ids
        }
        return last
    }

    /** Heuristic best move among hand slots (slot, row, col) or null. Used for hints and the bot test. */
    fun bestMove(board: IntArray, hand: IntArray): IntArray? {
        var best: IntArray? = null
        var bestScore = Int.MIN_VALUE
        val tmp = IntArray(N * N)
        for (slot in hand.indices) {
            val id = hand[slot]
            if (id < 0) continue
            val p = SHAPES[id]
            for (r in 0..N - p.rows) for (c in 0..N - p.cols) {
                if (!fits(board, p, r, c)) continue
                System.arraycopy(board, 0, tmp, 0, board.size)
                val lines = place(tmp, p, r, c)
                var s = lines * 1000
                // prefer compact boards: penalise isolated empty cells and filled count
                var filled = 0
                var isolated = 0
                for (i in 0 until N) for (j in 0 until N) {
                    if (tmp[i * N + j] != 0) { filled++; continue }
                    var free = 0
                    if (i > 0 && tmp[(i - 1) * N + j] == 0) free++
                    if (i < N - 1 && tmp[(i + 1) * N + j] == 0) free++
                    if (j > 0 && tmp[i * N + j - 1] == 0) free++
                    if (j < N - 1 && tmp[i * N + j + 1] == 0) free++
                    if (free == 0) isolated++
                }
                s -= isolated * 40
                s -= filled * 3
                // reward touching edges / existing blocks
                for (cell in p.cells) {
                    val rr = r + cell / 10; val cc = c + cell % 10
                    if (rr == 0 || rr == N - 1) s += 2
                    if (cc == 0 || cc == N - 1) s += 2
                }
                if (s > bestScore) { bestScore = s; best = intArrayOf(slot, r, c) }
            }
        }
        return best
    }
}

enum class PiecePackStatus { PLAYING, WON, STUCK, OUT_OF_PIECES }

class PiecePackState(val seed: Long, difficulty: String) {
    val config = PiecePackEngine.configFor(difficulty)
    var board = IntArray(PiecePackEngine.N * PiecePackEngine.N)
    var hand = IntArray(3) { -1 }
    var round = 0
    var piecesPlaced = 0
    var linesCleared = 0
    var score = 0
    var status = PiecePackStatus.PLAYING
        private set

    private class Snap(
        val board: IntArray, val hand: IntArray, val round: Int, val placed: Int,
        val lines: Int, val score: Int, val status: PiecePackStatus
    )

    private val undo = ArrayList<Snap>()

    init { hand = PiecePackEngine.deal(seed, 0, board, config.smallBias) }

    fun canUndo() = undo.isNotEmpty() && status != PiecePackStatus.WON

    fun piecesLeft() = config.pieceBudget - piecesPlaced

    fun tryPlace(slot: Int, r: Int, c: Int): Boolean {
        if (status != PiecePackStatus.PLAYING) return false
        val id = hand[slot]
        if (id < 0) return false
        val p = PiecePackEngine.SHAPES[id]
        if (!PiecePackEngine.fits(board, p, r, c)) return false
        undo.add(Snap(board.copyOf(), hand.copyOf(), round, piecesPlaced, linesCleared, score, status))
        val lines = PiecePackEngine.place(board, p, r, c)
        hand[slot] = -1
        piecesPlaced++
        linesCleared += lines
        score += p.size + lines * lines * 10
        if (hand.all { it < 0 }) {
            round++
            hand = PiecePackEngine.deal(seed, round, board, config.smallBias)
        }
        status = evaluate()
        return true
    }

    private fun evaluate(): PiecePackStatus {
        if (linesCleared >= config.targetLines) return PiecePackStatus.WON
        if (piecesPlaced >= config.pieceBudget) return PiecePackStatus.OUT_OF_PIECES
        val any = hand.any { it >= 0 && PiecePackEngine.anyFit(board, PiecePackEngine.SHAPES[it]) }
        return if (any) PiecePackStatus.PLAYING else PiecePackStatus.STUCK
    }

    fun undoMove(): Boolean {
        if (!canUndo()) return false
        val s = undo.removeAt(undo.size - 1)
        board = s.board; hand = s.hand; round = s.round; piecesPlaced = s.placed
        linesCleared = s.lines; score = s.score; status = s.status
        return true
    }

    fun restart() {
        board = IntArray(PiecePackEngine.N * PiecePackEngine.N)
        round = 0; piecesPlaced = 0; linesCleared = 0; score = 0
        hand = PiecePackEngine.deal(seed, 0, board, config.smallBias)
        undo.clear(); status = PiecePackStatus.PLAYING
    }

    fun hint(): IntArray? = PiecePackEngine.bestMove(board, hand)

    fun finalScore(): Int {
        val bonus = (piecesLeft().coerceAtLeast(0)) * 3
        return (50 + score / 2 + bonus).coerceAtLeast(10)
    }

    fun encode(): String {
        val sb = StringBuilder()
        for (v in board) sb.append(('a' + v))
        return "$round;$piecesPlaced;$linesCleared;$score;${hand.joinToString(",")};$sb"
    }

    fun restore(s: String) {
        try {
            val p = s.split(';')
            round = p[0].toInt(); piecesPlaced = p[1].toInt(); linesCleared = p[2].toInt(); score = p[3].toInt()
            hand = p[4].split(',').map { it.toInt() }.toIntArray()
            board = IntArray(PiecePackEngine.N * PiecePackEngine.N) { p[5][it] - 'a' }
            undo.clear()
            status = evaluate()
        } catch (_: Exception) {
            restart()
        }
    }
}
