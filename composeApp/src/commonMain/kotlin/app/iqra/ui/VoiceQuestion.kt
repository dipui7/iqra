package app.iqra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.iqra.data.Sound
import app.iqra.decodeMp3
import app.iqra.engine.Exercise
import app.iqra.engine.LessonSession
import app.iqra.engine.Pcm
import app.iqra.engine.VoiceMatch
import app.iqra.fetchAudioBytes
import app.iqra.resources.Res
import app.iqra.startRecording
import app.iqra.stopAudio
import app.iqra.stopRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tries allowed per voice question before it counts as wrong. */
private const val VOICE_TRIES = 3

private sealed interface VoiceState {
    data object Loading : VoiceState
    /** The reference recordings could not be loaded (e.g. no internet for a recitation). */
    data object Unavailable : VoiceState
    data object Ready : VoiceState
    data object Recording : VoiceState
    data object Checking : VoiceState
    data object NoMic : VoiceState
}

private suspend fun loadPcm(sound: Sound): Pcm? = withContext(Dispatchers.Default) {
    val bytes = when (sound) {
        is Sound.Clip -> runCatching { Res.readBytes(sound.path) }.getOrNull()
        is Sound.Ayah -> fetchAudioBytes(sound.ref.audioUrl)
    }
    bytes?.let { decodeMp3(it) }
}

@Composable
fun VoiceQuestion(q: Exercise.Voice, session: LessonSession) {
    var state by remember { mutableStateOf<VoiceState>(VoiceState.Loading) }
    var target by remember { mutableStateOf<Pcm?>(null) }
    var decoys by remember { mutableStateOf<List<Pcm>>(emptyList()) }
    var triesLeft by remember { mutableIntStateOf(VOICE_TRIES) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val answered = session.feedback != null

    LaunchedEffect(q) {
        val t = loadPcm(q.target)
        val d = q.decoys.mapNotNull { loadPcm(it) }
        target = t
        decoys = d
        state = if (t == null || d.isEmpty()) VoiceState.Unavailable else VoiceState.Ready
    }

    fun check(recording: Pcm?) {
        state = VoiceState.Checking
        scope.launch {
            val t = target ?: return@launch
            val result = recording?.let { r -> withContext(Dispatchers.Default) { VoiceMatch.check(r, t, decoys) } }
            when {
                result is VoiceMatch.Result.Scored && result.passed -> {
                    message = "Sounds right (${result.score}% match)"
                    session.gradeVoice(q, right = true)
                }
                else -> {
                    triesLeft--
                    val why = if (result is VoiceMatch.Result.Scored) "Not close enough yet" else "We couldn't hear you clearly"
                    if (triesLeft <= 0) {
                        message = "$why."
                        session.gradeVoice(q, right = false)
                    } else {
                        message = "$why — try again (${triesLeft} ${if (triesLeft == 1) "try" else "tries"} left)."
                        state = VoiceState.Ready
                    }
                }
            }
        }
    }

    fun toggleRecording() {
        when (state) {
            VoiceState.Ready -> {
                stopAudio()
                message = null
                startRecording { ok -> state = if (ok) VoiceState.Recording else VoiceState.NoMic }
            }
            VoiceState.Recording -> check(stopRecording())
            else -> Unit
        }
    }

    // Stop on its own after a while, in case the learner forgets.
    LaunchedEffect(state) {
        if (state == VoiceState.Recording) {
            delay(if (q.big != null && q.big.length <= 3) 5_000 else 15_000)
            if (state == VoiceState.Recording) check(stopRecording())
        }
    }

    Text(q.prompt, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.padding(vertical = 12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card).padding(16.dp)) {
        if (q.big != null) {
            Text(
                q.big,
                style = arabicStyle(if (q.big.length <= 3) 96 else 40).copy(textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("From memory", style = MaterialTheme.typography.titleMedium, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        q.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    Spacer(Modifier.height(24.dp))

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            VoiceState.Loading, VoiceState.Checking -> {
                CircularProgressIndicator(Modifier.size(72.dp), color = palette.accent)
            }
            VoiceState.Unavailable -> {
                Text(
                    "The recording to compare with couldn't be loaded. Check your internet connection.",
                    color = palette.secondary, textAlign = TextAlign.Center,
                )
                if (!answered) TextButton({ session.gradeVoice(q, right = false) }) { Text("Skip (counts as not passed)") }
            }
            VoiceState.NoMic -> {
                Text("No microphone, or permission to use it was refused.", color = palette.secondary, textAlign = TextAlign.Center)
                if (!answered) {
                    TextButton({ state = VoiceState.Ready }) { Text("Try again") }
                    TextButton({ session.gradeVoice(q, right = false) }) { Text("Skip (counts as not passed)") }
                }
            }
            VoiceState.Ready, VoiceState.Recording -> {
                val recording = state == VoiceState.Recording
                Box(
                    Modifier.size(88.dp).clip(CircleShape)
                        .background(if (recording) palette.wrong else palette.accentFill)
                        .clickable(enabled = !answered) { toggleRecording() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) "Stop" else "Record", tint = if (recording) palette.onAccent else palette.onAccentFill, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        answered -> ""
                        recording -> "Listening… tap to stop"
                        else -> "Tap the microphone, then say it"
                    },
                    color = palette.secondary,
                )
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = palette.text, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your voice is compared with the reciter's recording on this device and never uploaded. " +
                "It checks how alike they sound, not tajweed.",
            style = MaterialTheme.typography.bodySmall, color = palette.secondary, textAlign = TextAlign.Center,
        )
    }
}
