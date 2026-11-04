package app.iqra

import app.iqra.data.Course
import app.iqra.data.Exams
import app.iqra.data.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextExamTest {
    @Test
    fun theNextExamComesFromTheTabBeingShown() {
        // Nothing done yet: the reading tab suggests Unit 1's check, even though it is still locked.
        val (unit, exam) = Course.nextExam(Track.Reading, emptySet())!!
        assertEquals("u1", unit.id)
        assertEquals(Exams.check("u1"), exam.id)
        assertEquals(Track.Hifz, Course.nextExam(Track.Hifz, emptySet())!!.first.track)
    }

    @Test
    fun passedExamsAreSkipped() {
        val passed = setOf(Exams.check("u1"), Exams.check("u2"))
        assertEquals(Exams.check("u3"), Course.nextExam(Track.Reading, passed)!!.second.id)
    }

    @Test
    fun noneLeftWhenEveryExamIsPassed() {
        val all = Course.units.flatMap { it.lessons }.filter { Exams.isExam(it.id) }.map { it.id }.toSet()
        assertNull(Course.nextExam(Track.Reading, all))
    }
}
