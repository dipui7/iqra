package app.iqra

import app.iqra.data.Course
import app.iqra.engine.Badges
import app.iqra.engine.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgesTest {
    @Test
    fun aNewLearnerHasNoBadges() = assertTrue(Badges.earned(Progress()).isEmpty())

    @Test
    fun badgesFollowProgress() {
        val p = Progress(completed = setOf("u1-learn", "h112-recall", "h1-recall"), xp = 120, bestStreak = 4)
        assertEquals(setOf("first-lesson", "streak-3", "xp-100", "fatiha", "surah-1"), Badges.earned(p))
        assertEquals(2 to 5, Badges.byId("surah-5")!!.progress(p))
    }

    @Test
    fun everyLessonDoneEarnsTheCourseBadges() {
        val p = Progress(completed = Course.allLessons.map { it.id }.toSet())
        for (id in listOf("letters-met", "vowels", "rules", "reader", "fatiha", "surah-all")) assertTrue(id in Badges.earned(p), id)
        assertEquals(Badges.all.size, Badges.all.map { it.id }.toSet().size)
    }
}
