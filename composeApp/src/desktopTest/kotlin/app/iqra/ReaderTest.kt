package app.iqra

import app.iqra.data.AyahRef
import app.iqra.data.JuzAmma
import app.iqra.data.QuranText
import app.iqra.engine.RepeatPlan
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderTest {
    private fun sequence(plan: RepeatPlan, max: Int = 100): List<Int> =
        generateSequence(plan.start()) { plan.next(it) }.take(max).map { it.ayah }.toList()

    @Test
    fun playsTheRangeOnce() = assertEquals(listOf(2, 3, 4), sequence(RepeatPlan(2, 4)))

    @Test
    fun repeatsEachAyahThenLoopsTheRange() =
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2, 2), sequence(RepeatPlan(1, 2, perAyah = 2, loops = 2)))

    @Test
    fun loopsForeverWhenAsked() {
        val plan = RepeatPlan(3, 3, perAyah = 1, loops = 0)
        assertEquals(List(100) { 3 }, sequence(plan))
    }

    @Test
    fun startingMidRangeContinuesToTheEnd() {
        val plan = RepeatPlan(1, 5)
        assertEquals(listOf(4, 5), generateSequence(plan.start(4)) { plan.next(it) }.map { it.ayah }.toList())
        assertNull(plan.next(RepeatPlan.Position(5)))
    }

    @Test
    fun readerHasEverySurahWithBasmalaWhereTanzilGivesIt() {
        val quran = QuranText.parse(File("src/commonMain/composeResources/files/quran_uthmani.xml").readText())
        for (n in JuzAmma.surahs) {
            assertTrue(JuzAmma.names.containsKey(n))
            assertTrue(quran.surah(n).isNotEmpty())
            // Al-Fātiḥah's basmala is its first ayah, so it has none above it.
            // (Tanzil writes it with an extra shadda on the first letter in two surahs; shown as is.)
            val basmala = quran.text(AyahRef(1, 1))
            val withShadda = basmala.take(1) + "\u0651" + basmala.drop(1)
            if (n == 1) assertNull(quran.bismillah(n)) else assertTrue(quran.bismillah(n) in setOf(basmala, withShadda), "surah $n")
        }
        assertNull(quran.bismillah(9))
    }
}
