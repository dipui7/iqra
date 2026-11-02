package app.iqra.data

private const val ZWJ = "‍"

/**
 * One letter of the Arabic alphabet. Names, transliterations and hints are our own
 * teaching text; they are not Quranic text.
 *
 * @param sound short Latin transliteration used for syllables (b + a = "ba").
 * @param joins false for the six letters that never connect to the letter after them.
 * @param quranExample an ayah where the letter's name is recited on its own
 *        (the "opening letters" at the start of some surahs), used for audio.
 */
data class Letter(
    val char: String,
    val name: String,
    val sound: String,
    val hint: String,
    val joins: Boolean = true,
    val quranExample: AyahRef? = null,
) {
    // A zero-width joiner next to the letter makes the font draw its connected shapes.
    val initial: String get() = if (joins) "$char$ZWJ" else char
    val medial: String get() = if (joins) "$ZWJ$char$ZWJ" else "$ZWJ$char"
    val final: String get() = "$ZWJ$char"
}

object Letters {
    val alif = Letter("ا", "Alif", "a", "Carries a long \"aa\" sound, or a hamza", joins = false, quranExample = AyahRef(2, 1))
    val ba = Letter("ب", "Ba", "b", "\"b\" as in book — one dot below")
    val ta = Letter("ت", "Ta", "t", "\"t\" as in tea — two dots above")
    val tha = Letter("ث", "Tha", "th", "\"th\" as in think — three dots above")
    val jim = Letter("ج", "Jim", "j", "\"j\" as in jam — one dot inside")
    val haa = Letter("ح", "Ḥa", "ḥ", "A breathy \"h\" from the middle of the throat", quranExample = AyahRef(40, 1))
    val kha = Letter("خ", "Kha", "kh", "\"kh\" as in Scottish loch — one dot above")
    val dal = Letter("د", "Dal", "d", "\"d\" as in door", joins = false)
    val dhal = Letter("ذ", "Dhal", "dh", "\"th\" as in this — one dot above", joins = false)
    val ra = Letter("ر", "Ra", "r", "A lightly rolled \"r\"", joins = false, quranExample = AyahRef(10, 1))
    val zay = Letter("ز", "Zay", "z", "\"z\" as in zoo — one dot above", joins = false)
    val sin = Letter("س", "Sin", "s", "\"s\" as in sun — three teeth", quranExample = AyahRef(36, 1))
    val shin = Letter("ش", "Shin", "sh", "\"sh\" as in shoe — three dots above")
    val sad = Letter("ص", "Ṣad", "ṣ", "A heavy, full-mouthed \"s\"", quranExample = AyahRef(38, 1))
    val dad = Letter("ض", "Ḍad", "ḍ", "A heavy \"d\" — Ṣad with a dot")
    val taa = Letter("ط", "Ṭa", "ṭ", "A heavy, full-mouthed \"t\"", quranExample = AyahRef(20, 1))
    val zaa = Letter("ظ", "Ẓa", "ẓ", "A heavy \"th\" as in this — Ṭa with a dot")
    val ayn = Letter("ع", "ʿAyn", "ʿ", "A deep sound squeezed from the throat", quranExample = AyahRef(42, 2))
    val ghayn = Letter("غ", "Ghayn", "gh", "A gargled \"gh\", like a French r")
    val fa = Letter("ف", "Fa", "f", "\"f\" as in fish — one dot above")
    val qaf = Letter("ق", "Qaf", "q", "A deep \"k\" from the back of the throat", quranExample = AyahRef(50, 1))
    val kaf = Letter("ك", "Kaf", "k", "\"k\" as in kite", quranExample = AyahRef(19, 1))
    val lam = Letter("ل", "Lam", "l", "\"l\" as in lamp", quranExample = AyahRef(2, 1))
    val mim = Letter("م", "Mim", "m", "\"m\" as in moon", quranExample = AyahRef(2, 1))
    val nun = Letter("ن", "Nun", "n", "\"n\" as in noon — one dot above", quranExample = AyahRef(68, 1))
    val ha = Letter("ه", "Ha", "h", "A light \"h\" as in hat", quranExample = AyahRef(20, 1))
    val waw = Letter("و", "Waw", "w", "\"w\" as in wall, or a long \"oo\"", joins = false)
    val ya = Letter("ي", "Ya", "y", "\"y\" as in yes, or a long \"ee\"", quranExample = AyahRef(36, 1))

    val all = listOf(
        alif, ba, ta, tha, jim, haa, kha, dal, dhal, ra, zay, sin, shin, sad,
        dad, taa, zaa, ayn, ghayn, fa, qaf, kaf, lam, mim, nun, ha, waw, ya,
    )

    /**
     * Characters that count as "this letter" when spotting it in a Quran word:
     * hamza-carrying and wasla forms of alif, waw and ya are treated as the base letter.
     */
    private val variants = mapOf(
        "ا" to "اأإآٱ",
        "و" to "وؤ",
        "ي" to "يئ",
    )

    fun appearsIn(letter: Letter, word: String): Boolean {
        val chars = variants[letter.char] ?: letter.char
        return word.any { it in chars }
    }
}

/** A short vowel mark written above or below a letter. */
data class Haraka(val mark: String, val name: String, val sound: String, val hint: String)

object Harakat {
    val fatha = Haraka("َ", "Fatḥa", "a", "A small stroke above the letter: a short \"a\"")
    val kasra = Haraka("ِ", "Kasra", "i", "A small stroke below the letter: a short \"i\"")
    val damma = Haraka("ُ", "Ḍamma", "u", "A small waw above the letter: a short \"u\"")
    val all = listOf(fatha, kasra, damma)
}
