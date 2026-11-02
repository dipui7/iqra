package app.iqra.data

/*
 * The reading rules taught after the short vowels: sukūn, shadda, tanwīn and long vowels.
 * Practice syllables are put together from single letters and marks for teaching, the way a
 * Qāʿida (primer) does; they are not Quranic text. Quran words are only ever shown verbatim.
 */

const val SUKUN = "ْ"
const val SHADDA = "ّ"
const val FATHATAN = "ً"
const val DAMMATAN = "ٌ"
const val KASRATAN = "ٍ"
private const val SUPERSCRIPT_ALEF = "ٰ"

/** A reading rule: one mark (or letter pattern) with its explanation. */
data class Rule(
    val key: String,
    val name: String,
    val hint: String,
    /** How to spot the rule in a Quran word, so "find it in the Quran" questions are right. */
    val inWord: (String) -> Boolean,
    /** Words that surely do *not* show the rule (stricter than !inWord where the script is subtle). */
    val clearlyWithout: (String) -> Boolean = { !inWord(it) },
)

object Rules {
    val sukun = Rule(
        "sukun", "Sukūn",
        "A small circle above a letter: the letter has no vowel of its own. It closes the syllable before it: m-a-k is read \"mak\".",
        inWord = { SUKUN in it },
    )
    val shadda = Rule(
        "shadda", "Shadda",
        "A small \"w\" above a letter: say the letter twice — hold it, then add its vowel: r-a-bb-a is read \"rabba\".",
        inWord = { SHADDA in it },
    )
    val tanwin = Rule(
        "tanwin", "Tanwīn",
        "A doubled vowel mark adds an \"n\" sound: \"an\", \"in\", \"un\". It comes at the end of a word; the \"an\" form is usually followed by an alif, which is not read.",
        inWord = { w -> listOf(FATHATAN, KASRATAN, DAMMATAN).any { it in w } },
    )
    val long = Rule(
        "long", "Long vowels",
        "Alif after fatḥa, yā after kasra and wāw after ḍamma stretch the vowel to two beats: \"bā\", \"bī\", \"bū\". In the Quran a small alif above a letter does the same.",
        // Fatḥa+alif, kasra+yā, ḍamma+wāw, or the small (superscript) alif of the Uthmani script.
        inWord = { w ->
            SUPERSCRIPT_ALEF in w || Regex("َا|ِي|ُو").containsMatchIn(w)
        },
        // The Uthmani script also lengthens with small wāw/yā, maddah and alif maqṣūra; a word
        // with none of the long-vowel letters or signs surely has no long vowel.
        clearlyWithout = { w -> w.none { it in "اوىيٰۥۦٓ" } },
    )
    val all = listOf(sukun, shadda, tanwin, long)
}

/** A piece of a practice word: Arabic letters with marks, and how it is read. */
data class Piece(val arabic: String, val latin: String)

/** Builders for practice syllables. [Letter.sound] gives the consonant. */
object Pieces {
    fun open(l: Letter, h: Haraka) = Piece(l.char + h.mark, l.sound + h.sound)

    fun closed(l: Letter) = Piece(l.char + SUKUN, l.sound)

    fun doubled(l: Letter, h: Haraka) = Piece(l.char + SHADDA + h.mark, l.sound + l.sound + h.sound)

    fun tanwin(l: Letter, h: Haraka) = when (h) {
        Harakat.fatha -> Piece(l.char + FATHATAN + "ا", l.sound + "an")
        Harakat.kasra -> Piece(l.char + KASRATAN, l.sound + "in")
        else -> Piece(l.char + DAMMATAN, l.sound + "un")
    }

    fun long(l: Letter, h: Haraka) = when (h) {
        Harakat.fatha -> Piece(l.char + h.mark + "ا", l.sound + "ā")
        Harakat.kasra -> Piece(l.char + h.mark + "ي", l.sound + "ī")
        else -> Piece(l.char + h.mark + "و", l.sound + "ū")
    }

    /** Join pieces into one practice word. An apostrophe keeps e.g. s + h apart from "sh". */
    fun word(pieces: List<Piece>): Piece {
        val latin = StringBuilder()
        for (p in pieces) {
            val prev = latin.lastOrNull()
            if (prev != null && prev in "sktdgz" && p.latin.startsWith("h")) latin.append('\'')
            latin.append(p.latin)
        }
        return Piece(pieces.joinToString("") { it.arabic }, latin.toString())
    }
}
