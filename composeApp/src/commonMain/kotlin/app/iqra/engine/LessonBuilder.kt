package app.iqra.engine

import app.iqra.data.Course
import app.iqra.data.CourseUnit
import app.iqra.data.Haraka
import app.iqra.data.Harakat
import app.iqra.data.Lesson
import app.iqra.data.LessonKind
import app.iqra.data.Letter
import app.iqra.data.Letters
import app.iqra.data.Opening
import app.iqra.data.QuranText
import app.iqra.data.QuranWord
import kotlin.random.Random

/** Turns a lesson from the course outline into a concrete, shuffled list of exercises. */
class LessonBuilder(private val quran: QuranText, private val random: Random = Random.Default) {

    fun build(lesson: Lesson): List<Exercise> {
        val unit = Course.unitOf(lesson.id)
        return when (lesson.kind) {
            LessonKind.Learn -> learn(unit)
            LessonKind.Practice -> practice(unit)
            LessonKind.Quran -> findInQuran(unit)
            LessonKind.VowelsLearn -> vowelsLearn()
            LessonKind.VowelsPractice -> vowelsPractice()
            LessonKind.Openings -> openings()
        }
    }

    private fun learn(unit: CourseUnit): List<Exercise> {
        val intros = unit.letters.flatMap { listOf(Exercise.LetterIntro(it), letterToName(it, unit)) }
        val review = unit.letters.shuffled(random).map { nameToLetter(it, unit) }
        return intros + review
    }

    private fun practice(unit: CourseUnit): List<Exercise> {
        val letters = unit.letters
        val questions = letters.flatMap { letter ->
            buildList {
                add(soundToLetter(letter, unit))
                if (letter.joins) add(formToLetter(letter, unit)) else add(letterToName(letter, unit))
                add(nameToLetter(letter, unit))
            }
        }.shuffled(random)
        val match = Exercise.Match(
            prompt = "Match each letter to its name",
            pairs = letters.take(4).map { it.char to it.name },
        )
        return questions.take(questions.size / 2) + match + questions.drop(questions.size / 2)
    }

    private fun findInQuran(unit: CourseUnit): List<Exercise> {
        val words = Course.wordSurahs.flatMap { quran.words(it) }
        val questions = unit.letters.flatMap { letter ->
            listOfNotNull(wordContaining(letter, words), containsYesNo(letter, words, random.nextBoolean()))
        }
        // Mix in a couple of questions about letters from earlier units.
        val earlier = (Course.lettersUpTo(unit) - unit.letters.toSet()).shuffled(random).take(2)
        val review = earlier.mapNotNull { wordContaining(it, words) }
        return (questions + review).shuffled(random)
    }

    private fun vowelsLearn(): List<Exercise> {
        val sample = listOf(Letters.ba, Letters.ta, Letters.mim)
        return Harakat.all.flatMap { h ->
            val examples = sample.map { it.char + h.mark to it.sound + h.sound }
            listOf(Exercise.VowelIntro(h, examples), readSyllable(sample.random(random), h))
        } + List(4) { readSyllable(consonants.random(random), Harakat.all.random(random)) }
    }

    private fun vowelsPractice(): List<Exercise> =
        List(10) { readSyllable(consonants.random(random), Harakat.all.random(random)) } +
            Exercise.Match(
                prompt = "Match each syllable to its sound",
                pairs = consonants.shuffled(random).take(4).map { letter ->
                    val h = Harakat.all.random(random)
                    letter.char + h.mark to letter.sound + h.sound
                },
            )

    private fun openings(): List<Exercise> = Course.openings.shuffled(random).flatMap { opening ->
        val others = (Course.openings - opening).shuffled(random).take(3)
        val choices = (others + opening).shuffled(random)
        val text = quran.text(opening.ref)
        val namesOf = { o: Opening -> o.letters.joinToString(" · ") { it.name } }
        val sura = "Surah ${opening.ref.sura}, ayah ${opening.ref.aya} (${quran.suraName(opening.ref.sura)})"
        listOf(
            Exercise.Choice(
                prompt = "Listen. Which opening is recited?",
                options = choices.map { Option(quran.text(it.ref), arabic = true) },
                correct = choices.indexOf(opening),
                audio = opening.ref,
                autoPlay = true,
                explanation = "${namesOf(opening)} — $sura",
            ),
            Exercise.Choice(
                prompt = "Which letters make up this opening?",
                big = text,
                caption = sura,
                options = choices.map { Option(namesOf(it), arabic = false) },
                correct = choices.indexOf(opening),
                audio = opening.ref,
            ),
        )
    }.take(10)

    // --- Question templates -------------------------------------------------------------

    private val consonants = Letters.all - Letters.alif

    /** Pick up to [n] other letters, preferring ones from the same unit (they look alike). */
    private fun distractors(letter: Letter, unit: CourseUnit?, n: Int = 3): List<Letter> {
        val near = unit?.letters.orEmpty().filter { it != letter }.shuffled(random)
        val far = (Letters.all - near.toSet() - letter).shuffled(random)
        return (near + far).take(n)
    }

    private fun lettersChoice(
        prompt: String,
        letter: Letter,
        unit: CourseUnit?,
        big: String?,
        bigArabic: Boolean,
        label: (Letter) -> Option,
        caption: String? = null,
    ): Exercise.Choice {
        val choices = (distractors(letter, unit) + letter).shuffled(random)
        return Exercise.Choice(
            prompt = prompt,
            big = big,
            bigArabic = bigArabic,
            caption = caption,
            options = choices.map(label),
            correct = choices.indexOf(letter),
            explanation = "${letter.char}  is ${letter.name}: ${letter.hint}",
        )
    }

    private fun letterToName(letter: Letter, unit: CourseUnit?) = lettersChoice(
        "What is this letter called?", letter, unit, big = letter.char, bigArabic = true,
        label = { Option(it.name, arabic = false) },
    )

    private fun nameToLetter(letter: Letter, unit: CourseUnit?) = lettersChoice(
        "Which one is ${letter.name}?", letter, unit, big = null, bigArabic = false,
        label = { Option(it.char, arabic = true) },
    )

    private fun soundToLetter(letter: Letter, unit: CourseUnit?) = lettersChoice(
        "Which letter makes this sound?", letter, unit, big = letter.hint.substringBefore(" —"), bigArabic = false,
        label = { Option(it.char, arabic = true) },
    )

    private fun formToLetter(letter: Letter, unit: CourseUnit?) = lettersChoice(
        "This is a letter at the start of a word. Which letter is it?", letter, unit,
        big = letter.initial, bigArabic = true,
        label = { Option(it.char, arabic = true) },
        caption = "Letters change shape when they join",
    )

    private fun readSyllable(letter: Letter, h: Haraka): Exercise.Choice {
        val right = letter.sound + h.sound
        val wrong = (Harakat.all - h).map { letter.sound + it.sound } +
            distractors(letter, null, 1).map { it.sound + h.sound }
        val choices = (wrong + right).shuffled(random)
        return Exercise.Choice(
            prompt = "How is this read?",
            big = letter.char + h.mark,
            options = choices.map { Option(it, arabic = false) },
            correct = choices.indexOf(right),
            explanation = "${letter.name} with ${h.name} is read \"$right\"",
        )
    }

    private fun wordContaining(letter: Letter, words: List<QuranWord>): Exercise.Choice? {
        val with = words.filter { Letters.appearsIn(letter, it.text) }
        val without = words.filterNot { Letters.appearsIn(letter, it.text) }.distinctBy { it.text }
        if (with.isEmpty() || without.size < 3) return null
        val answer = with.random(random)
        val choices = (without.shuffled(random).take(3) + answer).shuffled(random)
        return Exercise.Choice(
            prompt = "Which word contains ${letter.name}  ${letter.char} ?",
            options = choices.map { Option(it.text, arabic = true) },
            correct = choices.indexOf(answer),
            caption = "Words from Juz ʿAmma and Al-Fātiḥah",
            audio = answer.ref,
            explanation = "${answer.text}  — from ${quran.suraName(answer.ref.sura)}, ${answer.ref}",
        )
    }

    private fun containsYesNo(letter: Letter, words: List<QuranWord>, wantYes: Boolean): Exercise.Choice? {
        val pool = words.filter { Letters.appearsIn(letter, it.text) == wantYes }
        if (pool.isEmpty()) return null
        val word = pool.random(random)
        return Exercise.Choice(
            prompt = "Does this word contain ${letter.name}  ${letter.char} ?",
            big = word.text,
            caption = "${quran.suraName(word.ref.sura)} · ${word.ref}",
            options = listOf(Option("Yes", arabic = false), Option("No", arabic = false)),
            correct = if (wantYes) 0 else 1,
            audio = word.ref,
        )
    }
}
