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

/**
 * While the app is in development hearts never run out. Set to false before release, so
 * learners have [MAX_HEARTS] hearts again.
 */
const val DEV_UNLIMITED_HEARTS = true

/** Coins needed to unlock unlimited hearts; one coin is earned for each exam passed. */
const val UNLIMITED_HEARTS_PRICE = 3
const val HEART_REFILL_MS = 20 * 60 * 1000L
const val LESSON_XP = 10
const val PERFECT_BONUS_XP = 5
const val PRACTICE_XP = 10
val DAILY_GOALS = listOf(10, 20, 30, 50)

/** Days until a skill comes up for review again, by its box: right answers move it up a box. */
private val REVIEW_INTERVAL_DAYS = listOf(0, 1, 2, 4, 8, 16)

/** Review schedule of one skill (see [Skills]), Leitner style. */
@Serializable
data class SkillState(
    val box: Int = 0,
    /** Day it is next due for review. */
    val due: Long = 0,
    val right: Int = 0,
    val wrong: Int = 0,
) {
    fun answered(correct: Boolean, today: Long): SkillState = if (correct) {
        val b = minOf(box + 1, REVIEW_INTERVAL_DAYS.lastIndex)
        copy(box = b, due = today + REVIEW_INTERVAL_DAYS[b], right = right + 1)
    } else {
        copy(box = 0, due = today, wrong = wrong + 1)
    }
}

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
    val skills: Map<String, SkillState> = emptyMap(),
    /** XP the learner aims for each day. */
    val dailyGoal: Int = 20,
    /** XP earned on [xpDay]. */
    val xpToday: Int = 0,
    val xpDay: Long = -1,
    /** Earned by passing an exam for the first time. */
    val coins: Int = 0,
    val passedExams: Set<String> = emptySet(),
    /** Unlimited hearts bought with coins; lost again when the streak breaks. */
    val unlimitedHearts: Boolean = false,
    /** Day [unlimitedHearts] was bought, so it lasts through that day even before a lesson. */
    val unlimitedSince: Long = -1,
) {
    /** Bought unlimited hearts last only while the streak is unbroken. */
    fun ownsUnlimitedHearts(today: Long): Boolean =
        unlimitedHearts && maxOf(lastActiveDay, unlimitedSince) >= today - 1

    fun hasUnlimitedHearts(today: Long): Boolean = DEV_UNLIMITED_HEARTS || ownsUnlimitedHearts(today)

    /** A missed day ends bought unlimited hearts; they must be bought again with coins. */
    fun expireUnlimitedHearts(today: Long): Progress =
        if (unlimitedHearts && !ownsUnlimitedHearts(today)) copy(unlimitedHearts = false) else this

    fun canBuyUnlimitedHearts(today: Long): Boolean =
        coins >= UNLIMITED_HEARTS_PRICE && !ownsUnlimitedHearts(today)

    fun buyUnlimitedHearts(today: Long): Progress =
        if (!canBuyUnlimitedHearts(today)) this
        else copy(coins = coins - UNLIMITED_HEARTS_PRICE, unlimitedHearts = true, unlimitedSince = today, hearts = MAX_HEARTS)

    /** A passed exam counts as a completed lesson; only the first pass earns a coin. */
    fun passExam(examId: String, xpGained: Int, today: Long): Progress {
        val first = examId !in passedExams
        return completeLesson(examId, xpGained, today)
            .copy(passedExams = passedExams + examId, coins = coins + if (first) 1 else 0)
    }

    fun xpOn(today: Long): Int = if (xpDay == today) xpToday else 0

    fun recordAnswer(skill: String, correct: Boolean, today: Long): Progress =
        copy(skills = skills + (skill to (skills[skill] ?: SkillState()).answered(correct, today)))

    /**
     * Skills to practise, most urgent first: those due today (lowest box, most mistakes first),
     * then if there are few, the weakest of the rest.
     */
    fun skillsToPractise(today: Long, max: Int = 6): List<String> {
        val urgency = compareBy<Map.Entry<String, SkillState>>({ it.value.box }, { -it.value.wrong }, { it.value.due })
        val due = skills.entries.filter { it.value.due <= today }.sortedWith(urgency)
        val rest = skills.entries.filter { it.value.due > today }.sortedWith(urgency)
        return (due + rest.take(maxOf(0, 3 - due.size))).take(max).map { it.key }
    }

    fun dueCount(today: Long): Int = skills.values.count { it.due <= today }

    private fun earnXp(xpGained: Int, today: Long): Progress = copy(
        xp = xp + xpGained,
        xpToday = xpOn(today) + xpGained,
        xpDay = today,
    )

    private fun countDay(today: Long): Progress {
        val newStreak = when (lastActiveDay) {
            today -> streak
            today - 1 -> streak + 1
            else -> 1
        }
        return copy(streak = newStreak, bestStreak = maxOf(bestStreak, newStreak), lastActiveDay = today)
    }

    /** A finished practice session: XP, the day counts for the streak, and one heart back. */
    fun completePractice(xpGained: Int, today: Long): Progress {
        val p = earnXp(xpGained, today).countDay(today)
        // The refill timer keeps running for any hearts still missing.
        return if (p.hearts >= MAX_HEARTS) p else p.copy(hearts = p.hearts + 1)
    }

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

    fun completeLesson(lessonId: String, xpGained: Int, today: Long): Progress =
        earnXp(xpGained, today).countDay(today).copy(completed = completed + lessonId)
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
    }.withRefilledHearts(nowMillis()).expireUnlimitedHearts(todayEpochDay())

    fun update(change: (Progress) -> Progress) {
        progress = change(progress)
        writeProgress(json.encodeToString(Progress.serializer(), progress))
    }

    fun refreshHearts() = update { it.withRefilledHearts(nowMillis()).expireUnlimitedHearts(todayEpochDay()) }

    fun loseHeart() {
        if (!progress.hasUnlimitedHearts(todayEpochDay())) update { it.loseHeart(nowMillis()) }
    }

    fun passExam(examId: String, xpGained: Int) = update { it.passExam(examId, xpGained, todayEpochDay()) }

    fun buyUnlimitedHearts() = update { it.buyUnlimitedHearts(todayEpochDay()) }

    fun completeLesson(lessonId: String, xpGained: Int) =
        update { it.completeLesson(lessonId, xpGained, todayEpochDay()) }

    fun completePractice(xpGained: Int) = update { it.completePractice(xpGained, todayEpochDay()) }

    fun recordAnswer(skill: String, correct: Boolean) = update { it.recordAnswer(skill, correct, todayEpochDay()) }

    fun setDailyGoal(xp: Int) = update { it.copy(dailyGoal = xp) }

    /** Start over: everything is forgotten except the daily goal. */
    fun reset() = update { Progress(dailyGoal = it.dailyGoal) }
}
