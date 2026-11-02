package app.iqra.data

private const val ZWJ = "‍"

/**
 * One letter of the Arabic alphabet. Names, transliterations and hints are our own
 * teaching text; they are not Quranic text.
 *
 * @param key ASCII id, used for file names of the letter's recordings.
 * @param sound short Latin transliteration used for syllables (b + a = "ba").
 * @param joins false for the six letters that never connect to the letter after them.
 * @param quranExample an ayah that starts with the letter's name recited (the "opening
 *        letters" of some surahs). It plays the whole ayah, so it is offered as "hear it in the
 *        Quran", never as the letter's own sound.
 */
data class Letter(
    val key: String,
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
    val alif = Letter("alif", "ا", "Alif", "a", "Carries a long \"aa\" sound, or a hamza", joins = false, quranExample = AyahRef(2, 1))
    val ba = Letter("ba", "ب", "Ba", "b", "\"b\" as in book — one dot below")
    val ta = Letter("ta", "ت", "Ta", "t", "\"t\" as in tea — two dots above")
    val tha = Letter("tha", "ث", "Tha", "th", "\"th\" as in think — three dots above")
    val jim = Letter("jim", "ج", "Jim", "j", "\"j\" as in jam — one dot inside")
    val haa = Letter("haa", "ح", "Ḥa", "ḥ", "A breathy \"h\" from the middle of the throat", quranExample = AyahRef(40, 1))
    val kha = Letter("kha", "خ", "Kha", "kh", "\"kh\" as in Scottish loch — one dot above")
    val dal = Letter("dal", "د", "Dal", "d", "\"d\" as in door", joins = false)
    val dhal = Letter("dhal", "ذ", "Dhal", "dh", "\"th\" as in this — one dot above", joins = false)
    val ra = Letter("ra", "ر", "Ra", "r", "A lightly rolled \"r\"", joins = false, quranExample = AyahRef(10, 1))
    val zay = Letter("zay", "ز", "Zay", "z", "\"z\" as in zoo — one dot above", joins = false)
    val sin = Letter("sin", "س", "Sin", "s", "\"s\" as in sun — three teeth", quranExample = AyahRef(36, 1))
    val shin = Letter("shin", "ش", "Shin", "sh", "\"sh\" as in shoe — three dots above")
    val sad = Letter("sad", "ص", "Ṣad", "ṣ", "A heavy, full-mouthed \"s\"", quranExample = AyahRef(38, 1))
    val dad = Letter("dad", "ض", "Ḍad", "ḍ", "A heavy \"d\" — Ṣad with a dot")
    val taa = Letter("taa", "ط", "Ṭa", "ṭ", "A heavy, full-mouthed \"t\"", quranExample = AyahRef(20, 1))
    val zaa = Letter("zaa", "ظ", "Ẓa", "ẓ", "A heavy \"th\" as in this — Ṭa with a dot")
    val ayn = Letter("ayn", "ع", "ʿAyn", "ʿ", "A deep sound squeezed from the throat", quranExample = AyahRef(42, 2))
    val ghayn = Letter("ghayn", "غ", "Ghayn", "gh", "A gargled \"gh\", like a French r")
    val fa = Letter("fa", "ف", "Fa", "f", "\"f\" as in fish — one dot above")
    val qaf = Letter("qaf", "ق", "Qaf", "q", "A deep \"k\" from the back of the throat", quranExample = AyahRef(50, 1))
    val kaf = Letter("kaf", "ك", "Kaf", "k", "\"k\" as in kite", quranExample = AyahRef(19, 1))
    val lam = Letter("lam", "ل", "Lam", "l", "\"l\" as in lamp", quranExample = AyahRef(2, 1))
    val mim = Letter("mim", "م", "Mim", "m", "\"m\" as in moon", quranExample = AyahRef(2, 1))
    val nun = Letter("nun", "ن", "Nun", "n", "\"n\" as in noon — one dot above", quranExample = AyahRef(68, 1))
    val ha = Letter("ha", "ه", "Ha", "h", "A light \"h\" as in hat", quranExample = AyahRef(20, 1))
    val waw = Letter("waw", "و", "Waw", "w", "\"w\" as in wall, or a long \"oo\"", joins = false)
    val ya = Letter("ya", "ي", "Ya", "y", "\"y\" as in yes, or a long \"ee\"", quranExample = AyahRef(36, 1))

    /** What is heard in each [Letter.quranExample] ayah, in the order it is recited. */
    val quranExampleRecited: Map<AyahRef, String> = mapOf(
        AyahRef(2, 1) to "Alif · Lām · Mīm",
        AyahRef(10, 1) to "Alif · Lām · Rā, then the rest of the ayah",
        AyahRef(19, 1) to "Kāf · Hā · Yā · ʿAyn · Ṣād",
        AyahRef(20, 1) to "Ṭā · Hā",
        AyahRef(36, 1) to "Yā · Sīn",
        AyahRef(38, 1) to "Ṣād, then the rest of the ayah",
        AyahRef(40, 1) to "Ḥā · Mīm",
        AyahRef(42, 2) to "ʿAyn · Sīn · Qāf",
        AyahRef(50, 1) to "Qāf, then the rest of the ayah",
        AyahRef(68, 1) to "Nūn, then the rest of the ayah",
    )

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
