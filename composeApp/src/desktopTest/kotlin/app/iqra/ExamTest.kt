package app.iqra

import app.iqra.data.Course
import app.iqra.data.Exams
import app.iqra.data.LetterAudio
import app.iqra.data.Letters
import app.iqra.data.QuranText
import app.iqra.engine.Exercise
import app.iqra.engine.LessonBuilder
import app.iqra.engine.Pcm
import app.iqra.engine.Progress
import app.iqra.engine.UNLIMITED_HEARTS_PRICE
import app.iqra.engine.VoiceMatch
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExamTest {
    private val resources = File("src/commonMain/composeResources")
    private val quran = QuranText.parse(File(resources, "files/quran_uthmani.xml").readText())
    private val audio = LetterAudio(LetterAudio.allPaths.filter { File(resources, it).exists() }.toSet())
    private val day = 20_000L

    private fun clip(letterKey: String): Pcm =
        decodeMp3(File(resources, "${LetterAudio.DIR}/$letterKey.mp3").readBytes())!!

    @Test
    fun everyStageEndsWithAnExam() {
        val examIds = Course.allLessons.filter { Exams.isExam(it.id) }.map { it.id }
        assertTrue(Exams.LETTERS in examIds && Exams.VOWELS in examIds && Exams.READING in examIds)
        assertEquals(Exams.hifzGroups.size, examIds.count { it.startsWith("exam-hifz-") })
        assertTrue(Exams.hifzGroups.isNotEmpty())
    }

    @Test
    fun everyUnitEndsWithExactlyOneCheckOrExam() {
        for (unit in Course.units + Course.hifzUnits) {
            val exams = unit.lessons.filter { Exams.isExam(it.id) }
            assertEquals(1, exams.size, "${unit.id} has ${exams.size} exams")
            assertEquals(exams.single(), unit.lessons.last(), "${unit.id}: the exam should be last")
        }
    }

    @Test
    fun examsMixQuizHardAndVoiceWithAtLeastOneVoice() {
        val examIds = Course.allLessons.filter { Exams.isExam(it.id) }.map { it.id }
        repeat(20) { seed ->
            for (withAudio in listOf(audio, LetterAudio.none)) {
                val builder = LessonBuilder(quran, Random(seed), withAudio)
                for (id in examIds) {
                    val exam = builder.exam(id)
                    val voices = exam.count { it is Exercise.Voice }
                    assertTrue(voices >= 1, "$id has no voice question")
                    if (Exams.isUnitCheck(id)) {
                        assertEquals(Exams.CHECK_SIZE, exam.size, "$id has ${exam.size} questions")
                        assertEquals(1, voices, "$id: expected 1 voice question")
                    } else {
                        assertEquals(Exams.STAGE_SIZE, exam.size, "$id has ${exam.size} questions")
                        assertEquals(3, voices, "$id: expected 3 voice questions")
                    }
                    for (e in exam) if (e is Exercise.Choice) assertTrue(e.correct in e.options.indices)
                    for (e in exam) if (e is Exercise.Voice) assertTrue(e.decoys.isNotEmpty() && e.target !in e.decoys)
                }
            }
        }
    }

    @Test
    fun coinsComeFromFirstPassesAndBuyUnlimitedHearts() {
        var p = Progress()
        p = p.passExam(Exams.check("u1"), 10, day)
        p = p.passExam(Exams.check("u1"), 10, day) // a retake earns nothing
        assertEquals(Exams.CHECK_COINS, p.coins)
        p = p.passExam(Exams.check("u2"), 10, day)
        assertFalse(p.canBuyUnlimitedHearts(day))
        p = p.passExam(Exams.LETTERS, 20, day)
        assertEquals(2 * Exams.CHECK_COINS + Exams.STAGE_COINS, p.coins)
        assertEquals(UNLIMITED_HEARTS_PRICE, p.coins)
        assertTrue(p.canBuyUnlimitedHearts(day))
        p = p.buyUnlimitedHearts(day)
        assertEquals(0, p.coins)
        assertTrue(p.ownsUnlimitedHearts(day))
        assertFalse(p.canBuyUnlimitedHearts(day))
    }

    @Test
    fun unlimitedHeartsEndWhenTheStreakBreaks() {
        var p = Progress(coins = UNLIMITED_HEARTS_PRICE).buyUnlimitedHearts(day)
        p = p.completeLesson("u1-learn", 10, day + 1)
        assertTrue(p.ownsUnlimitedHearts(day + 2), "practised yesterday: still unlimited")
        assertFalse(p.ownsUnlimitedHearts(day + 3), "a whole day missed: unlimited hearts are gone")
        p = p.expireUnlimitedHearts(day + 3)
        assertFalse(p.unlimitedHearts)
        assertFalse(p.ownsUnlimitedHearts(day + 3))
    }

    @Test
    fun aRecordingMatchesItselfOverOtherLetters() {
        val ba = clip("ba")
        val others = listOf("sin", "mim", "qaf").map(::clip)
        val result = VoiceMatch.check(ba, ba, others)
        assertIs<VoiceMatch.Result.Scored>(result)
        assertTrue(result.passed && result.score > 90, "score ${result.score}")
    }

    @Test
    fun aSlowerNoisierSayingStillMatches() {
        // Stand-in for a learner: the same word 20% slower, quieter, with background noise.
        val sin = clip("sin")
        val rnd = Random(1)
        val slow = FloatArray((sin.samples.size * 1.2).toInt()) { i ->
            sin.samples[minOf((i / 1.2).toInt(), sin.samples.size - 1)] * 0.6f + (rnd.nextFloat() - 0.5f) * 0.01f
        }
        val user = Pcm(slow, sin.rate)
        for (wrong in listOf("ba", "mim", "qaf", "lam", "ayn")) {
            val result = VoiceMatch.check(user, sin, listOf(clip(wrong)))
            assertIs<VoiceMatch.Result.Scored>(result)
            assertTrue(result.passed, "slow sin matched $wrong better")
        }
    }

    @Test
    fun theWrongLetterDoesNotPass() {
        val result = VoiceMatch.check(clip("mim"), clip("qaf"), listOf(clip("mim"), clip("ba")))
        assertIs<VoiceMatch.Result.Scored>(result)
        assertFalse(result.passed)
    }

    @Test
    fun silenceIsNotAnAnswer() {
        val quiet = Pcm(FloatArray(16_000) { 0.0005f * (it % 7 - 3) }, 16_000)
        assertEquals(VoiceMatch.Result.NoVoice, VoiceMatch.check(quiet, clip("ba"), listOf(clip("mim"))))
    }

    @Test
    fun everyLetterRecordingIsRecognisedAgainstDecoys() {
        val keys = Letters.all.map { it.key }.filter { File(resources, "${LetterAudio.DIR}/$it.mp3").exists() }
        val clips = keys.associateWith(::clip)
        var wrong = 0
        for (k in keys) {
            val decoys = (keys - k).shuffled(Random(k.hashCode())).take(3).map { clips.getValue(it) }
            val r = VoiceMatch.check(clips.getValue(k), clips.getValue(k), decoys)
            if (r !is VoiceMatch.Result.Scored || !r.passed) wrong++
        }
        assertEquals(0, wrong)
    }
}
