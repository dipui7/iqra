package app.iqra.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Feedback {
    data object Correct : Feedback
    /** [answer] is the right option, or null when the question shows the answer itself (ordering). */
    data class Wrong(val answer: Option?) : Feedback
}

/**
 * The state of one lesson in progress. Questions answered wrongly cost a heart and are
 * asked again at the end, so a lesson is only finished once every question is answered right.
 */
class LessonSession(
    exercises: List<Exercise>,
    startHearts: Int,
    private val onMistake: () -> Unit,
    /** Called with the question's skill for every graded answer, to update the review schedule. */
    private val onAnswer: (skill: String?, right: Boolean) -> Unit = { _, _ -> },
    /** In practice sessions mistakes cost no hearts, so they can be done with none left. */
    private val heartsMatter: Boolean = true,
    /** An exam: no hearts and no second tries; every question counts once toward the score. */
    val exam: Boolean = false,
) {
    private val queue = mutableStateListOf<Exercise>().apply { addAll(exercises) }
    private val total = exercises.size

    var index by mutableIntStateOf(0)
        private set
    var hearts by mutableIntStateOf(startHearts)
        private set
    var mistakes by mutableIntStateOf(0)
        private set
    private var solved by mutableIntStateOf(0)
    private var graded by mutableIntStateOf(0)
    /** Questions answered right (in an exam, each is asked once). */
    var rightCount by mutableIntStateOf(0)
        private set
    val gradedCount: Int get() = graded

    var selected by mutableStateOf<Int?>(null)
    /** The learner's order for an [Exercise.Order], once every item is placed. */
    var orderAnswer by mutableStateOf<List<String>?>(null)
    var feedback by mutableStateOf<Feedback?>(null)
        private set

    val current: Exercise? get() = queue.getOrNull(index)
    val progress: Float get() = (if (exam) graded else solved).toFloat() / total
    val finished: Boolean get() = index >= queue.size
    val outOfHearts: Boolean get() = heartsMatter && hearts <= 0
    val usesHearts: Boolean get() = heartsMatter
    val questionCount: Int get() = total

    /** Grade the current multiple-choice or ordering question. */
    fun check() {
        val order = current as? Exercise.Order
        if (order != null) {
            val answer = orderAnswer ?: return
            // Compared as texts, so identical ayat (Al-Kāfirūn 3 and 5) can go either way round.
            grade(order, answer == order.items, order.skill, null)
            return
        }
        val choice = current as? Exercise.Choice ?: return
        val pick = selected ?: return
        grade(choice, pick == choice.correct, choice.skill, choice.options[choice.correct])
    }

    /** A voice question was checked: [right] if the recording sounded closest to the target. */
    fun gradeVoice(voice: Exercise.Voice, right: Boolean) = grade(voice, right, voice.skill, null)

    private fun grade(exercise: Exercise, right: Boolean, skill: String?, answer: Option?) {
        onAnswer(skill, right)
        graded++
        if (right) rightCount++
        if (exam) {
            // No second tries in an exam.
            if (right) solved++ else mistakes++
            feedback = if (right) Feedback.Correct else Feedback.Wrong(answer)
            return
        }
        if (right) {
            solved++
            feedback = Feedback.Correct
        } else {
            registerMistake()
            queue.add(exercise)
            feedback = Feedback.Wrong(answer)
        }
    }

    /** A wrong pair in a matching exercise: costs a heart but the exercise carries on. */
    fun registerMistake() {
        mistakes++
        if (!heartsMatter) return
        hearts--
        onMistake()
    }

    /** Matching finished, or a teaching card was read. */
    fun markSolved(showFeedback: Boolean) {
        solved++
        if (showFeedback) feedback = Feedback.Correct else next()
    }

    fun next() {
        feedback = null
        selected = null
        orderAnswer = null
        index++
    }
}
