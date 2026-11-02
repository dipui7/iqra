package app.iqra.engine

import app.iqra.data.AyahRef
import app.iqra.data.Haraka
import app.iqra.data.Letter

data class Option(val label: String, val arabic: Boolean)

sealed interface Exercise {
    /** A teaching card with no question; the learner just reads it and continues. */
    data class LetterIntro(val letter: Letter) : Exercise

    data class VowelIntro(val haraka: Haraka, val examples: List<Pair<String, String>>) : Exercise

    /**
     * A multiple-choice question. Almost every question type in the course is one of these:
     * what changes is the prompt, what is shown large, and what the options are.
     */
    data class Choice(
        val prompt: String,
        val options: List<Option>,
        val correct: Int,
        val big: String? = null,
        val bigArabic: Boolean = true,
        val caption: String? = null,
        val audio: AyahRef? = null,
        /** Play [audio] as soon as the question appears (listening questions). */
        val autoPlay: Boolean = false,
        val explanation: String? = null,
    ) : Exercise

    /** Tap an Arabic item, then its matching Latin item, until all pairs are matched. */
    data class Match(val prompt: String, val pairs: List<Pair<String, String>>) : Exercise
}
