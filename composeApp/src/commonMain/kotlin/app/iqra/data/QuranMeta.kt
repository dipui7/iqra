package app.iqra.data

data class SurahInfo(
    val number: Int,
    /** Transliterated name: ours for Juz ʿAmma and Al-Fātiḥah, Tanzil's for the rest. */
    val name: String,
    /** Arabic name, from Tanzil's metadata (not Quranic text). */
    val arabicName: String,
    /** English meaning of the name, from Tanzil. */
    val meaning: String,
    val meccan: Boolean,
    val ayahCount: Int,
)

/**
 * Surah names and the start of each juz, from Tanzil's metadata file (tanzil.net, CC BY 3.0),
 * bundled unmodified in composeResources/files/quran_metadata.xml.
 */
class QuranMeta private constructor(
    val surahs: List<SurahInfo>,
    /** Where each juz starts, as (surah, ayah), juz 1 first. */
    private val juzStarts: List<Pair<Int, Int>>,
) {
    fun surah(number: Int): SurahInfo = surahs[number - 1]

    /** The juz in which the surah begins. */
    fun juzOf(surah: Int): Int = juzStarts.indexOfLast { (s, a) -> s < surah || (s == surah && a == 1) } + 1

    companion object {
        private val suraRegex =
            Regex("""<sura index="(\d+)" ayas="(\d+)" start="\d+" name="([^"]*)" tname="([^"]*)" ename="([^"]*)" type="(\w+)"""")
        private val juzRegex = Regex("""<juz index="(\d+)" sura="(\d+)" aya="(\d+)"""")

        fun parse(xml: String): QuranMeta {
            val surahs = suraRegex.findAll(xml).map { m ->
                val n = m.groupValues[1].toInt()
                SurahInfo(
                    number = n,
                    name = JuzAmma.names[n] ?: m.groupValues[4],
                    arabicName = m.groupValues[3],
                    meaning = m.groupValues[5],
                    meccan = m.groupValues[6] == "Meccan",
                    ayahCount = m.groupValues[2].toInt(),
                )
            }.toList()
            val juz = juzRegex.findAll(xml).map { it.groupValues[2].toInt() to it.groupValues[3].toInt() }.toList()
            check(surahs.size == 114 && surahs.map { it.number } == (1..114).toList()) { "Surah list incomplete" }
            check(juz.size == 30) { "Juz list incomplete" }
            return QuranMeta(surahs, juz)
        }
    }
}
