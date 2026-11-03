package app.iqra.engine

import app.iqra.data.AyahRef
import app.iqra.data.Course
import app.iqra.data.Exams
import app.iqra.data.CourseUnit
import app.iqra.data.Haraka
import app.iqra.data.Hifz
import app.iqra.data.Harakat
import app.iqra.data.Lesson
import app.iqra.data.LessonKind
import app.iqra.data.Letter
import app.iqra.data.LetterAudio
import app.iqra.data.Letters
import app.iqra.data.Opening
import app.iqra.data.Piece
import app.iqra.data.Pieces
import app.iqra.data.QuranText
import app.iqra.data.QuranWord
import app.iqra.data.Rule
import app.iqra.data.Rules
import app.iqra.data.Sound
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Turns a lesson from the course outline into a concrete, shuffled list of exercises.
 * Listening questions about single letters are only made for letters whose recordings are in [audio].
 */
class LessonBuilder(
    private val quran: QuranText,
    private val random: Random = Random.Default,
    private val audio: LetterAudio = LetterAudio.none,
) {
    private val hifz = HifzBuilder(quran, random)


    fun build(lesson: Lesson): List<Exercise> {
        val unit = Course.unitOf(lesson.id)
        return when (lesson.kind) {
            LessonKind.Learn -> learn(unit)
            LessonKind.Practice -> practice(unit)
            LessonKind.Quran -> findInQuran(unit)
            LessonKind.VowelsLearn -> vowelsLearn()
            LessonKind.VowelsPractice -> vowelsPractice()
            LessonKind.Openings -> openings()
            LessonKind.RuleLearn -> ruleLearn(unit.rule!!)
            LessonKind.RulePractice -> rulePractice(unit.rule!!, Course.rulesUpTo(unit))
            LessonKind.RuleQuran -> ruleQuran(listOf(unit.rule!!), questions = 10)
            LessonKind.ReadingReview -> readingReview()
            LessonKind.QuranMarks -> ruleQuran(Rules.all, questions = 12)
            LessonKind.HifzLearn -> hifz.learn(unit.surah!!)
            LessonKind.HifzRecall -> hifz.recall(unit.surah!!)
            LessonKind.Exam -> exam(lesson.id)
        }
    }

    /**
     * A practice session for the given skills (see [Skills]): a few questions on each, mixed.
     * Unknown skills are ignored.
     */
    fun practice(skills: List<String>, questions: Int = 12): List<Exercise> {
        if (skills.isEmpty()) return emptyList()
        val out = mutableListOf<Exercise>()
        var i = 0
        while (out.size < questions && i < questions * 3) {
            skillQuestion(skills[i % skills.size])?.let(out::add)
            i++
        }
        return out.shuffled(random)
    }

    private fun skillQuestion(skill: String): Exercise.Choice? {
        val (kind, key) = skill.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "letter" -> {
                val letter = Letters.all.firstOrNull { it.key == key } ?: return null
                val unit = Course.units.firstOrNull { letter in it.letters }
                val options = listOfNotNull(
                    letterToName(letter, unit), nameToLetter(letter, unit), soundToLetter(letter, unit),
                    if (letter.joins) formToLetter(letter, unit) else null,
                    hearName(letter, unit), hearSound(letter, unit),
                )
                options.random(random)
            }
            "vowel" -> {
                val h = Harakat.all.firstOrNull { it.sound == key } ?: return null
                val letter = consonants.random(random)
                hearSyllable(letter, h)?.takeIf { random.nextBoolean() } ?: readSyllable(letter, h)
            }
            "rule" -> {
                val rule = Rules.all.firstOrNull { it.key == key } ?: return null
                if (random.nextInt(3) == 0) whichIsRead(rule) else readWord(rule)
            }
            "surah" -> key.toIntOrNull()?.takeIf { it in 1..114 }?.let { hifz.reviewQuestion(it) }
            else -> null
        }
    }

    private fun learn(unit: CourseUnit): List<Exercise> {
        val intros = unit.letters.flatMap { letter ->
            listOfNotNull(
                Exercise.LetterIntro(letter, name = audio.name(letter), sound = audio.sound(letter)),
                letterToName(letter, unit),
                hearSound(letter, unit),
            )
        }
        val review = unit.letters.shuffled(random).flatMap { listOfNotNull(nameToLetter(it, unit), hearName(it, unit)) }
        return intros + review.shuffled(random)
    }

    private fun practice(unit: CourseUnit): List<Exercise> {
        val letters = unit.letters
        val questions = letters.flatMap { letter ->
            buildList {
                add(hearSound(letter, unit) ?: soundToLetter(letter, unit))
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
            val examples = sample.map { syllable(it, h) }
            listOf(Exercise.RuleIntro("NEW VOWEL MARK", h.name, h.hint, examples), readSyllable(sample.random(random), h))
        } + List(4) { readSyllable(consonants.random(random), Harakat.all.random(random)) }
    }

    private fun vowelsPractice(): List<Exercise> =
        List(10) {
            val letter = consonants.random(random)
            val h = Harakat.all.random(random)
            // Half the questions are listening ones, where the syllable has been recorded.
            (if (it % 2 == 0) hearSyllable(letter, h) else null) ?: readSyllable(letter, h)
        } +
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
                audio = Sound.Ayah(opening.ref),
                autoPlay = true,
                explanation = "${namesOf(opening)} — $sura",
            ),
            Exercise.Choice(
                prompt = "Which letters make up this opening?",
                big = text,
                caption = sura,
                options = choices.map { Option(namesOf(it), arabic = false) },
                correct = choices.indexOf(opening),
                audio = Sound.Ayah(opening.ref),
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
        listenTo: Sound? = null,
    ): Exercise.Choice {
        val choices = (distractors(letter, unit) + letter).shuffled(random)
        return Exercise.Choice(
            prompt = prompt,
            big = big,
            bigArabic = bigArabic,
            bigSound = if (big != null && bigArabic) audio.name(letter) else null,
            caption = caption,
            options = choices.map { label(it).copy(sound = audio.name(it)) },
            correct = choices.indexOf(letter),
            // A listening question plays its clip straight away; any other question offers the
            // letter's name after it is answered, so every answer is also heard.
            audio = listenTo ?: audio.name(letter),
            autoPlay = listenTo != null,
            audioAfterAnswer = listenTo == null,
            explanation = "${letter.char}  is ${letter.name}: ${letter.hint}",
            skill = Skills.letter(letter),
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

    /** "Listen. Which letter is this?" — the letter's name is played. */
    private fun hearName(letter: Letter, unit: CourseUnit?): Exercise.Choice? {
        val clip = audio.name(letter) ?: return null
        return lettersChoice(
            "Listen. Which letter is this?", letter, unit, big = null, bigArabic = false,
            label = { Option(it.char, arabic = true) }, listenTo = clip,
        )
    }

    /** "Listen. Which letter makes this sound?" — the letter read with fatḥa ("ba") is played. */
    private fun hearSound(letter: Letter, unit: CourseUnit?): Exercise.Choice? {
        val clip = audio.sound(letter) ?: return null
        return lettersChoice(
            "Listen. Which letter makes this sound?", letter, unit, big = null, bigArabic = false,
            label = { Option(it.char, arabic = true) }, listenTo = clip,
        ).copy(explanation = "${letter.char}َ  is read \"${letter.sound}a\" — ${letter.hint}")
    }

    private fun syllable(letter: Letter, h: Haraka) =
        Syllable(letter.char + h.mark, letter.sound + h.sound, audio.syllable(letter, h))

    /** "Listen. Which did you hear?" — choose between the same letter with other vowels, and a look-alike. */
    private fun hearSyllable(letter: Letter, h: Haraka): Exercise.Choice? {
        val clip = audio.syllable(letter, h) ?: return null
        val right = syllable(letter, h)
        val wrong = (Harakat.all - h).map { syllable(letter, it) } +
            distractors(letter, Course.units.firstOrNull { letter in it.letters }, 1)
                .filter { it != Letters.alif }.map { syllable(it, h) }
        val choices = (wrong + right).shuffled(random)
        return Exercise.Choice(
            prompt = "Listen. Which did you hear?",
            options = choices.map { Option(it.arabic, arabic = true) },
            correct = choices.indexOf(right),
            audio = clip,
            autoPlay = true,
            explanation = "${right.arabic}  is read \"${right.latin}\": ${letter.name} with ${h.name}",
            skill = Skills.vowel(h),
        )
    }

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
            audio = audio.syllable(letter, h),
            audioAfterAnswer = true,
            explanation = "${letter.name} with ${h.name} is read \"$right\"",
            skill = Skills.vowel(h),
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
            audio = Sound.Ayah(answer.ref),
            explanation = "${answer.text}  — from ${quran.suraName(answer.ref.sura)}, ${answer.ref}",
            skill = Skills.letter(letter),
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
            audio = Sound.Ayah(word.ref),
            skill = Skills.letter(letter),
        )
    }

    // --- Reading rules ----------------------------------------------------------------------

    /** One syllable of a practice word, kept symbolic so it can be varied for wrong options. */
    private sealed interface Spec {
        data class Open(val l: Letter, val h: Haraka) : Spec
        data class Closed(val l: Letter) : Spec
        data class Doubled(val l: Letter, val h: Haraka) : Spec
        data class Tanwin(val l: Letter, val h: Haraka) : Spec
        data class Long(val l: Letter, val h: Haraka) : Spec
    }

    private fun piece(s: Spec): Piece = when (s) {
        is Spec.Open -> Pieces.open(s.l, s.h)
        is Spec.Closed -> Pieces.closed(s.l)
        is Spec.Doubled -> Pieces.doubled(s.l, s.h)
        is Spec.Tanwin -> Pieces.tanwin(s.l, s.h)
        is Spec.Long -> Pieces.long(s.l, s.h)
    }

    private fun word(specs: List<Spec>) = Pieces.word(specs.map(::piece))

    /** Plausible misreadings of one syllable: another vowel, or ignoring the mark. */
    private fun misreadings(s: Spec): List<Spec> = when (s) {
        is Spec.Open -> (Harakat.all - s.h).map { Spec.Open(s.l, it) }
        is Spec.Closed -> listOf(Spec.Open(s.l, Harakat.fatha), Spec.Open(s.l, Harakat.kasra), Spec.Open(s.l, Harakat.damma))
        is Spec.Doubled -> listOf(Spec.Open(s.l, s.h)) + (Harakat.all - s.h).map { Spec.Doubled(s.l, it) }
        is Spec.Tanwin -> listOf(Spec.Open(s.l, s.h)) + (Harakat.all - s.h).map { Spec.Tanwin(s.l, it) }
        is Spec.Long -> listOf(Spec.Open(s.l, s.h)) + (Harakat.all - s.h).map { Spec.Long(s.l, it) }
    }

    /** Letters whose sound is one Latin character, so a doubled sound reads clearly ("bb", not "shsh"). */
    private val singleSound = consonants.filter { it.sound.length == 1 && it.sound != "ʿ" }

    /** Wāw and yā with sukūn after ḍamma / kasra are long vowels, so closed syllables avoid them. */
    private val closable = consonants - Letters.waw - Letters.ya

    private fun h() = Harakat.all.random(random)

    /** A short practice word that uses [rule]. */
    private fun specsFor(rule: Rule): List<Spec> {
        // Each letter at most once per word, and no ʿAyn: words like عَعْ read as "ʿaʿ",
        // which is hard to tell apart. ʿAyn is still practised in single syllables.
        val used = mutableSetOf<Letter>()
        val pick = { from: List<Letter> -> (from - Letters.ayn - used).random(random).also { used += it } }
        val c = { pick(consonants) }
        val end = { pick(closable) }
        return when (rule) {
            Rules.sukun -> if (random.nextInt(3) == 0) listOf(Spec.Open(c(), h()), Spec.Open(c(), h()), Spec.Closed(end()))
            else listOf(Spec.Open(c(), h()), Spec.Closed(end()))
            Rules.shadda -> listOf(Spec.Open(c(), h()), Spec.Doubled(pick(singleSound), h()))
            Rules.tanwin -> listOf(Spec.Open(c(), h()), Spec.Tanwin(c(), h()))
            else -> if (random.nextBoolean()) listOf(Spec.Long(c(), h()), Spec.Closed(end()))
            else listOf(Spec.Open(c(), h()), Spec.Long(c(), h()))
        }
    }

    /** The right word and up to three misread versions of it, all read differently. */
    private fun wordAndMisreadings(rule: Rule): Pair<Piece, List<Piece>> {
        val specs = specsFor(rule)
        val right = word(specs)
        val wrong = specs.indices.flatMap { i -> misreadings(specs[i]).map { alt -> word(specs.toMutableList().also { it[i] = alt }) } }
            .filter { it.latin != right.latin && it.arabic != right.arabic }
            .distinctBy { it.latin }.distinctBy { it.arabic }
            .shuffled(random).take(3)
        return right to wrong
    }

    private fun ruleNote(rule: Rule) = when (rule) {
        Rules.sukun -> "the letter with sukūn has no vowel"
        Rules.shadda -> "the letter with shadda is said twice"
        Rules.tanwin -> "tanwīn adds an \"n\""
        else -> "the vowel is stretched"
    }

    /** "How is this read?" — a practice word, Latin options. */
    private fun readWord(rule: Rule): Exercise.Choice {
        val (right, wrong) = wordAndMisreadings(rule)
        val choices = (wrong + right).shuffled(random)
        return Exercise.Choice(
            prompt = "How is this read?",
            big = right.arabic,
            options = choices.map { Option(it.latin, arabic = false) },
            correct = choices.indexOf(right),
            explanation = "${right.arabic}  is read \"${right.latin}\": ${ruleNote(rule)}",
            skill = Skills.rule(rule),
        )
    }

    /** "Which one is read …?" — Latin shown, Arabic options. */
    private fun whichIsRead(rule: Rule): Exercise.Choice {
        val (right, wrong) = wordAndMisreadings(rule)
        val choices = (wrong + right).shuffled(random)
        return Exercise.Choice(
            prompt = "Which one is read \"${right.latin}\"?",
            options = choices.map { Option(it.arabic, arabic = true) },
            correct = choices.indexOf(right),
            explanation = "${right.arabic}  is read \"${right.latin}\": ${ruleNote(rule)}",
            skill = Skills.rule(rule),
        )
    }

    private fun wordsMatch(rules: List<Rule>): Exercise.Match {
        val words = mutableListOf<Piece>()
        while (words.size < 4) {
            val w = word(specsFor(rules.random(random)))
            if (words.none { it.latin == w.latin || it.arabic == w.arabic }) words += w
        }
        return Exercise.Match("Match each word to how it is read", words.map { it.arabic to it.latin })
    }

    private fun ruleExamples(rule: Rule): List<Syllable> = with(Letters) {
        val pieces = when (rule) {
            Rules.sukun -> listOf(
                Pieces.word(listOf(Pieces.open(mim, Harakat.fatha), Pieces.closed(kaf))),
                Pieces.word(listOf(Pieces.open(qaf, Harakat.damma), Pieces.closed(lam))),
                Pieces.word(listOf(Pieces.open(mim, Harakat.kasra), Pieces.closed(nun))),
            )
            Rules.shadda -> listOf(
                Pieces.word(listOf(Pieces.open(ra, Harakat.fatha), Pieces.doubled(ba, Harakat.fatha))),
                Pieces.word(listOf(Pieces.open(ta, Harakat.damma), Pieces.doubled(mim, Harakat.fatha))),
                Pieces.word(listOf(Pieces.open(kaf, Harakat.damma), Pieces.doubled(lam, Harakat.damma))),
            )
            Rules.tanwin -> Harakat.all.map { Pieces.tanwin(ba, it) }
            else -> Harakat.all.map { Pieces.long(ba, it) }
        }
        pieces.map { Syllable(it.arabic, it.latin) }
    }

    private fun ruleLearn(rule: Rule): List<Exercise> =
        listOf(Exercise.RuleIntro("NEW RULE", rule.name, rule.hint, ruleExamples(rule))) +
            List(6) { readWord(rule) } + List(2) { whichIsRead(rule) }

    private fun rulePractice(rule: Rule, soFar: List<Rule>): List<Exercise> {
        val earlier = soFar - rule
        val main = List(7) { readWord(rule) } + List(3) { whichIsRead(rule) }
        val review = earlier.shuffled(random).take(2).map { readWord(it) }
        val questions = (main + review).shuffled(random)
        return questions.take(5) + wordsMatch(listOf(rule)) + questions.drop(5)
    }

    private fun readingReview(): List<Exercise> {
        val questions = List(12) {
            val rule = Rules.all.random(random)
            if (it % 4 == 3) whichIsRead(rule) else readWord(rule)
        }
        return questions.take(6) + wordsMatch(Rules.all) + questions.drop(6)
    }

    private fun ruleQuestionName(rule: Rule) = when (rule) {
        Rules.long -> "a long vowel"
        else -> "a ${rule.name.lowercase()}"
    }

    /** "Which word has a shadda?" and "Does this word have a shadda?" with real Quran words. */
    private fun ruleQuran(rules: List<Rule>, questions: Int): List<Exercise> {
        val words = Course.wordSurahs.flatMap { quran.words(it) }.distinctBy { it.text }
        return List(questions) { n ->
            val rule = rules[n % rules.size]
            val with = words.filter { rule.inWord(it.text) }
            val without = words.filter { rule.clearlyWithout(it.text) }
            if (n % 3 == 2) {
                val wantYes = random.nextBoolean()
                val word = (if (wantYes) with else without).random(random)
                Exercise.Choice(
                    prompt = "Does this word have ${ruleQuestionName(rule)}?",
                    big = word.text,
                    caption = "${quran.suraName(word.ref.sura)} · ${word.ref}",
                    options = listOf(Option("Yes", arabic = false), Option("No", arabic = false)),
                    correct = if (wantYes) 0 else 1,
                    audio = Sound.Ayah(word.ref),
                    explanation = rule.hint,
                    skill = Skills.rule(rule),
                )
            } else {
                val answer = with.random(random)
                val choices = (without.shuffled(random).take(3) + answer).shuffled(random)
                Exercise.Choice(
                    prompt = "Which word has ${ruleQuestionName(rule)}?",
                    options = choices.map { Option(it.text, arabic = true) },
                    correct = choices.indexOf(answer),
                    caption = "Words from Juz ʿAmma and Al-Fātiḥah",
                    audio = Sound.Ayah(answer.ref),
                    explanation = "${answer.text}  — from ${quran.suraName(answer.ref.sura)}, ${answer.ref}",
                    skill = Skills.rule(rule),
                )
            }
        }.shuffled(random)
    }
    // --- Exams ---------------------------------------------------------------------------

    /**
     * An end-of-stage exam of [size] questions: half ordinary quiz questions, a fifth harder
     * ones, and the rest voice questions, always at least one.
     */
    fun exam(examId: String, size: Int = 10): List<Exercise> {
        val skills = examSkills(examId)
        val voices = examVoices(examId).shuffled(random)
        val hardCount = (size * 0.2).roundToInt()
        val voiceCount = maxOf(1, size - (size * 0.5).roundToInt() - hardCount).coerceAtMost(voices.size)
        val quizCount = size - hardCount - voiceCount
        val quiz = distinctQuestions(quizCount) { skillQuestion(skills.random(random)) }
        val hard = distinctQuestions(hardCount) { hardQuestion(skills.random(random)) }
        return (quiz + hard + voices.take(voiceCount)).shuffled(random)
    }

    /** Up to [n] questions from [make], skipping repeats of the same prompt and answer. */
    private fun distinctQuestions(n: Int, make: () -> Exercise.Choice?): List<Exercise.Choice> {
        val out = mutableListOf<Exercise.Choice>()
        var tries = 0
        while (out.size < n && tries++ < n * 10) {
            val q = make() ?: continue
            if (out.none { it.prompt == q.prompt && it.options[it.correct] == q.options[q.correct] }) out += q
        }
        return out
    }

    private fun examSkills(examId: String): List<String> = when (examId) {
        Exams.LETTERS -> Letters.all.map(Skills::letter)
        Exams.VOWELS -> Harakat.all.map(Skills::vowel)
        Exams.READING -> Rules.all.map(Skills::rule)
        else -> Exams.hifzGroupOf(examId)?.map(Skills::surah) ?: error("Unknown exam $examId")
    }

    /** Harder versions: look-alike letters in joined forms, reading with similar options, next word. */
    private fun hardQuestion(skill: String): Exercise.Choice? {
        val (kind, key) = skill.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "letter" -> Letters.all.firstOrNull { it.key == key }?.let(::lookAlikeForm)
            "vowel" -> Harakat.all.firstOrNull { it.sound == key }?.let { whichSyllable(consonants.random(random), it) }
            "rule" -> Rules.all.firstOrNull { it.key == key }?.let(::whichIsRead)
            "surah" -> key.toIntOrNull()?.let { hifz.nextWord(it) ?: hifz.reviewQuestion(it) }
            else -> null
        }
    }

    /** Letters that differ only by dots or small details. */
    private val lookAlikes: List<List<Letter>> = with(Letters) {
        listOf(
            listOf(ba, ta, tha, nun, ya), listOf(jim, haa, kha), listOf(dal, dhal), listOf(ra, zay),
            listOf(sin, shin), listOf(sad, dad), listOf(taa, zaa), listOf(ayn, ghayn), listOf(fa, qaf),
        )
    }

    /** "Which letter is this, in the middle of a word?" with look-alikes as the other answers. */
    private fun lookAlikeForm(letter: Letter): Exercise.Choice {
        val family = lookAlikes.firstOrNull { letter in it }.orEmpty() - letter
        val others = (family.shuffled(random) + (Letters.all - family.toSet() - letter).shuffled(random)).take(3)
        val choices = (others + letter).shuffled(random)
        val (where, form) = if (letter.joins) "in the middle of a word" to letter.medial else "at the end of a word" to letter.final
        return Exercise.Choice(
            prompt = "Which letter is this, $where?",
            big = form,
            options = choices.map { Option(it.char, arabic = true, sound = audio.name(it)) },
            correct = choices.indexOf(letter),
            audio = audio.name(letter),
            audioAfterAnswer = true,
            explanation = "${letter.char}  is ${letter.name}: ${letter.hint}",
            skill = Skills.letter(letter),
        )
    }

    /** "Which one is read "bi"?" — the same letter with every vowel, and a look-alike. */
    private fun whichSyllable(letter: Letter, h: Haraka): Exercise.Choice {
        val right = syllable(letter, h)
        val lookAlike = lookAlikes.firstOrNull { letter in it }?.minus(letter)?.randomOrNull(random)
            ?: (consonants - letter).random(random)
        val wrong = (Harakat.all - h).map { syllable(letter, it) } + syllable(lookAlike, h)
        val choices = (wrong + right).shuffled(random)
        return Exercise.Choice(
            prompt = "Which one is read \"${right.latin}\"?",
            options = choices.map { Option(it.arabic, arabic = true, sound = it.sound) },
            correct = choices.indexOf(right),
            explanation = "${right.arabic}  is read \"${right.latin}\": ${letter.name} with ${h.name}",
            skill = Skills.vowel(h),
        )
    }

    private fun examVoices(examId: String): List<Exercise.Voice> {
        val voices = when (examId) {
            Exams.LETTERS -> letterNameVoices()
            Exams.VOWELS -> openingVoices()
            Exams.READING -> shortAyahVoices()
            else -> hifzVoices(Exams.hifzGroupOf(examId).orEmpty())
        }
        // Without the letter recordings the openings are recited instead, so every exam has a voice question.
        return voices.ifEmpty { openingVoices() }
    }

    private fun letterNameVoices(): List<Exercise.Voice> = Letters.all.mapNotNull { letter ->
        val target = audio.name(letter) ?: return@mapNotNull null
        // Decoys should sound clearly different, so none come from the letter's look-alike group.
        val family = lookAlikes.firstOrNull { letter in it }.orEmpty()
        val decoys = (Letters.all - family.toSet() - letter).shuffled(random).mapNotNull { audio.name(it) }.take(3)
        Exercise.Voice(
            prompt = "Say this letter's name",
            big = letter.char,
            caption = null,
            target = target,
            decoys = decoys,
            reveal = letter.name,
            skill = Skills.letter(letter),
        )
    }

    private fun openingVoices(): List<Exercise.Voice> = Course.openings.map { opening ->
        Exercise.Voice(
            prompt = "Recite this opening",
            big = quran.text(opening.ref),
            caption = "Surah ${opening.ref.sura}, ayah ${opening.ref.aya}",
            target = Sound.Ayah(opening.ref),
            decoys = (Course.openings - opening).shuffled(random).take(3).map { Sound.Ayah(it.ref) },
            reveal = opening.letters.joinToString(" · ") { it.name },
        )
    }

    /** Ayat of Juz Amma with at most three words, to read aloud. */
    private val shortAyat: List<AyahRef> by lazy {
        (78..114).flatMap { quran.surah(it) }
            .filter { (_, text) -> text.split(' ').count { w -> w.any { it in 'ء'..'ي' } } <= 3 }
            .map { it.first }
    }

    private fun shortAyahVoices(): List<Exercise.Voice> = shortAyat.shuffled(random).take(8).map { ref ->
        Exercise.Voice(
            prompt = "Read this ayah aloud",
            big = quran.text(ref),
            caption = "${quran.suraName(ref.sura)} · $ref",
            target = Sound.Ayah(ref),
            decoys = (shortAyat - ref).shuffled(random).take(3).map { Sound.Ayah(it) },
        )
    }

    /** Recite the first ayah of each surah in the group from memory. */
    private fun hifzVoices(surahs: List<Int>): List<Exercise.Voice> = surahs.map { s ->
        val name = Hifz.surahs.firstOrNull { it.number == s }?.name ?: quran.suraName(s)
        Exercise.Voice(
            prompt = "Recite the first ayah of $name from memory",
            big = null,
            caption = name,
            target = Sound.Ayah(AyahRef(s, 1)),
            decoys = (surahs - s).shuffled(random).take(3).map { Sound.Ayah(AyahRef(it, 1)) },
            reveal = quran.text(AyahRef(s, 1)),
            skill = Skills.surah(s),
        )
    }
}
