package app.iqra.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Feedback {
    data object Correct : Feedback
    data class Wrong(val answer: Option) : Feedback
}

/**
 * The state of one lesson in progress. Questions answered wrongly cost a heart and are
 * asked again at the end, so a lesson is only finished once every question is answered right.
 */
class LessonSession(
    exercises: List<Exercise>,
    startHearts: Int,
    private val onMistake: () -> Unit,
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

    var selected by mutableStateOf<Int?>(null)
    var feedback by mutableStateOf<Feedback?>(null)
        private set

    val current: Exercise? get() = queue.getOrNull(index)
    val progress: Float get() = solved.toFloat() / total
    val finished: Boolean get() = index >= queue.size
    val outOfHearts: Boolean get() = hearts <= 0
    val questionCount: Int get() = total

    /** Grade the selected option of the current multiple-choice question. */
    fun check() {
        val choice = current as? Exercise.Choice ?: return
        val pick = selected ?: return
        if (pick == choice.correct) {
            solved++
            feedback = Feedback.Correct
        } else {
            registerMistake()
            queue.add(choice)
            feedback = Feedback.Wrong(choice.options[choice.correct])
        }
    }

    /** A wrong pair in a matching exercise: costs a heart but the exercise carries on. */
    fun registerMistake() {
        mistakes++
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
        index++
    }
}
