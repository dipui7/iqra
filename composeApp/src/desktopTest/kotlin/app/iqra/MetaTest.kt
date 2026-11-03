package app.iqra

import app.iqra.data.QuranMeta
import app.iqra.data.QuranText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaTest {
    private val res = File("src/commonMain/composeResources/files")
    private val quran = QuranText.parse(File(res, "quran_uthmani.xml").readText())
    private val meta = QuranMeta.parse(File(res, "quran_metadata.xml").readText())

    @Test
    fun metadataAgreesWithTheQuranText() {
        for (s in meta.surahs) {
            assertEquals(quran.surah(s.number).size, s.ayahCount, "ayah count of surah ${s.number}")
            assertEquals(quran.suraName(s.number), s.arabicName, "Arabic name of surah ${s.number}")
        }
    }

    @Test
    fun surahsSitInTheRightJuz() {
        assertEquals(1, meta.juzOf(1))
        assertEquals(1, meta.juzOf(2))
        assertEquals(30, meta.juzOf(78))
        assertEquals(30, meta.juzOf(114))
        // Al-Kahf (18) starts in juz 15; the juz never goes backwards from one surah to the next.
        assertEquals(15, meta.juzOf(18))
        val juz = meta.surahs.map { meta.juzOf(it.number) }
        assertEquals(juz.sorted(), juz)
    }
}
