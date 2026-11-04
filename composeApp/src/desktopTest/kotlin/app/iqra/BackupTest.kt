package app.iqra

import app.iqra.engine.Backup
import app.iqra.engine.Progress
import app.iqra.engine.SkillState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupTest {
    private val progress = Progress(
        completed = setOf("u1-learn", "h112-recall"), xp = 140, streak = 4, bestStreak = 9, lastActiveDay = 20_400,
        skills = mapOf("letter:ba" to SkillState(box = 3, due = 20_404, right = 5, wrong = 1)), coins = 2, reminderHour = 18,
    )

    @Test
    fun roundTrips() = assertEquals(progress, Backup.import(Backup.export(progress)))

    @Test
    fun survivesLineBreaksAndSpacesFromChatApps() {
        val code = Backup.export(progress).chunked(30).joinToString("\n ")
        assertEquals(progress, Backup.import("  $code \n"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(Backup.import(""))
        assertNull(Backup.import("hello"))
        assertNull(Backup.import("ALIFUP1:not-base64!!"))
        assertNull(Backup.import(Backup.export(progress).dropLast(10)))
    }
}
