package app.iqra

import app.iqra.resources.Res

/** The app's feedback sounds (tools/sfx/make_sfx.py): natural sounds, deliberately not music. */
enum class Effect(val file: String) {
    Correct("correct"),
    Wrong("wrong"),
    Complete("complete"),
    Badge("badge"),
    /** Out of hearts, or a lesson not passed. */
    Heart("heart"),
}

object Effects {
    /** Follows the "Sound effects" setting. */
    var enabled = true
    private val loaded = HashMap<Effect, ByteArray>()

    /** Read every effect once at start-up; a missing file just stays silent. */
    suspend fun load() {
        for (e in Effect.entries) runCatching { Res.readBytes("files/sfx/${e.file}.mp3") }.getOrNull()?.let { loaded[e] = it }
    }

    fun play(effect: Effect) {
        if (enabled) loaded[effect]?.let(::playEffect)
    }
}
