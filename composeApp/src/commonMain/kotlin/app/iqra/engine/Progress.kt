package app.iqra.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.iqra.nowMillis
import app.iqra.readProgress
import app.iqra.todayEpochDay
import app.iqra.writeProgress
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MAX_HEARTS = 5
const val HEART_REFILL_MS = 20 * 60 * 1000L
const val LESSON_XP = 10
const val PERFECT_BONUS_XP = 5

@Serializable
data class Progress(
    val completed: Set<String> = emptySet(),
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Day (days since 1970-01-01, local time) of the last completed lesson. */
    val lastActiveDay: Long = -1,
    val hearts: Int = MAX_HEARTS,
    /** When the refill timer for the next heart started; only meaningful below MAX_HEARTS. */
    val heartTimerStart: Long = 0,
) {
    /** The streak still counts if the learner practised today or yesterday. */
    fun currentStreak(today: Long): Int = if (lastActiveDay >= today - 1) streak else 0

    fun withRefilledHearts(now: Long): Progress {
        if (hearts >= MAX_HEARTS) return this
        val gained = ((now - heartTimerStart) / HEART_REFILL_MS).toInt()
        if (gained <= 0) return this
        val newHearts = minOf(MAX_HEARTS, hearts + gained)
        return copy(hearts = newHearts, heartTimerStart = heartTimerStart + gained * HEART_REFILL_MS)
    }

    fun msUntilNextHeart(now: Long): Long? =
        if (hearts >= MAX_HEARTS) null else (heartTimerStart + HEART_REFILL_MS - now).coerceAtLeast(0)

    fun loseHeart(now: Long): Progress = copy(
        hearts = (hearts - 1).coerceAtLeast(0),
        heartTimerStart = if (hearts >= MAX_HEARTS) now else heartTimerStart,
    )

    fun completeLesson(lessonId: String, xpGained: Int, today: Long): Progress {
        val newStreak = when (lastActiveDay) {
            today -> streak
            today - 1 -> streak + 1
            else -> 1
        }
        return copy(
            completed = completed + lessonId,
            xp = xp + xpGained,
            streak = newStreak,
            bestStreak = maxOf(bestStreak, newStreak),
            lastActiveDay = today,
        )
    }
}

/** Holds the learner's progress as Compose state and saves every change to local storage. */
class ProgressStore {
    private val json = Json { ignoreUnknownKeys = true }

    var progress: Progress by mutableStateOf(load())
        private set

    private fun load(): Progress = try {
        readProgress()?.let { json.decodeFromString<Progress>(it) } ?: Progress()
    } catch (e: Exception) {
        Progress()
    }.withRefilledHearts(nowMillis())

    fun update(change: (Progress) -> Progress) {
        progress = change(progress)
        writeProgress(json.encodeToString(Progress.serializer(), progress))
    }

    fun refreshHearts() = update { it.withRefilledHearts(nowMillis()) }

    fun loseHeart() = update { it.loseHeart(nowMillis()) }

    fun completeLesson(lessonId: String, xpGained: Int) =
        update { it.completeLesson(lessonId, xpGained, todayEpochDay()) }
}
