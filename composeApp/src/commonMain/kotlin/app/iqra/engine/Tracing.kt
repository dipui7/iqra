package app.iqra.engine

/**
 * Scores a traced letter on a coarse grid, so it is forgiving of shaky fingers:
 *
 * - [TraceScore.onLetter]: how much of what was drawn lies on (or right next to) the letter;
 * - [TraceScore.covered]: how much of the letter the drawing passed over (or right next to).
 *
 * A tracing passes when both are high enough. Scribbling over everything fails the first,
 * drawing one short line fails the second.
 */
object Tracing {
    const val GRID = 24
    const val MIN_ON_LETTER = 0.7f
    const val MIN_COVERED = 0.65f

    /**
     * [letter] is GRID×GRID cells (row-major), true where the letter's ink is. [strokes] are the
     * drawn lines with points in grid units (0..GRID).
     */
    fun score(letter: BooleanArray, strokes: List<List<Pair<Float, Float>>>): TraceScore {
        require(letter.size == GRID * GRID)
        val drawn = BooleanArray(GRID * GRID)
        for (stroke in strokes) {
            if (stroke.size == 1) mark(drawn, stroke[0].first, stroke[0].second)
            stroke.zipWithNext { (x0, y0), (x1, y1) ->
                // Sample each segment finely enough not to skip cells.
                val steps = maxOf(1, (maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)) * 4).toInt())
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    mark(drawn, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
                }
            }
        }
        val drawnCount = drawn.count { it }
        val letterCount = letter.count { it }
        if (drawnCount == 0 || letterCount == 0) return TraceScore(0f, 0f)
        val nearLetter = dilate(letter)
        val nearDrawn = dilate(drawn)
        val onLetter = drawn.indices.count { drawn[it] && nearLetter[it] }.toFloat() / drawnCount
        val covered = letter.indices.count { letter[it] && nearDrawn[it] }.toFloat() / letterCount
        return TraceScore(onLetter, covered)
    }

    private fun mark(cells: BooleanArray, x: Float, y: Float) {
        val cx = x.toInt().coerceIn(0, GRID - 1)
        val cy = y.toInt().coerceIn(0, GRID - 1)
        cells[cy * GRID + cx] = true
    }

    /** Each true cell also marks its 8 neighbours. */
    fun dilate(cells: BooleanArray): BooleanArray {
        val out = BooleanArray(cells.size)
        for (y in 0 until GRID) for (x in 0 until GRID) if (cells[y * GRID + x]) {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until GRID && ny in 0 until GRID) out[ny * GRID + nx] = true
            }
        }
        return out
    }
}

data class TraceScore(val onLetter: Float, val covered: Float) {
    val passed: Boolean get() = onLetter >= Tracing.MIN_ON_LETTER && covered >= Tracing.MIN_COVERED

    /** A hint for a failed attempt. */
    val advice: String get() = when {
        onLetter < Tracing.MIN_ON_LETTER -> "Stay on the grey letter — some of your lines went off it."
        else -> "Trace the whole letter, all the way along (dots too)."
    }
}
