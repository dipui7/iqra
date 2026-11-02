package app.iqra.data

/** A surah in the memorisation track. [name] is our own transliteration, not Quranic text. */
data class HifzSurah(val number: Int, val name: String, val meaning: String)

object Hifz {
    /** Short surahs, roughly in the order they are usually memorised (shortest and best known first). */
    val surahs = listOf(
        HifzSurah(1, "Al-Fātiḥah", "The Opening"),
        HifzSurah(112, "Al-Ikhlāṣ", "Sincerity"),
        HifzSurah(113, "Al-Falaq", "The Daybreak"),
        HifzSurah(114, "An-Nās", "Mankind"),
        HifzSurah(108, "Al-Kawthar", "Abundance"),
        HifzSurah(103, "Al-ʿAṣr", "Time"),
        HifzSurah(110, "An-Naṣr", "Help"),
        HifzSurah(106, "Quraysh", "Quraysh"),
        HifzSurah(105, "Al-Fīl", "The Elephant"),
        HifzSurah(111, "Al-Masad", "Palm Fibre"),
        HifzSurah(107, "Al-Māʿūn", "Small Kindnesses"),
        HifzSurah(109, "Al-Kāfirūn", "The Disbelievers"),
        HifzSurah(97, "Al-Qadr", "The Night of Decree"),
        HifzSurah(102, "At-Takāthur", "Rivalry in Increase"),
        HifzSurah(104, "Al-Humazah", "The Slanderer"),
        HifzSurah(99, "Az-Zalzalah", "The Earthquake"),
        HifzSurah(95, "At-Tīn", "The Fig"),
        HifzSurah(94, "Ash-Sharḥ", "The Relief"),
        HifzSurah(101, "Al-Qāriʿah", "The Striking Calamity"),
        HifzSurah(100, "Al-ʿĀdiyāt", "The Chargers"),
        HifzSurah(93, "Aḍ-Ḍuḥā", "The Morning Hours"),
    )

    fun surah(number: Int): HifzSurah = surahs.first { it.number == number }

    /** Ayah counts, so the course outline doesn't need the Quran text loaded. */
    val ayahCount = mapOf(
        1 to 7, 93 to 11, 94 to 8, 95 to 8, 97 to 5, 99 to 8, 100 to 11, 101 to 11, 102 to 8,
        103 to 3, 104 to 9, 105 to 5, 106 to 4, 107 to 7, 108 to 3, 109 to 6, 110 to 3,
        111 to 5, 112 to 4, 113 to 5, 114 to 6,
    )
}

/** Western digits as Arabic-Indic digits, e.g. 112 → ١١٢. */
fun arabicDigits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")
