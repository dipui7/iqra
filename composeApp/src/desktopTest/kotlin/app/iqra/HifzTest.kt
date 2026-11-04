package app.iqra

import app.iqra.data.Course
import app.iqra.data.Hifz
import app.iqra.data.QuranText
import app.iqra.data.Sound
import app.iqra.engine.Exercise
import app.iqra.engine.HifzBuilder
import app.iqra.engine.LessonBuilder
import app.iqra.engine.Skills
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HifzTest {
    private val quran = QuranText.parse(File("src/commonMain/composeResources/files/quran_uthmani.xml").readText())

    @Test
    fun ayahCountsMatchTheText() {
        for (s in Hifz.surahs) assertEquals(Hifz.ayahCount[s.number], quran.surah(s.number).size, "surah ${s.number}")
        assertEquals(Hifz.surahs.size, Course.hifzUnits.size)
    }

    @Test
    fun everyQuestionIsVerbatimAndUnambiguous() {
        repeat(30) { seed ->
            val b = HifzBuilder(quran, Random(seed))
            for (s in Hifz.surahs) {
                val ayat = quran.surah(s.number)
                val texts = ayat.map { it.second }
                for (e in b.learn(s.number) + b.recall(s.number)) when (e) {
                    is Exercise.AyahIntro -> assertEquals(quran.text(e.ref), e.text)
                    is Exercise.Order -> {
                        assertTrue(e.items.size >= 3)
                        // A run of consecutive ayat, verbatim.
                        val start = texts.indexOf(e.items.first())
                        assertEquals(texts.subList(start, start + e.items.size), e.items)
                    }
                    is Exercise.Choice -> {
                        assertEquals(e.options.size, e.options.map { it.label }.toSet().size, "duplicate options: $e")
                        assertEquals(Skills.surah(s.number), e.skill)
                        val answer = e.options[e.correct].label
                        val ref = (e.audio as Sound.Ayah).ref
                        when (e.prompt) {
                            "What comes next?" -> {
                                assertEquals(quran.text(ref), e.big)
                                assertEquals(1, texts.count { it == e.big }, "ambiguous prompt in ${s.number}")
                                assertEquals(quran.text(ref.copy(aya = ref.aya + 1)), answer)
                            }
                            "Which word comes next?" -> {
                                val ayah = quran.text(ref)
                                assertTrue(ayah.startsWith(e.big + " " + answer), "not a verbatim prefix: ${e.big} / $answer")
                                // No other ayah starts the same way but continues differently.
                                for (t in texts) if (t.startsWith(e.big + " ")) assertTrue(t.startsWith(e.big + " " + answer + " ") || t == e.big + " " + answer)
                            }
                            "Listen. Which ayah is this?" -> assertEquals(quran.text(ref), answer)
                            else -> error("unexpected prompt ${e.prompt}")
                        }
                    }
                    else -> error("unexpected exercise $e")
                }
            }
        }
    }

    @Test
    fun surahsCanBePractised() {
        repeat(10) { seed ->
            for (s in Hifz.surahs) {
                val qs = LessonBuilder(quran, Random(seed)).practice(listOf(Skills.surah(s.number)), questions = 3)
                assertEquals(3, qs.size, "surah ${s.number}")
            }
        }
    }
}
