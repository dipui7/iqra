package app.iqra.data

/** Tajweed rules, grouped the way they are coloured and explained to the learner. */
enum class TajweedGroup(val label: String, val hint: String) {
    Madd("Madd", "Stretch the vowel longer than usual"),
    Ghunnah("Ghunnah", "A nasal sound held for two counts"),
    Ikhfa("Ikhfa", "Nun or tanwin hidden, said softly through the nose"),
    Idghaam("Idghaam", "A letter merged into the next one"),
    Iqlab("Iqlab", "Nun or tanwin turned into a meem sound"),
    Qalqalah("Qalqalah", "A light echo on ق ط ب ج د with sukun"),
    Silent("Silent", "Written but not pronounced"),
}

data class TajweedMark(val group: TajweedGroup, val rule: String, val start: Int, val end: Int)

/**
 * Tajweed marks (github.com/cpfair/quran-tajweed, CC BY 4.0) with positions mapped onto our
 * Tanzil text by tools/tajweed/map_tajweed.py. They only say which characters to colour; the
 * Quran text itself is never changed.
 */
class TajweedMarks private constructor(private val marks: Map<AyahRef, List<TajweedMark>>) {
    fun of(ref: AyahRef): List<TajweedMark> = marks[ref].orEmpty()

    companion object {
        fun groupOf(rule: String): TajweedGroup? = when {
            rule.startsWith("madd") -> TajweedGroup.Madd
            rule == "ghunnah" -> TajweedGroup.Ghunnah
            rule.startsWith("ikhfa") -> TajweedGroup.Ikhfa
            rule.startsWith("idghaam") -> TajweedGroup.Idghaam
            rule == "iqlab" -> TajweedGroup.Iqlab
            rule == "qalqalah" -> TajweedGroup.Qalqalah
            rule == "hamzat_wasl" || rule == "lam_shamsiyyah" || rule == "silent" -> TajweedGroup.Silent
            else -> null
        }

        fun parse(text: String): TajweedMarks {
            val out = HashMap<AyahRef, List<TajweedMark>>(6300)
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val (s, a, list) = line.split('|', limit = 3)
                out[AyahRef(s.toInt(), a.toInt())] = list.split(',').mapNotNull { m ->
                    val (rule, start, end) = m.split(':')
                    groupOf(rule)?.let { TajweedMark(it, rule, start.toInt(), end.toInt()) }
                }
            }
            return TajweedMarks(out)
        }
    }
}

/** An ayah-by-ayah translation from Tanzil (tanzil.net/trans), shown alongside the Arabic. */
class Translation private constructor(val name: String, private val ayat: Map<AyahRef, String>) {
    fun of(ref: AyahRef): String? = ayat[ref]

    companion object {
        /** Tanzil's "text with aya numbers" format: surah|ayah|text, then a # header block. */
        fun parse(name: String, text: String): Translation {
            val out = HashMap<AyahRef, String>(6300)
            for (line in text.lineSequence()) {
                val parts = line.split('|', limit = 3)
                if (parts.size == 3 && parts[0].toIntOrNull() != null) {
                    out[AyahRef(parts[0].toInt(), parts[1].toInt())] = parts[2]
                }
            }
            check(out.size == 6236) { "Translation incomplete: ${out.size} ayat" }
            return Translation(name, out)
        }
    }
}
