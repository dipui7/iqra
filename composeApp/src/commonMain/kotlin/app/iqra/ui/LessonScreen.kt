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
import app.iqra.data.AyahRef
import app.iqra.data.Letter
import app.iqra.engine.Exercise
import app.iqra.engine.Feedback
import app.iqra.engine.LESSON_XP
import app.iqra.engine.LessonSession
import app.iqra.engine.Option
import app.iqra.engine.PERFECT_BONUS_XP
import app.iqra.engine.ProgressStore
import app.iqra.playAudio
import app.iqra.stopAudio
import kotlinx.coroutines.delay

data class LessonResult(val lessonId: String, val passed: Boolean, val xp: Int, val accuracy: Int)

@Composable
fun LessonScreen(
    lessonId: String,
    exercises: List<Exercise>,
    store: ProgressStore,
    onClose: () -> Unit,
    onFinished: (LessonResult) -> Unit,
) {
    val session = remember { LessonSession(exercises, store.progress.hearts, onMistake = store::loseHeart) }

    fun finish(passed: Boolean) {
        stopAudio()
        val questions = exercises.count { it !is Exercise.LetterIntro && it !is Exercise.VowelIntro }
        val accuracy = if (questions == 0) 100 else (100 * questions / (questions + session.mistakes))
        val xp = if (passed) LESSON_XP + if (session.mistakes == 0) PERFECT_BONUS_XP else 0 else 0
        if (passed) store.completeLesson(lessonId, xp)
        onFinished(LessonResult(lessonId, passed, xp, accuracy))
    }

    LaunchedEffect(session.finished) { if (session.finished) finish(passed = true) }
    // In a matching exercise mistakes don't show a feedback bar, so end the lesson right away.
    LaunchedEffect(session.hearts) {
        if (session.outOfHearts && session.current is Exercise.Match) finish(passed = false)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(session, onClose = { stopAudio(); onClose() })
        val exercise = session.current ?: return@Column
        key(session.index) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                when (exercise) {
                    is Exercise.LetterIntro -> LetterIntro(exercise.letter)
                    is Exercise.VowelIntro -> VowelIntro(exercise)
                    is Exercise.Choice -> ChoiceQuestion(exercise, session)
                    is Exercise.Match -> MatchQuestion(exercise, session)
                }
            }
        }
        BottomBar(exercise, session, onContinueAfterFeedback = {
            if (session.outOfHearts) finish(passed = false) else session.next()
        })
    }
}

@Composable
private fun TopBar(session: LessonSession, onClose: () -> Unit) {
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
        Icon(Icons.Rounded.Favorite, "Hearts", tint = palette.heart, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(4.dp))
        Text("${session.hearts}", style = MaterialTheme.typography.titleMedium, color = palette.heart)
    }
}

// --- Teaching cards --------------------------------------------------------------------

@Composable
private fun LetterIntro(letter: Letter) {
    Label("NEW LETTER")
    Card {
        Text(letter.char, style = arabicStyle(110, lineFactor = 1.4), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(letter.name, style = MaterialTheme.typography.titleLarge, color = palette.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(letter.hint, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
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
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.card).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(form, style = arabicStyle(34))
                Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
            }
        }
    }
    letter.quranExample?.let { ref ->
        Spacer(Modifier.height(16.dp))
        AudioButton(ref, "Hear ${letter.name} recited (start of ${ref})")
    }
}

@Composable
private fun VowelIntro(intro: Exercise.VowelIntro) {
    Label("NEW VOWEL MARK")
    Card {
        Text(intro.haraka.name, style = MaterialTheme.typography.titleLarge, color = palette.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(intro.haraka.hint, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        intro.examples.forEach { (arabic, latin) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.card).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(arabic, style = arabicStyle(48))
                Text(latin, style = MaterialTheme.typography.titleMedium, color = palette.accent)
            }
        }
    }
}

// --- Questions -------------------------------------------------------------------------

@Composable
private fun ChoiceQuestion(q: Exercise.Choice, session: LessonSession) {
    Text(q.prompt, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(vertical = 12.dp))
    if (q.big != null) {
        Card {
            if (q.bigArabic) {
                Text(q.big, style = arabicStyle(if (q.big.length <= 3) 96 else 48).copy(textDirection = TextDirection.Rtl), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        val label = if (q.autoPlay) "Play again" else if (q.big != null) "Listen to the ayah" else "Listen"
        val showNow = q.autoPlay || session.feedback != null || q.big != null
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
            .clickable(enabled = fb == null) { session.selected = i }
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
                if (fb is Feedback.Wrong) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Answer: ", color = palette.text)
                        if (fb.answer.arabic) Text(fb.answer.label, style = arabicStyle(28))
                        else Text(fb.answer.label, style = MaterialTheme.typography.titleMedium, color = palette.text)
                    }
                }
                (exercise as? Exercise.Choice)?.explanation?.let {
                    Text(it, color = palette.secondary, modifier = Modifier.padding(top = 4.dp))
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

@Composable
fun AudioButton(ref: AyahRef, label: String, autoPlay: Boolean = false) {
    var playing by remember(ref) { mutableStateOf(false) }
    val toggle = {
        if (playing) {
            stopAudio()
        } else {
            playing = true
            playAudio(ref.audioUrl) { playing = false }
        }
    }
    LaunchedEffect(ref) { if (autoPlay) toggle() }
    DisposableEffect(ref) { onDispose { if (playing) stopAudio() } }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(palette.accentSoft).clickable(onClick = toggle)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (playing) "Playing…" else label, color = palette.accent, style = MaterialTheme.typography.bodyMedium)
    }
}
