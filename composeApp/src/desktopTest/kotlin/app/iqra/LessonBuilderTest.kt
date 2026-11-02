package app.iqra

import app.iqra.data.Course
import app.iqra.data.Letters
import app.iqra.data.QuranText
import app.iqra.engine.Exercise
import app.iqra.engine.LessonBuilder
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LessonBuilderTest {
    private val file = File("src/commonMain/composeResources/files/quran_uthmani.xml")
    private val xml = file.readText()
    private val quran = QuranText.parse(xml)

    @Test
    fun quranTextIsVerbatimTanzil() {
        // Fingerprint of the file as downloaded from tanzil.net; any edit to it fails this test.
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        assertEquals("c5052534d63d3856ce25413ff464d0b609f90169a202a1615aa8707efec81244", sha.joinToString("") { "%02x".format(it) })
        assertTrue(xml.contains("PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK"))
        // Every word we show must appear in its ayah exactly as written.
        for (sura in Course.wordSurahs) for (w in quran.words(sura)) assertTrue(w.text in quran.text(w.ref))
    }

    @Test
    fun everyLessonBuildsValidQuestions() {
        repeat(50) { seed ->
            val builder = LessonBuilder(quran, Random(seed))
            for (lesson in Course.allLessons) {
                val exercises = builder.build(lesson)
                assertTrue(exercises.size >= 5, "${lesson.id} has only ${exercises.size} exercises")
                for (e in exercises) when (e) {
                    is Exercise.Choice -> {
                        assertTrue(e.correct in e.options.indices, "${lesson.id}: bad answer index")
                        assertEquals(e.options.size, e.options.map { it.label }.toSet().size, "${lesson.id}: duplicate options in $e")
                    }
                    is Exercise.Match -> assertEquals(e.pairs.size, e.pairs.map { it.second }.toSet().size)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun everyLetterCanBeFoundInJuzAmma() {
        val words = Course.wordSurahs.flatMap { quran.words(it) }
        for (letter in Letters.all) assertTrue(words.any { Letters.appearsIn(letter, it.text) }, "No word with ${letter.name}")
    }
}
