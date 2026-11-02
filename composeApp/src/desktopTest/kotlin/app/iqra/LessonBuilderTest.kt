package app.iqra

import app.iqra.data.Course
import app.iqra.data.Letters
import app.iqra.data.LetterAudio
import app.iqra.data.QuranText
import app.iqra.data.Rules
import app.iqra.data.Harakat
import app.iqra.engine.Exercise
import app.iqra.engine.LessonBuilder
import app.iqra.engine.Skills
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
    fun lessonsWithAllLetterRecordingsAreValid() {
        val audio = LetterAudio(LetterAudio.allPaths.toSet())
        repeat(20) { seed ->
            val builder = LessonBuilder(quran, Random(seed), audio)
            for (lesson in Course.allLessons) for (e in builder.build(lesson)) if (e is Exercise.Choice) {
                assertTrue(e.correct in e.options.indices)
                assertEquals(e.options.size, e.options.map { it.label }.toSet().size, "${lesson.id}: duplicate options in $e")
                if (e.autoPlay) assertTrue(e.audio != null)
            }
        }
    }

    @Test
    fun practiceCoversEverySkill() {
        val skills = Letters.all.map(Skills::letter) + Harakat.all.map(Skills::vowel) + Rules.all.map(Skills::rule)
        repeat(20) { seed ->
            for (skill in skills) {
                val questions = LessonBuilder(quran, Random(seed)).practice(listOf(skill), questions = 4)
                assertEquals(4, questions.size, skill)
                for (q in questions) {
                    q as Exercise.Choice
                    assertEquals(skill, q.skill)
                    assertEquals(q.options.size, q.options.map { it.label }.toSet().size, "duplicate options in $q")
                }
            }
        }
    }

    @Test
    fun ruleQuestionsAboutQuranWordsAreRight() {
        val words = Course.wordSurahs.flatMap { quran.words(it) }
        for (rule in Rules.all) {
            val with = words.count { rule.inWord(it.text) }
            val without = words.count { rule.clearlyWithout(it.text) }
            assertTrue(with > 20 && without > 20, "${rule.key}: $with with, $without without")
            assertTrue(words.none { rule.inWord(it.text) && rule.clearlyWithout(it.text) }, rule.key)
        }
    }

    @Test
    fun everyLetterCanBeFoundInJuzAmma() {
        val words = Course.wordSurahs.flatMap { quran.words(it) }
        for (letter in Letters.all) assertTrue(words.any { Letters.appearsIn(letter, it.text) }, "No word with ${letter.name}")
    }
}
