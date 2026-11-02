package app.iqra.engine

import app.iqra.data.Letter
import app.iqra.data.Sound

data class Option(val label: String, val arabic: Boolean)

/** A letter with a vowel mark, e.g. بَ read "ba", and its recording if there is one. */
data class Syllable(val arabic: String, val latin: String, val sound: Sound? = null)

sealed interface Exercise {
    /**
     * A teaching card with no question; the learner just reads it and continues.
     * [name] says the letter's name, [sound] reads it with fatḥa ("ba"), when recorded.
     */
    data class LetterIntro(val letter: Letter, val name: Sound? = null, val sound: Sound? = null) : Exercise

    /** Introduces a vowel mark or reading rule, with tappable examples. */
    data class RuleIntro(val label: String, val name: String, val hint: String, val examples: List<Syllable>) : Exercise

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
        val audio: Sound? = null,
        /** Play [audio] as soon as the question appears (listening questions). */
        val autoPlay: Boolean = false,
        /** Offer [audio] only after answering, because hearing it would give the answer away. */
        val audioAfterAnswer: Boolean = false,
        val explanation: String? = null,
        /** What this question practises, e.g. "letter:ba" or "rule:shadda" (see [Skills]). */
        val skill: String? = null,
    ) : Exercise

    /** Tap an Arabic item, then its matching Latin item, until all pairs are matched. */
    data class Match(val prompt: String, val pairs: List<Pair<String, String>>) : Exercise
}
