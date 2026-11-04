package app.iqra.data

/** Something the app can play. */
sealed interface Sound {
    /** A whole ayah recited by Mishary Alafasy, streamed from EveryAyah. */
    data class Ayah(val ref: AyahRef) : Sound

    /** A short recording bundled with the app, at [path] inside composeResources. */
    data class Clip(val path: String) : Sound
}

/**
 * Recordings of each letter on its own, bundled in composeResources/files/letters:
 *
 * - `<key>.mp3`       the letter's name, e.g. "bāʾ"
 * - `<key>_a.mp3`     the letter with fatḥa, e.g. "ba" — this is how its sound is taught
 * - `<key>_i.mp3`     the letter with kasra, e.g. "bi"
 * - `<key>_u.mp3`     the letter with ḍamma, e.g. "bu"
 *
 * Any of these may be missing; the app only offers the ones that are bundled ([available]).
 * tools/letter-audio makes them from a recording.
 */
class LetterAudio(val available: Set<String>) {

    fun name(letter: Letter): Sound.Clip? = clip(namePath(letter))

    fun syllable(letter: Letter, haraka: Haraka): Sound.Clip? = clip(syllablePath(letter, haraka))

    /** The letter's sound: the letter read with fatḥa ("ba"). */
    fun sound(letter: Letter): Sound.Clip? = syllable(letter, Harakat.fatha)

    private fun clip(path: String?) = path?.takeIf { it in available }?.let(Sound::Clip)

    companion object {
        const val DIR = "files/letters"

        val none = LetterAudio(emptySet())

        fun namePath(letter: Letter) = "$DIR/${letter.key}.mp3"

        /** Alif carries no vowel itself (a hamza does), so it has no syllable recordings. */
        fun syllablePath(letter: Letter, haraka: Haraka): String? =
            if (letter == Letters.alif) null else "$DIR/${letter.key}_${haraka.sound}.mp3"

        /** Every recording the app knows how to use. */
        val allPaths: List<String> = Letters.all.flatMap { letter ->
            listOf(namePath(letter)) + Harakat.all.mapNotNull { syllablePath(letter, it) }
        }
    }
}
