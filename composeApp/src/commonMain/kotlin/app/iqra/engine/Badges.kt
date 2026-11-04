package app.iqra.engine

import app.iqra.data.Course
import app.iqra.data.Hifz
import app.iqra.data.Letters

/**
 * A milestone. Badges are worked out from progress each time, so nothing extra is saved and
 * they can't get out of step with it. [progress] says how far along a badge is (done, needed).
 */
data class Badge(
    val id: String,
    val title: String,
    val detail: String,
    val progress: (Progress) -> Pair<Int, Int>,
) {
    fun earned(p: Progress): Boolean = progress(p).let { (done, needed) -> done >= needed }
}

object Badges {
    private fun lessons(vararg ids: String) = { p: Progress -> ids.count { it in p.completed } to ids.size }

    private val letterLessons = Course.units.filter { it.letters.isNotEmpty() }.map { "${it.id}-learn" }.toTypedArray()
    private val ruleLessons = Course.units.filter { it.rule != null }.flatMap { u -> u.lessons.map { it.id } }.toTypedArray()
    private val surahsKnown = { p: Progress -> Hifz.surahs.count { "h${it.number}-recall" in p.completed } }

    val all: List<Badge> = listOf(
        Badge("first-lesson", "First step", "Finish your first lesson") { p -> minOf(p.completed.size, 1) to 1 },
        Badge("streak-3", "Three days", "Practise 3 days in a row") { p -> minOf(p.bestStreak, 3) to 3 },
        Badge("streak-7", "A whole week", "Practise 7 days in a row") { p -> minOf(p.bestStreak, 7) to 7 },
        Badge("streak-30", "A month of reading", "Practise 30 days in a row") { p -> minOf(p.bestStreak, 30) to 30 },
        Badge("xp-100", "100 XP", "Earn 100 XP") { p -> minOf(p.xp, 100) to 100 },
        Badge("xp-500", "500 XP", "Earn 500 XP") { p -> minOf(p.xp, 500) to 500 },
        Badge("xp-1000", "1000 XP", "Earn 1000 XP") { p -> minOf(p.xp, 1000) to 1000 },
        Badge("letters-met", "All 28 letters", "Meet every letter of the alphabet", lessons(*letterLessons)),
        Badge("letters-strong", "Strong letters", "Get all 28 letters to \"strong\" in practice") { p ->
            Letters.all.count { (p.skills[Skills.letter(it)]?.box ?: 0) >= 3 } to Letters.all.size
        },
        Badge("vowels", "Vowels", "Finish the short vowels unit", lessons("u8-learn", "u8-practice")),
        Badge("rules", "Reading rules", "Finish sukūn, shadda, tanwīn and long vowels", lessons(*ruleLessons)),
        Badge("reader", "Reader", "Finish the \"Reading words\" unit", lessons("u14-read", "u14-quran", "u14-review")),
        Badge("exam", "Exam passed", "Pass your first exam") { p -> minOf(p.passedExams.size, 1) to 1 },
        Badge("fatiha", "Al-Fātiḥah by heart", "Recite Al-Fātiḥah from memory", lessons("h1-recall")),
        Badge("surah-1", "First surah", "Recite any surah from memory") { p -> minOf(surahsKnown(p), 1) to 1 },
        Badge("surah-5", "Five surahs", "Recite 5 surahs from memory") { p -> minOf(surahsKnown(p), 5) to 5 },
        Badge("surah-all", "Hafiz of the short surahs", "Recite all ${Hifz.surahs.size} surahs from memory") { p ->
            surahsKnown(p) to Hifz.surahs.size
        },
    )

    fun earned(p: Progress): Set<String> = all.filter { it.earned(p) }.map { it.id }.toSet()

    fun byId(id: String): Badge? = all.firstOrNull { it.id == id }
}
