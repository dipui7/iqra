package app.iqra

import app.iqra.engine.Tracing
import app.iqra.engine.Tracing.GRID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracingTest {
    /** An alif-like letter: a vertical bar, 2 cells wide, rows 3..20. */
    private val alif = BooleanArray(GRID * GRID) { i -> (i % GRID) in 11..12 && (i / GRID) in 3..20 }

    /** A ba-like letter: a horizontal bowl plus a dot below. */
    private val ba = BooleanArray(GRID * GRID) { i ->
        val x = i % GRID; val y = i / GRID
        (y in 12..13 && x in 4..19) || (x in 4..5 && y in 9..12) || (x in 18..19 && y in 9..12) || (x in 11..12 && y in 17..18)
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(x0 to y0, x1 to y1)

    @Test
    fun aCarefulTracePasses() {
        assertTrue(Tracing.score(alif, listOf(line(12f, 3.5f, 12.2f, 20.5f))).passed)
        // A little wobble is fine.
        assertTrue(Tracing.score(alif, listOf(listOf(11.5f to 3f, 13f to 8f, 11f to 14f, 12.5f to 20.5f))).passed)
    }

    @Test
    fun aBowlWithItsDotPasses() {
        val bowl = listOf(4.5f to 9f, 4.5f to 12.5f, 19f to 12.5f, 19f to 9f)
        val dot = listOf(11.8f to 17.6f)
        assertTrue(Tracing.score(ba, listOf(bowl, dot)).passed)
    }

    @Test
    fun scribblingEverywhereFails() {
        val scribble = (0 until GRID step 2).map { y -> line(0f, y.toFloat(), GRID - 0.01f, y.toFloat()) }
        val s = Tracing.score(alif, scribble)
        assertTrue(s.covered > 0.9f)
        assertFalse(s.passed)
    }

    @Test
    fun aShortDashFails() = assertFalse(Tracing.score(alif, listOf(line(12f, 10f, 12f, 12f))).passed)

    @Test
    fun aLineBesideTheLetterFails() = assertFalse(Tracing.score(alif, listOf(line(18f, 3f, 18f, 20f))).passed)

    @Test
    fun nothingDrawnFails() = assertFalse(Tracing.score(alif, emptyList()).passed)
}
