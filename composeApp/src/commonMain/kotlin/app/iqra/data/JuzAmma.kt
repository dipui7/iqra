package app.iqra.data

/** Surahs offered in the reader: Al-Fātiḥah and Juz ʿAmma (78–114). Names are our own transliterations. */
object JuzAmma {
    val names: Map<Int, String> = mapOf(
        1 to "Al-Fātiḥah", 78 to "An-Nabaʾ", 79 to "An-Nāziʿāt", 80 to "ʿAbasa", 81 to "At-Takwīr",
        82 to "Al-Infiṭār", 83 to "Al-Muṭaffifīn", 84 to "Al-Inshiqāq", 85 to "Al-Burūj", 86 to "Aṭ-Ṭāriq",
        87 to "Al-Aʿlā", 88 to "Al-Ghāshiyah", 89 to "Al-Fajr", 90 to "Al-Balad", 91 to "Ash-Shams",
        92 to "Al-Layl", 93 to "Aḍ-Ḍuḥā", 94 to "Ash-Sharḥ", 95 to "At-Tīn", 96 to "Al-ʿAlaq",
        97 to "Al-Qadr", 98 to "Al-Bayyinah", 99 to "Az-Zalzalah", 100 to "Al-ʿĀdiyāt", 101 to "Al-Qāriʿah",
        102 to "At-Takāthur", 103 to "Al-ʿAṣr", 104 to "Al-Humazah", 105 to "Al-Fīl", 106 to "Quraysh",
        107 to "Al-Māʿūn", 108 to "Al-Kawthar", 109 to "Al-Kāfirūn", 110 to "An-Naṣr", 111 to "Al-Masad",
        112 to "Al-Ikhlāṣ", 113 to "Al-Falaq", 114 to "An-Nās",
    )

    /** In reading order: Al-Fātiḥah, then 78 to 114. */
    val surahs: List<Int> = listOf(1) + (78..114)
}
