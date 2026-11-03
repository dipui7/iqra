package app.iqra

import app.iqra.data.AyahRef
import app.iqra.data.QuranText
import app.iqra.data.TajweedGroup
import app.iqra.data.TajweedMarks
import app.iqra.data.Translation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TajweedTest {
    private val res = File("src/commonMain/composeResources/files")
    private val quran = QuranText.parse(File(res, "quran_uthmani.xml").readText())
    private val tajweed = TajweedMarks.parse(File(res, "tajweed.txt").readText())

    /** The letters each rule is about; a mark that misses them was mapped onto the wrong place. */
    private fun onRightLetters(rule: String, span: String): Boolean = when {
        rule == "hamzat_wasl" -> 'ٱ' in span
        rule == "lam_shamsiyyah" -> 'ل' in span
        rule == "qalqalah" -> span.any { it in "قطبجد" }
        rule == "ghunnah" -> span.any { it in "نم" }
        rule == "ikhfa_shafawi" || rule == "idghaam_shafawi" -> 'م' in span
        rule == "iqlab" -> span.any { it in "نًࣰٌࣱٍࣲۭۢ" }
        rule.startsWith("madd") -> span.any { it in "اويىٰۦۥٰٓۦۥ" }
        else -> span.isNotBlank()
    }

    @Test
    fun everyMarkSitsOnTheRightLettersOfOurText() {
        var total = 0
        for (s in 1..114) for ((ref, text) in quran.surah(s)) {
            for (m in tajweed.of(ref)) {
                total++
                assertTrue(m.start in 0 until m.end && m.end <= text.length, "$ref: mark ${m.rule} out of bounds")
                assertTrue(onRightLetters(m.rule, text.substring(m.start, m.end)), "$ref: ${m.rule} at ${m.start}..${m.end}")
            }
        }
        assertTrue(total > 59_000, "only $total marks")
    }

    @Test
    fun everyRuleGroupIsUsed() {
        val groups = (1..114).flatMap { s -> quran.surah(s).flatMap { (ref, _) -> tajweed.of(ref) } }.map { it.group }.toSet()
        assertEquals(TajweedGroup.entries.toSet(), groups)
    }

    @Test
    fun translationIsCompleteAndVerbatim() {
        val raw = File(res, "translation_en_sahih.txt").readText()
        val t = Translation.parse("Saheeh International", raw)
        assertEquals("Say, \"He is Allah, [who is] One,", t.of(AyahRef(112, 1)))
        // The file keeps Tanzil's header naming the translator and source.
        assertTrue("Translator: Saheeh International" in raw && "Source: Tanzil.net" in raw)
    }
}
