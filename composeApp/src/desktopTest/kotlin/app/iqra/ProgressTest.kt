package app.iqra

import app.iqra.engine.MAX_HEARTS
import app.iqra.engine.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressTest {
    private val day = 20_000L

    @Test
    fun rightAnswersSpaceReviewsOutWrongOnesBringThemBack() {
        var p = Progress()
        p = p.recordAnswer("letter:ba", true, day)
        assertEquals(1, p.skills["letter:ba"]!!.box)
        assertEquals(day + 1, p.skills["letter:ba"]!!.due)
        p = p.recordAnswer("letter:ba", true, day + 1)
        assertEquals(day + 3, p.skills["letter:ba"]!!.due)
        p = p.recordAnswer("letter:ba", false, day + 3)
        assertEquals(0, p.skills["letter:ba"]!!.box)
        assertEquals(day + 3, p.skills["letter:ba"]!!.due)
    }

    @Test
    fun practisesDueSkillsFirstWeakestFirst() {
        var p = Progress()
        p = p.recordAnswer("letter:ba", true, day) // due tomorrow
        p = p.recordAnswer("letter:ta", false, day) // due today, box 0
        p = p.recordAnswer("letter:tha", false, day)
        p = p.recordAnswer("letter:tha", false, day) // more mistakes than ta
        assertEquals(listOf("letter:tha", "letter:ta", "letter:ba"), p.skillsToPractise(day))
        assertEquals(2, p.dueCount(day))
        // Tomorrow ba is due too.
        assertEquals(3, p.dueCount(day + 1))
    }

    @Test
    fun practiceGivesBackAHeartAndCountsForTheDay() {
        val p = Progress(hearts = 0, heartTimerStart = 1000).completePractice(10, day)
        assertEquals(1, p.hearts)
        assertEquals(1000, p.heartTimerStart)
        assertEquals(10, p.xpOn(day))
        assertEquals(1, p.streak)
        assertEquals(MAX_HEARTS, Progress().completePractice(10, day).hearts)
    }

    @Test
    fun dailyXpResetsEachDay() {
        var p = Progress().completeLesson("u1-learn", 15, day)
        p = p.completeLesson("u1-practice", 10, day)
        assertEquals(25, p.xpOn(day))
        assertEquals(0, p.xpOn(day + 1))
        p = p.completeLesson("u1-quran", 10, day + 1)
        assertEquals(10, p.xpOn(day + 1))
        assertEquals(35, p.xp)
        assertEquals(2, p.streak)
        assertTrue("u1-quran" in p.completed)
    }
}
