package app.iqra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.iqra.data.LetterAudio
import app.iqra.data.QuranText
import app.iqra.engine.LessonBuilder
import app.iqra.engine.ProgressStore
import app.iqra.resources.Res
import app.iqra.ui.AboutScreen
import app.iqra.ui.HomeScreen
import app.iqra.ui.IqraTheme
import app.iqra.ui.LessonResult
import app.iqra.ui.LessonScreen
import app.iqra.ui.ResultScreen
import app.iqra.ui.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Home : Screen
    data object About : Screen
    data class Lesson(val id: String, val attempt: Int) : Screen
    data class Result(val result: LessonResult) : Screen
}

@Composable
fun App() {
    IqraTheme {
        val quran by produceState<QuranText?>(null) {
            value = withContext(Dispatchers.Default) {
                QuranText.parse(Res.readBytes("files/quran_uthmani.xml").decodeToString())
            }
        }
        // Find out once which letter recordings are bundled; lessons only ask about those.
        val letterAudio by produceState<LetterAudio?>(null) {
            value = withContext(Dispatchers.Default) {
                LetterAudio(LetterAudio.allPaths.filter { runCatching { Res.readBytes(it) }.isSuccess }.toSet())
            }
        }
        val store = remember { ProgressStore() }
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var attempts by remember { mutableStateOf(0) }

        // Hearts refill over time; check twice a minute while the app is open.
        LaunchedEffect(Unit) {
            while (true) {
                store.refreshHearts()
                delay(30_000)
            }
        }

        PlatformBackHandler(enabled = screen != Screen.Home) {
            stopAudio()
            screen = Screen.Home
        }

        Box(
            Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val q = quran
            val audio = letterAudio
            if (q == null || audio == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    progress = store.progress,
                    onStart = { id -> screen = Screen.Lesson(id, ++attempts) },
                    onAbout = { screen = Screen.About },
                )
                Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
                is Screen.Lesson -> {
                    val exercises = remember(s) { LessonBuilder(q, audio = audio).build(app.iqra.data.Course.lesson(s.id)) }
                    LessonScreen(
                        lessonId = s.id,
                        exercises = exercises,
                        store = store,
                        onClose = { screen = Screen.Home },
                        onFinished = { screen = Screen.Result(it) },
                    )
                }
                is Screen.Result -> ResultScreen(
                    result = s.result,
                    streak = store.progress.streak,
                    onContinue = { screen = Screen.Home },
                )
            }
        }
    }
}
