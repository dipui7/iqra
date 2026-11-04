package app.iqra.data

data class AyahRef(val sura: Int, val aya: Int) {
    /** Per-ayah recitation by Mishary Rashid Alafasy, streamed from everyayah.com (never bundled). */
    val audioUrl: String
        get() = "https://everyayah.com/data/Alafasy_128kbps/${pad(sura)}${pad(aya)}.mp3"

    override fun toString() = "$sura:$aya"

    private fun pad(n: Int) = n.toString().padStart(3, '0')
}

/** One space-separated word of an ayah, exactly as it appears in the Tanzil text. */
data class QuranWord(val text: String, val ref: AyahRef)

/**
 * The Tanzil Uthmani Quran text (tanzil.net, CC BY 3.0), loaded from the unmodified
 * XML file bundled in composeResources/files. The app only ever displays this text
 * verbatim: whole ayat, or single words split at the spaces already in the text.
 */
class QuranText private constructor(
    private val ayat: Map<AyahRef, String>,
    private val suraNames: Map<Int, String>,
    private val bismillahs: Map<Int, String>,
) {
    /** The basmala shown above a surah, exactly as Tanzil gives it (none for 1, where it is ayah 1, or 9). */
    fun bismillah(sura: Int): String? = bismillahs[sura]

    fun text(ref: AyahRef): String = ayat[ref] ?: error("No ayah $ref")

    fun suraName(sura: Int): String = suraNames[sura] ?: sura.toString()

    /** Every ayah of a surah, in order. */
    fun surah(sura: Int): List<Pair<AyahRef, String>> =
        generateSequence(1) { it + 1 }.map { AyahRef(sura, it) }.takeWhile { it in ayat }.map { it to ayat.getValue(it) }.toList()

    fun words(sura: Int): List<QuranWord> =
        ayat.filterKeys { it.sura == sura }.flatMap { (ref, text) ->
            // Skip stand-alone pause marks and the rubʿ symbol, which are not words.
            text.split(' ').filter { token -> token.any { it in 'ء'..'ي' } }
                .map { QuranWord(it, ref) }
        }

    companion object {
        private val suraRegex = Regex("""<sura index="(\d+)" name="([^"]*)"""")
        private val ayaRegex = Regex("""<aya index="(\d+)" text="([^"]*)"""")
        private val bismillahRegex = Regex("""bismillah="([^"]*)"""")

        fun parse(xml: String): QuranText {
            val ayat = HashMap<AyahRef, String>(6300)
            val names = HashMap<Int, String>(120)
            val bismillahs = HashMap<Int, String>(120)
            var sura = 0
            for (line in xml.lineSequence()) {
                suraRegex.find(line)?.let {
                    sura = it.groupValues[1].toInt()
                    names[sura] = it.groupValues[2]
                }
                ayaRegex.find(line)?.let {
                    ayat[AyahRef(sura, it.groupValues[1].toInt())] = it.groupValues[2]
                    bismillahRegex.find(line)?.let { b -> bismillahs[sura] = b.groupValues[1] }
                }
            }
            check(ayat.size == 6236) { "Quran text incomplete: ${ayat.size} ayat" }
            return QuranText(ayat, names, bismillahs)
        }
    }
}
