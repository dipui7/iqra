package app.iqra.engine

import app.iqra.data.AyahRef
import app.iqra.data.Letter
import app.iqra.data.Sound

/** An answer button. [sound] (e.g. the letter's name) can be played by tapping it once answered. */
data class Option(val label: String, val arabic: Boolean, val sound: Sound? = null)

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
        /** Played when the [big] text is tapped, once answered (before, it would give the answer away). */
        val bigSound: Sound? = null,
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

    /** One ayah to listen to and read, while memorising a surah. */
    data class AyahIntro(val ref: AyahRef, val text: String, val surahName: String, val ayahCount: Int) : Exercise

    /**
     * Put Arabic items (ayat) in order by tapping them. [items] is the right order; items with the
     * same text are interchangeable.
     */
    data class Order(val prompt: String, val items: List<String>, val caption: String? = null, val skill: String? = null) : Exercise

    /**
     * Say something into the microphone. The recording is compared with [target] and the
     * [decoys] (see [VoiceMatch]); it passes if it sounds closest to [target].
     * [big] is the Arabic to read aloud, or null when it is to be recited from memory.
     * [reveal] is shown once the question is answered.
     */
    data class Voice(
        val prompt: String,
        val big: String?,
        val caption: String?,
        val target: Sound,
        val decoys: List<Sound>,
        val reveal: String? = null,
        val skill: String? = null,
    ) : Exercise

    /** Tap an Arabic item, then its matching Latin item, until all pairs are matched. */
    data class Match(val prompt: String, val pairs: List<Pair<String, String>>) : Exercise
}
