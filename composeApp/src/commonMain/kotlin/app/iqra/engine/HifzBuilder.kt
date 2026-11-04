package app.iqra.engine

import app.iqra.data.Hifz
import app.iqra.data.QuranText
import app.iqra.data.Sound
import kotlin.random.Random

/**
 * Exercises for memorising a surah. Everything shown is Tanzil text, verbatim: whole ayat, or
 * the first words of an ayah exactly as written (the text up to one of its own spaces).
 *
 * Questions are only asked when they have exactly one right answer: an ayah whose text occurs
 * twice in the surah (Al-Kāfirūn 3 and 5) is never used as a "what comes next" prompt, and
 * option texts are always distinct.
 */
class HifzBuilder(private val quran: QuranText, private val random: Random = Random.Default) {

    private fun ayat(surah: Int) = quran.surah(surah)

    private fun nameOf(surah: Int) = Hifz.surahs.firstOrNull { it.number == surah }?.name ?: quran.suraName(surah)

    fun learn(surah: Int): List<Exercise> {
        val ayat = ayat(surah)
        val name = nameOf(surah)
        val out = mutableListOf<Exercise>()
        ayat.forEachIndexed { i, (ref, text) ->
            out += Exercise.AyahIntro(ref, text, name, ayat.size)
            // After each new ayah, check the link from the one before it.
            if (i >= 1) nextAyah(surah, i - 1)?.let { out += it }
        }
        out += orderChunks(surah, maxItems = 5).take(2)
        out += List(3) { nextWord(surah) }.filterNotNull().distinctBy { it.big }
        return out
    }

    fun recall(surah: Int): List<Exercise> {
        val n = ayat(surah).size
        val questions = (0 until n - 1).shuffled(random).take(4).mapNotNull { nextAyah(surah, it) } +
            List(4) { nextWord(surah) }.filterNotNull().distinctBy { it.big } +
            List(2) { listenWhich(surah) }.filterNotNull().distinctBy { it.audio }
        val order = orderChunks(surah, maxItems = 6).shuffled(random).take(1)
        val mixed = questions.shuffled(random)
        return mixed.take(mixed.size / 2) + order + mixed.drop(mixed.size / 2)
    }

    /** One review question for a surah, for practice sessions. */
    fun reviewQuestion(surah: Int): Exercise.Choice? {
        val n = ayat(surah).size
        return when (random.nextInt(3)) {
            0 -> nextAyah(surah, random.nextInt(maxOf(1, n - 1)))
            1 -> listenWhich(surah)
            else -> null
        } ?: nextWord(surah)
    }

    /** Other ayat to offer as wrong options: this surah first, then the surahs around it. */
    private fun otherAyat(surah: Int, exclude: Set<String>): List<String> {
        val near = ayat(surah).map { it.second }
        val longest = near.maxOf { it.length }
        val around = listOf(surah - 1, surah + 1).filter { it in 1..114 }
            .flatMap { s -> ayat(s).map { it.second } }
            .filter { it.length <= maxOf(60, longest + 20) }
        return (near.shuffled(random) + around.shuffled(random)).filter { it !in exclude }.distinct()
    }

    /** "What comes next?" — shows ayah [i] (0-based), asks for ayah i+1. */
    fun nextAyah(surah: Int, i: Int): Exercise.Choice? {
        val ayat = ayat(surah)
        if (i + 1 >= ayat.size) return null
        val (ref, text) = ayat[i]
        if (ayat.count { it.second == text } > 1) return null
        val answer = ayat[i + 1].second
        val wrong = otherAyat(surah, setOf(answer, text)).take(3)
        if (wrong.size < 2) return null
        val choices = (wrong + answer).shuffled(random)
        return Exercise.Choice(
            prompt = "What comes next?",
            big = text,
            caption = "${nameOf(surah)} · ayah ${ref.aya}",
            options = choices.map { Option(it, arabic = true) },
            correct = choices.indexOf(answer),
            audio = Sound.Ayah(ref),
            explanation = "Ayah ${ref.aya + 1} of ${nameOf(surah)}",
            skill = Skills.surah(surah),
        )
    }

    /** "Which word comes next?" — the start of an ayah, cut at one of its own spaces. */
    fun nextWord(surah: Int): Exercise.Choice? {
        val ayat = ayat(surah)
        val candidates = ayat.flatMap { (ref, text) ->
            val tokens = text.split(' ')
            (1 until tokens.size).map { k -> Triple(ref, tokens, k) }
        }.filter { (_, tokens, k) -> isWord(tokens[k]) }.shuffled(random)
        for ((ref, tokens, k) in candidates) {
            val prefix = tokens.take(k).joinToString(" ")
            val answer = tokens[k]
            // The same opening words must not continue differently elsewhere in the surah.
            val continuations = ayat.map { it.second.split(' ') }
                .filter { it.size > k && it.take(k).joinToString(" ") == prefix }
                .map { it[k] }.toSet()
            if (continuations.size != 1) continue
            val pool = ayat.flatMap { it.second.split(' ') }.filter { isWord(it) && it != answer }.distinct()
            if (pool.size < 3) continue
            val choices = (pool.shuffled(random).take(3) + answer).shuffled(random)
            return Exercise.Choice(
                prompt = "Which word comes next?",
                big = prefix,
                caption = "${nameOf(surah)} · ayah ${ref.aya}",
                options = choices.map { Option(it, arabic = true) },
                correct = choices.indexOf(answer),
                audio = Sound.Ayah(ref),
                audioAfterAnswer = true,
                explanation = "Ayah ${ref.aya} of ${nameOf(surah)}",
                skill = Skills.surah(surah),
            )
        }
        return null
    }

    /** "Listen. Which ayah is this?" */
    fun listenWhich(surah: Int): Exercise.Choice? {
        val ayat = ayat(surah)
        val (ref, text) = ayat.random(random)
        if (ayat.count { it.second == text } > 1) return null
        val wrong = otherAyat(surah, setOf(text)).take(3)
        if (wrong.size < 2) return null
        val choices = (wrong + text).shuffled(random)
        return Exercise.Choice(
            prompt = "Listen. Which ayah is this?",
            options = choices.map { Option(it, arabic = true) },
            correct = choices.indexOf(text),
            audio = Sound.Ayah(ref),
            autoPlay = true,
            explanation = "Ayah ${ref.aya} of ${nameOf(surah)}",
            skill = Skills.surah(surah),
        )
    }

    /** "Put these ayat in order", over runs of consecutive ayat. */
    fun orderChunks(surah: Int, maxItems: Int): List<Exercise.Order> {
        val ayat = ayat(surah)
        if (ayat.size < 3) return emptyList()
        val chunks = if (ayat.size <= maxItems) listOf(ayat)
        else (ayat.windowed(4, 3) + listOf(ayat.takeLast(4))).distinct()
        return chunks.map { chunk ->
            val first = chunk.first().first.aya
            val last = chunk.last().first.aya
            Exercise.Order(
                prompt = "Put the ayat in order",
                items = chunk.map { it.second },
                caption = if (chunk.size == ayat.size) nameOf(surah) else "${nameOf(surah)} · ayat $first–$last",
                skill = Skills.surah(surah),
            )
        }
    }

    /** A token with letters in it (not a stand-alone pause mark). */
    private fun isWord(token: String) = token.any { it in 'ء'..'ي' }
}
