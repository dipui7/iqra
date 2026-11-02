package app.iqra.data

enum class LessonKind {
    /** Introduces each new letter, then quick recognition questions. */
    Learn,
    /** Mixed review of the unit's letters: names, sounds, shapes, matching. */
    Practice,
    /** Spot the unit's letters inside real words from Juz ʿAmma and Al-Fātiḥah. */
    Quran,
    /** Introduces fatḥa, kasra and ḍamma on letters already learned. */
    VowelsLearn,
    VowelsPractice,
    /** The "opening letters" (ḥurūf muqaṭṭaʿāt) heard in real recitation. */
    Openings,
    /** Introduces the unit's [CourseUnit.rule] with examples, then reading questions. */
    RuleLearn,
    /** Read practice syllables that use the rule. */
    RulePractice,
    /** Spot the rule's mark in real Quran words. */
    RuleQuran,
    /** Read practice words mixing every rule so far. */
    ReadingReview,
    /** Spot every rule in real Quran words. */
    QuranMarks,
    /** Memorise a surah: hear and read each ayah, then what-comes-next and ordering. */
    HifzLearn,
    /** Recall a surah from memory: next ayah, next word, listening, ordering. */
    HifzRecall,
}

enum class Track { Reading, Hifz }

data class Lesson(val id: String, val title: String, val kind: LessonKind)

data class CourseUnit(
    val id: String,
    val title: String,
    val subtitle: String,
    val letters: List<Letter>,
    val lessons: List<Lesson>,
    /** The reading rule this unit teaches, for units after the short vowels. */
    val rule: Rule? = null,
    /** Arabic shown on the unit's card: its letters, or an example of its marks. */
    val badge: String = letters.joinToString(" ") { it.char },
    val track: Track = Track.Reading,
    /** The surah memorised in this unit (memorisation track). */
    val surah: Int? = null,
)

/** An ayah that consists only of opening letters, with the letters it is made of. */
data class Opening(val ref: AyahRef, val letters: List<Letter>)

object Course {
    private fun letterUnit(n: Int, title: String, vararg letters: Letter) = CourseUnit(
        id = "u$n",
        title = title,
        subtitle = letters.joinToString(" · ") { it.name },
        letters = letters.toList(),
        lessons = listOf(
            Lesson("u$n-learn", "Meet the letters", LessonKind.Learn),
            Lesson("u$n-practice", "Practice", LessonKind.Practice),
            Lesson("u$n-quran", "Find them in the Quran", LessonKind.Quran),
        ),
    )

    val units: List<CourseUnit> = with(Letters) {
        listOf(
            letterUnit(1, "First letters", alif, ba, ta, tha),
            letterUnit(2, "The jim family", jim, haa, kha),
            letterUnit(3, "Letters that don't join", dal, dhal, ra, zay),
            letterUnit(4, "Teeth and loops", sin, shin, sad, dad),
            letterUnit(5, "Heavy and throat letters", taa, zaa, ayn, ghayn),
            letterUnit(6, "Fa to Lam", fa, qaf, kaf, lam),
            letterUnit(7, "The last five", mim, nun, ha, waw, ya),
            CourseUnit(
                id = "u8",
                title = "Short vowels",
                subtitle = "Fatḥa · Kasra · Ḍamma",
                letters = emptyList(),
                badge = Harakat.all.joinToString(" ") { Letters.ba.char + it.mark },
                lessons = listOf(
                    Lesson("u8-learn", "Meet the vowels", LessonKind.VowelsLearn),
                    Lesson("u8-practice", "Read syllables", LessonKind.VowelsPractice),
                ),
            ),
            CourseUnit(
                id = "u9",
                title = "Letters in the Quran",
                subtitle = "Listen to the opening letters",
                letters = emptyList(),
                lessons = listOf(
                    Lesson("u9-listen", "Listen and pick", LessonKind.Openings),
                    Lesson("u9-review", "Review", LessonKind.Openings),
                ),
            ),
            ruleUnit(10, "No vowel", Rules.sukun),
            ruleUnit(11, "Doubled letters", Rules.shadda),
            ruleUnit(12, "The \"n\" endings", Rules.tanwin),
            ruleUnit(13, "Stretching vowels", Rules.long),
            CourseUnit(
                id = "u14",
                title = "Reading words",
                subtitle = "Everything together",
                letters = emptyList(),
                lessons = listOf(
                    Lesson("u14-read", "Read practice words", LessonKind.ReadingReview),
                    Lesson("u14-quran", "Marks in the Quran", LessonKind.QuranMarks),
                    Lesson("u14-review", "Final review", LessonKind.ReadingReview),
                ),
            ),
        )
    }

    private fun ruleUnit(n: Int, title: String, rule: Rule) = CourseUnit(
        id = "u$n",
        title = title,
        subtitle = rule.name,
        letters = emptyList(),
        rule = rule,
        badge = when (rule) {
            Rules.sukun -> Pieces.closed(Letters.ba).arabic
            Rules.shadda -> Pieces.doubled(Letters.ba, Harakat.fatha).arabic
            Rules.tanwin -> Pieces.tanwin(Letters.ba, Harakat.damma).arabic
            else -> Pieces.long(Letters.ba, Harakat.fatha).arabic
        },
        lessons = listOf(
            Lesson("u$n-learn", "Meet ${rule.name.lowercase()}", LessonKind.RuleLearn),
            Lesson("u$n-practice", "Read with ${rule.name.lowercase()}", LessonKind.RulePractice),
            Lesson("u$n-quran", "Find it in the Quran", LessonKind.RuleQuran),
        ),
    )

    /** Rules taught in this unit and every unit before it. */
    fun rulesUpTo(unit: CourseUnit): List<Rule> =
        units.take(units.indexOf(unit) + 1).mapNotNull { it.rule }

    /** The memorisation track: one unit per short surah. */
    val hifzUnits: List<CourseUnit> = Hifz.surahs.map { s ->
        CourseUnit(
            id = "h${s.number}",
            title = s.name,
            subtitle = "${s.meaning} · ${Hifz.ayahCount.getValue(s.number)} ayat",
            letters = emptyList(),
            badge = arabicDigits(s.number),
            track = Track.Hifz,
            surah = s.number,
            lessons = listOf(
                Lesson("h${s.number}-learn", "Listen and learn", LessonKind.HifzLearn),
                Lesson("h${s.number}-recall", "Recite from memory", LessonKind.HifzRecall),
            ),
        )
    }

    private val allUnits = units + hifzUnits

    val allLessons: List<Lesson> = allUnits.flatMap { it.lessons }

    fun unitOf(lessonId: String): CourseUnit = allUnits.first { u -> u.lessons.any { it.id == lessonId } }

    fun lesson(lessonId: String): Lesson = allLessons.first { it.id == lessonId }

    /** Letters taught in this unit and every unit before it. */
    fun lettersUpTo(unit: CourseUnit): List<Letter> =
        units.take(units.indexOf(unit) + 1).flatMap { it.letters }

    /**
     * Reading lessons unlock once every reading lesson before them is completed. Memorisation is
     * personal (many learners already know some surahs), so every surah is open.
     */
    fun isUnlocked(lessonId: String, completed: Set<String>): Boolean {
        if (unitOf(lessonId).track == Track.Hifz) return true
        return units.flatMap { it.lessons }.takeWhile { it.id != lessonId }.all { it.id in completed }
    }

    val openings: List<Opening> = with(Letters) {
        listOf(
            Opening(AyahRef(2, 1), listOf(alif, lam, mim)),
            Opening(AyahRef(7, 1), listOf(alif, lam, mim, sad)),
            Opening(AyahRef(19, 1), listOf(kaf, ha, ya, ayn, sad)),
            Opening(AyahRef(20, 1), listOf(taa, ha)),
            Opening(AyahRef(26, 1), listOf(taa, sin, mim)),
            Opening(AyahRef(36, 1), listOf(ya, sin)),
            Opening(AyahRef(40, 1), listOf(haa, mim)),
            Opening(AyahRef(42, 2), listOf(ayn, sin, qaf)),
        )
    }

    /** Surahs whose words are used for "find the letter" questions. */
    val wordSurahs: List<Int> = listOf(1) + (78..114)
}
