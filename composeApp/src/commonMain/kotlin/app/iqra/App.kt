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
import app.iqra.data.Course
import app.iqra.data.Track
import app.iqra.data.LetterAudio
import app.iqra.data.Letters
import app.iqra.engine.Skills
import app.iqra.data.QuranMeta
import app.iqra.data.QuranText
import app.iqra.data.TajweedMarks
import app.iqra.data.Translation
import app.iqra.ui.ReaderExtras
import app.iqra.engine.LessonBuilder
import app.iqra.engine.ProgressStore
import app.iqra.resources.Res
import app.iqra.ui.AboutScreen
import app.iqra.ui.HomeScreen
import app.iqra.ui.IqraTheme
import app.iqra.ui.LessonResult
import app.iqra.ui.LessonScreen
import app.iqra.engine.Badges
import app.iqra.ui.BadgesScreen
import app.iqra.ui.ReaderScreen
import app.iqra.ui.ResultScreen
import app.iqra.ui.SurahList
import app.iqra.ui.SettingsScreen
import app.iqra.ui.SupportScreen
import app.iqra.ui.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Home : Screen
    data object About : Screen
    data object Settings : Screen
    data object Support : Screen
    data class Lesson(val id: String, val attempt: Int) : Screen
    data class Practice(val attempt: Int) : Screen
    data class Reader(val surah: Int) : Screen
    data object Badges : Screen
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
        // Tajweed colours and the translation, for the reader; loaded in the background.
        val readerExtras by produceState<ReaderExtras?>(null) {
            value = withContext(Dispatchers.Default) {
                ReaderExtras(
                    TajweedMarks.parse(Res.readBytes("files/tajweed.txt").decodeToString()),
                    Translation.parse("Saheeh International", Res.readBytes("files/translation_en_sahih.txt").decodeToString()),
                )
            }
        }
        val meta by produceState<QuranMeta?>(null) {
            value = withContext(Dispatchers.Default) {
                QuranMeta.parse(Res.readBytes("files/quran_metadata.xml").decodeToString())
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
        // Kept here so coming back from a lesson or the reader returns to the same tab.
        var homeTab by remember { mutableStateOf(Track.Reading) }
        // Badges held when the last lesson started, to celebrate new ones on the results screen.
        var badgesBefore by remember { mutableStateOf(Badges.earned(store.progress)) }
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
            val m = meta
            val audio = letterAudio
            if (q == null || m == null || audio == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    progress = store.progress,
                    onStart = { id -> screen = Screen.Lesson(id, ++attempts) },
                    onAbout = { screen = Screen.About },
                    onPractice = { screen = Screen.Practice(++attempts) },
                    onSettings = { screen = Screen.Settings },
                    onSupport = { screen = Screen.Support },
                    quranTab = { SurahList(m) { screen = Screen.Reader(it) } },
                    track = homeTab,
                    onTrackChange = { homeTab = it },
                    onBadges = { screen = Screen.Badges },
                    onBuyUnlimited = store::buyUnlimitedHearts,
                )
                Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
                is Screen.Reader -> ReaderScreen(
                    q, m.surah(s.surah), onBack = { screen = Screen.Home },
                    extras = readerExtras,
                    showTajweed = store.progress.showTajweed,
                    showTranslation = store.progress.showTranslation,
                    onShowTajweed = store::setShowTajweed,
                    onShowTranslation = store::setShowTranslation,
                    textSize = store.progress.readerTextSize,
                    onTextSize = store::setReaderTextSize,
                )
                Screen.Badges -> BadgesScreen(store.progress, onBack = { screen = Screen.Home })
                Screen.Support -> SupportScreen(onBack = { screen = Screen.Home })
                Screen.Settings -> SettingsScreen(store, onBack = { screen = Screen.Home })
                is Screen.Lesson -> {
                    val lesson = remember(s) { Course.lesson(s.id) }
                    val exercises = remember(s) { LessonBuilder(q, audio = audio).build(lesson) }
                    LessonScreen(
                        lessonId = s.id,
                        title = lesson.title,
                        exercises = exercises,
                        store = store,
                        onClose = { screen = Screen.Home },
                        onFinished = { screen = Screen.Result(it) },
                    )
                }
                is Screen.Practice -> {
                    val exercises = remember(s) {
                        val builder = LessonBuilder(q, audio = audio)
                        builder.practice(store.progress.skillsToPractise(todayEpochDay()))
                            .ifEmpty { builder.practice(Letters.all.take(4).map(Skills::letter)) }
                    }
                    LessonScreen(
                        lessonId = "practice",
                        title = "Practice",
                        exercises = exercises,
                        store = store,
                        onClose = { screen = Screen.Home },
                        onFinished = { screen = Screen.Result(it) },
                        practice = true,
                    )
                }
                is Screen.Result -> ResultScreen(
                    result = s.result,
                    streak = store.progress.streak,
                    onContinue = { badgesBefore = Badges.earned(store.progress); screen = Screen.Home },
                    newBadges = remember(s) { Badges.earned(store.progress) - badgesBefore },
                )
            }
        }
    }
}
