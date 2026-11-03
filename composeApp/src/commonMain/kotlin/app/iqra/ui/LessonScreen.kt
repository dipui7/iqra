package app.iqra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.iqra.data.Exams
import app.iqra.data.Sound
import app.iqra.stopRecording
import app.iqra.todayEpochDay
import app.iqra.engine.Exercise
import app.iqra.engine.Feedback
import app.iqra.engine.LESSON_XP
import app.iqra.engine.LessonSession
import app.iqra.engine.MAX_HEARTS
import app.iqra.engine.Option
import app.iqra.engine.PERFECT_BONUS_XP
import app.iqra.engine.PRACTICE_XP
import app.iqra.engine.ProgressStore
import app.iqra.playAudio
import app.iqra.playAudioBytes
import app.iqra.resources.Res
import app.iqra.stopAudio
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LessonResult(
    val title: String,
    val passed: Boolean,
    val xp: Int,
    val accuracy: Int,
    /** A practice session, which also gives a heart back if any were missing. */
    val practice: Boolean = false,
    val heartGained: Boolean = false,
    /** An exam: [accuracy] is the score, and [coinsEarned] if it was passed for the first time. */
    val exam: Boolean = false,
    val coinsEarned: Int = 0,
)

/** Share of exam questions that must be answered right to pass. */
const val EXAM_PASS_PERCENT = 80

@Composable
fun LessonScreen(
    lessonId: String,
    title: String,
    exercises: List<Exercise>,
    store: ProgressStore,
    onClose: () -> Unit,
    onFinished: (LessonResult) -> Unit,
    /** A practice session: mistakes cost no hearts, and finishing gives one back. */
    practice: Boolean = false,
) {
    val exam = Exams.isExam(lessonId)
    val unlimited = remember { store.progress.hasUnlimitedHearts(todayEpochDay()) }
    val session = remember {
        LessonSession(
            exercises, store.progress.hearts,
            onMistake = store::loseHeart,
            onAnswer = { skill, right -> skill?.let { store.recordAnswer(it, right) } },
            heartsMatter = !practice && !exam && !unlimited,
            exam = exam,
        )
    }

    fun finish(passed: Boolean) {
        stopAudio()
        stopRecording()
        if (exam) {
            val score = if (session.gradedCount == 0) 0 else 100 * session.rightCount / session.gradedCount
            val ok = score >= EXAM_PASS_PERCENT
            val firstPass = ok && lessonId !in store.progress.passedExams
            // A stage exam is worth two lessons' XP; a unit check one.
            val xp = if (!ok) 0 else if (Exams.isStageExam(lessonId)) LESSON_XP * 2 else LESSON_XP
            if (ok) store.passExam(lessonId, xp)
            onFinished(LessonResult(title, ok, xp, score, exam = true, coinsEarned = if (firstPass) Exams.coinsFor(lessonId) else 0))
            return
        }
        val questions = exercises.count { it !is Exercise.LetterIntro && it !is Exercise.RuleIntro && it !is Exercise.AyahIntro }
        val accuracy = if (questions == 0) 100 else (100 * questions / (questions + session.mistakes))
        val base = if (practice) PRACTICE_XP else LESSON_XP
        val xp = if (passed) base + if (session.mistakes == 0) PERFECT_BONUS_XP else 0 else 0
        val heartGained = passed && practice && store.progress.hearts < MAX_HEARTS
        if (passed) {
            if (practice) store.completePractice(xp) else store.completeLesson(lessonId, xp)
        }
        onFinished(LessonResult(title, passed, xp, accuracy, practice, heartGained))
    }

    LaunchedEffect(session.finished) { if (session.finished) finish(passed = true) }
    // In a matching exercise mistakes don't show a feedback bar, so end the lesson right away.
    LaunchedEffect(session.hearts) {
        if (session.outOfHearts && session.current is Exercise.Match) finish(passed = false)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            session,
            label = when {
                exam -> "Exam"
                practice -> "Practice"
                else -> null
            },
            onClose = { stopAudio(); stopRecording(); onClose() },
        )
        val exercise = session.current ?: return@Column
        key(session.index) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                when (exercise) {
                    is Exercise.LetterIntro -> LetterIntro(exercise)
                    is Exercise.RuleIntro -> RuleIntro(exercise)
                    is Exercise.Choice -> ChoiceQuestion(exercise, session)
                    is Exercise.Match -> MatchQuestion(exercise, session)
                    is Exercise.AyahIntro -> AyahIntro(exercise)
                    is Exercise.Order -> OrderQuestion(exercise, session)
                    is Exercise.Voice -> VoiceQuestion(exercise, session)
                    is Exercise.Trace -> TraceQuestion(exercise, session)
                }
            }
        }
        BottomBar(exercise, session, onContinueAfterFeedback = {
            if (session.outOfHearts) finish(passed = false) else session.next()
        })
    }
}

@Composable
private fun TopBar(session: LessonSession, label: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Quit lesson", tint = palette.secondary) }
        LinearProgressIndicator(
            progress = { session.progress },
            modifier = Modifier.weight(1f).height(10.dp),
            color = palette.accent,
            trackColor = palette.separator,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(14.dp))
        if (session.usesHearts) {
            Icon(Icons.Rounded.Favorite, "Hearts", tint = palette.heart, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text("${session.hearts}", style = MaterialTheme.typography.titleMedium, color = palette.heart)
        } else if (label != null) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = palette.accent)
        } else {
            // Unlimited hearts (bought with coins, or the developer switch).
            Icon(Icons.Rounded.Favorite, "Unlimited hearts", tint = palette.heart, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text("∞", style = MaterialTheme.typography.titleMedium, color = palette.heart)
        }
    }
}

// --- Teaching cards --------------------------------------------------------------------

@Composable
private fun LetterIntro(intro: Exercise.LetterIntro) {
    val letter = intro.letter
    val name = rememberSoundPlayer(intro.name)
    val tapToHear = if (name != null) Modifier.clickable { name.toggle() } else Modifier
    Label("NEW LETTER")
    Card {
        Text(letter.char, style = arabicStyle(110, lineFactor = 1.4), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().then(tapToHear))
        Text(letter.name, style = MaterialTheme.typography.titleLarge, color = palette.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(letter.hint, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    if (intro.name != null || intro.sound != null) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intro.name?.let { AudioButton(it, "Its name") }
            intro.sound?.let { AudioButton(it, "Its sound: ${letter.sound}a") }
        }
    }
    Spacer(Modifier.height(16.dp))
    Label(if (letter.joins) "ITS SHAPES IN A WORD" else "DOES NOT JOIN TO THE NEXT LETTER")
    val forms = if (letter.joins) {
        listOf("Alone" to letter.char, "Start" to letter.initial, "Middle" to letter.medial, "End" to letter.final)
    } else {
        listOf("Alone" to letter.char, "End" to letter.final)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        forms.forEach { (label, form) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.card).then(tapToHear).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(form, style = arabicStyle(34))
                Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
            }
        }
    }
}

@Composable
private fun RuleIntro(intro: Exercise.RuleIntro) {
    Label(intro.label)
    Card {
        Text(intro.name, style = MaterialTheme.typography.titleLarge, color = palette.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(intro.hint, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        intro.examples.forEach { syllable ->
            val play = rememberSoundPlayer(syllable.sound)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.card)
                    .then(if (play != null) Modifier.clickable { play.toggle() } else Modifier)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(syllable.arabic, style = arabicStyle(48))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(syllable.latin, style = MaterialTheme.typography.titleMedium, color = palette.accent)
                    if (play != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (play.playing) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            "Play ${syllable.latin}", tint = palette.accent, modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahIntro(intro: Exercise.AyahIntro) {
    Label("AYAH ${intro.ref.aya} OF ${intro.ayahCount} · ${intro.surahName.uppercase()}")
    Card {
        Text(
            intro.text,
            style = arabicStyle(if (intro.text.length > 60) 30 else 36, lineFactor = 2.0).copy(textDirection = TextDirection.Rtl),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    AudioButton(Sound.Ayah(intro.ref), "Listen again", autoPlay = true)
    Text(
        "Listen, then recite it aloud two or three times — looking, then with your eyes closed — before you continue.",
        color = palette.secondary,
        modifier = Modifier.padding(top = 14.dp),
    )
}

// --- Questions -------------------------------------------------------------------------

@Composable
private fun ChoiceQuestion(q: Exercise.Choice, session: LessonSession) {
    Text(q.prompt, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(vertical = 12.dp))
    if (q.big != null) {
        val bigPlayer = rememberSoundPlayer(q.bigSound)
        val answered = session.feedback != null
        Card {
            if (q.bigArabic) {
                if (bigPlayer != null && answered) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Icon(
                            if (bigPlayer.playing) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            "Tap the letter to hear it", tint = palette.accent, modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(q.big, style = arabicStyle(if (q.big.length <= 3) 96 else 48).copy(textDirection = TextDirection.Rtl), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().then(if (bigPlayer != null && answered) Modifier.clickable { bigPlayer.toggle() } else Modifier))
            } else {
                Text(q.big, style = MaterialTheme.typography.titleLarge, color = palette.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
            q.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.height(12.dp))
    } else {
        q.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.secondary, modifier = Modifier.padding(bottom = 8.dp)) }
    }
    if (q.audio != null) {
        // For "listen" questions the audio is the question; otherwise it is optional context.
        val label = when {
            q.autoPlay -> "Play again"
            q.audio is Sound.Ayah && q.big != null -> "Listen to the ayah"
            else -> "Listen"
        }
        val showNow = session.feedback != null || q.autoPlay || (q.big != null && !q.audioAfterAnswer)
        if (showNow) {
            AudioButton(q.audio, label, autoPlay = q.autoPlay)
            Spacer(Modifier.height(12.dp))
        }
    }

    val grid = q.options.size == 4 && q.options.all { it.arabic && it.label.length <= 3 }
    val optionCard: @Composable (Int, Modifier) -> Unit = { i, modifier ->
        OptionCard(q.options[i], i, q, session, modifier)
    }
    if (grid) {
        q.options.indices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { optionCard(it, Modifier.weight(1f)) }
            }
        }
    } else {
        q.options.indices.forEach { optionCard(it, Modifier.fillMaxWidth().padding(bottom = 10.dp)) }
    }
}

@Composable
private fun OptionCard(option: Option, i: Int, q: Exercise.Choice, session: LessonSession, modifier: Modifier) {
    val fb = session.feedback
    val selected = session.selected == i
    val player = rememberSoundPlayer(option.sound)
    val border = when {
        fb != null && i == q.correct -> palette.correct
        fb is Feedback.Wrong && selected -> palette.wrong
        selected -> palette.accent
        else -> palette.separator
    }
    val bg = when {
        fb != null && i == q.correct -> palette.correctSoft
        fb is Feedback.Wrong && selected -> palette.wrongSoft
        selected -> palette.accentSoft
        else -> palette.card
    }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(BorderStroke(2.dp, border), RoundedCornerShape(16.dp))
            // Before answering a tap selects; afterwards it plays the option (e.g. the letter's name).
            .clickable(enabled = fb == null || player != null) { if (fb == null) session.selected = i else player?.toggle() }
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (option.arabic) {
            val size = when {
                option.label.length <= 3 -> 44
                option.label.length <= 12 -> 32
                else -> 26
            }
            Text(option.label, style = arabicStyle(size).copy(textDirection = TextDirection.Rtl), textAlign = TextAlign.Center)
        } else {
            Text(option.label, style = MaterialTheme.typography.bodyLarge, color = palette.text, textAlign = TextAlign.Center)
        }
        if (fb != null && player != null) {
            Icon(
                if (player.playing) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, "Play",
                tint = palette.accent, modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
            )
        }
    }
}

@Composable
private fun OrderQuestion(q: Exercise.Order, session: LessonSession) {
    // Positions into q.items, shown shuffled; the learner taps them in order.
    val shuffled = remember { q.items.indices.shuffled() }
    val picked = remember { mutableStateListOf<Int>() }
    val fb = session.feedback
    LaunchedEffect(picked.size) {
        session.orderAnswer = if (picked.size == q.items.size) picked.map { q.items[it] } else null
    }

    Text(q.prompt, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(top = 12.dp))
    q.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.secondary, modifier = Modifier.padding(bottom = 8.dp)) }
    Text(
        if (fb is Feedback.Wrong) "The right order:" else "Tap the ayat from first to last. Tap a placed one to take it back.",
        style = MaterialTheme.typography.bodySmall, color = palette.secondary, modifier = Modifier.padding(vertical = 6.dp),
    )

    val placed: List<Int> = if (fb is Feedback.Wrong) q.items.indices.toList() else picked
    placed.forEachIndexed { n, i ->
        OrderTile(
            number = n + 1, text = q.items[i],
            state = if (fb == Feedback.Correct) palette.correct else palette.accent,
            enabled = fb == null,
        ) { picked.remove(i) }
    }
    if (fb == null) {
        if (picked.isNotEmpty() && picked.size < q.items.size) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = palette.separator)
        }
        shuffled.filter { it !in picked }.forEach { i ->
            OrderTile(number = null, text = q.items[i], state = palette.separator, enabled = true) { picked.add(i) }
        }
    }
}

@Composable
private fun OrderTile(number: Int?, text: String, state: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .border(BorderStroke(2.dp, state), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (number != null) state else palette.background), contentAlignment = Alignment.Center) {
            if (number != null) Text("$number", color = palette.onAccent, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = arabicStyle(if (text.length > 50) 22 else 26).copy(textDirection = TextDirection.Rtl),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MatchQuestion(q: Exercise.Match, session: LessonSession) {
    val left = remember { q.pairs.map { it.first }.shuffled() }
    val right = remember { q.pairs.map { it.second }.shuffled() }
    val matched = remember { mutableStateListOf<String>() }
    var pickLeft by remember { mutableStateOf<String?>(null) }
    var pickRight by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(pickLeft, pickRight) {
        val l = pickLeft ?: return@LaunchedEffect
        val r = pickRight ?: return@LaunchedEffect
        if (q.pairs.contains(l to r)) {
            matched += l
            pickLeft = null
            pickRight = null
            if (matched.size == q.pairs.size) session.markSolved(showFeedback = true)
        } else {
            wrong = l to r
            session.registerMistake()
            delay(600)
            wrong = null
            pickLeft = null
            pickRight = null
        }
    }

    Text(q.prompt, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(vertical = 12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            left.forEach { item ->
                MatchTile(item, arabic = true, done = item in matched, picked = pickLeft == item, wrong = wrong?.first == item) {
                    if (wrong == null) pickLeft = item
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            right.forEach { item ->
                val done = q.pairs.any { it.second == item && it.first in matched }
                MatchTile(item, arabic = false, done = done, picked = pickRight == item, wrong = wrong?.second == item) {
                    if (wrong == null) pickRight = item
                }
            }
        }
    }
}

@Composable
private fun MatchTile(text: String, arabic: Boolean, done: Boolean, picked: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val border = when {
        wrong -> palette.wrong
        picked -> palette.accent
        else -> palette.separator
    }
    Box(
        Modifier.fillMaxWidth().height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (done) palette.background else if (wrong) palette.wrongSoft else palette.card)
            .border(BorderStroke(2.dp, if (done) Color.Transparent else border), RoundedCornerShape(16.dp))
            .clickable(enabled = !done, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (done) palette.separator else palette.text
        if (arabic) Text(text, style = arabicStyle(34, lineFactor = 1.5).copy(color = color))
        else Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

// --- Bottom bar ------------------------------------------------------------------------

@Composable
private fun BottomBar(exercise: Exercise, session: LessonSession, onContinueAfterFeedback: () -> Unit) {
    val fb = session.feedback
    val bg = when (fb) {
        Feedback.Correct -> palette.correctSoft
        is Feedback.Wrong -> palette.wrongSoft
        null -> palette.background
    }
    Column(Modifier.fillMaxWidth().background(bg).padding(20.dp)) {
        AnimatedVisibility(visible = fb != null, enter = slideInVertically { it / 2 }) {
            Column(Modifier.padding(bottom = 14.dp)) {
                val good = fb == Feedback.Correct
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null,
                        tint = if (good) palette.correct else palette.wrong, modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (good) "Correct!" else "Not quite",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (good) palette.correct else palette.wrong,
                    )
                }
                if (fb is Feedback.Wrong && fb.answer != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Answer: ", color = palette.text)
                        if (fb.answer.arabic) Text(fb.answer.label, style = arabicStyle(28))
                        else Text(fb.answer.label, style = MaterialTheme.typography.titleMedium, color = palette.text)
                    }
                }
                (exercise as? Exercise.Choice)?.explanation?.let {
                    Text(it, color = palette.secondary, modifier = Modifier.padding(top = 4.dp))
                }
                (exercise as? Exercise.Voice)?.let { v ->
                    v.reveal?.let {
                        Text(it, style = if (v.big == null) arabicStyle(26) else MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AudioButton(v.target, "Hear the recording")
                }
            }
        }
        when {
            fb != null -> PrimaryButton(
                "Continue",
                color = if (fb == Feedback.Correct) palette.correct else palette.wrong,
                onClick = onContinueAfterFeedback,
            )
            exercise is Exercise.Choice -> PrimaryButton("Check", enabled = session.selected != null, onClick = session::check)
            exercise is Exercise.Match -> PrimaryButton("Match all pairs", enabled = false, onClick = {})
            exercise is Exercise.Order -> PrimaryButton("Check", enabled = session.orderAnswer != null, onClick = session::check)
            exercise is Exercise.Voice -> PrimaryButton("Record your answer above", enabled = false, onClick = {})
            exercise is Exercise.Trace -> Unit // its Check button is under the drawing
            else -> PrimaryButton("Continue", onClick = { session.markSolved(showFeedback = false) })
        }
    }
}

// --- Shared pieces ---------------------------------------------------------------------

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, color: Color = palette.accent, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = palette.separator),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = palette.secondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card).padding(16.dp)) { content() }
}

/** Plays one [Sound] and tracks whether it is playing. */
class SoundPlayer internal constructor(
    private val sound: Sound,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    var playing by mutableStateOf(false)
        private set

    fun toggle() {
        if (playing) {
            playing = false
            stopAudio()
            return
        }
        playing = true
        when (val s = sound) {
            is Sound.Ayah -> playAudio(s.ref.audioUrl) { playing = false }
            is Sound.Clip -> launch {
                val bytes = runCatching { Res.readBytes(s.path) }.getOrNull()
                // Stopped while loading, or the file is missing: don't start.
                if (bytes == null || !playing) playing = false
                else playAudioBytes(bytes) { playing = false }
            }
        }
    }
}

@Composable
fun rememberSoundPlayer(sound: Sound?): SoundPlayer? {
    if (sound == null) return null
    val scope = rememberCoroutineScope()
    val player = remember(sound) { SoundPlayer(sound) { block -> scope.launch { block() } } }
    DisposableEffect(player) { onDispose { if (player.playing) stopAudio() } }
    return player
}

@Composable
fun AudioButton(sound: Sound, label: String, autoPlay: Boolean = false) {
    val player = rememberSoundPlayer(sound)!!
    val playing = player.playing
    LaunchedEffect(sound) { if (autoPlay) player.toggle() }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(palette.accentSoft).clickable { player.toggle() }
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = palette.onAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (playing) "Playing…" else label, color = palette.accent, style = MaterialTheme.typography.bodyMedium)
    }
}
