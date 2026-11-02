package app.iqra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.iqra.data.Course
import app.iqra.data.QuranText
import app.iqra.engine.Exercise
import app.iqra.engine.LessonBuilder
import app.iqra.engine.Progress
import app.iqra.engine.ProgressStore
import app.iqra.ui.HomeScreen
import app.iqra.ui.IqraTheme
import app.iqra.ui.LessonResult
import app.iqra.ui.LessonScreen
import app.iqra.ui.ResultScreen
import app.iqra.ui.palette
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/** Renders key screens to composeApp/build/screens as PNG files for a visual check (not an assertion test). */
class ScreenshotTest {
    private val quran = QuranText.parse(File("src/commonMain/composeResources/files/quran_uthmani.xml").readText())
    private val out = File("build/screens").apply { mkdirs() }

    private fun shot(name: String, content: @Composable () -> Unit) {
        ImageComposeScene(440 * 2, 860 * 2, Density(2f)) {
            IqraTheme { Box(Modifier.fillMaxSize().background(palette.background)) { content() } }
        }.use { scene ->
            // Let async font/resource loading settle across a few frames.
            repeat(30) { scene.render(it * 16_000_000L); Thread.sleep(50) }
            val data = scene.render(31 * 16_000_000L).encodeToData(EncodedImageFormat.PNG)!!
            File(out, "$name.png").writeBytes(data.bytes)
        }
    }

    private fun lessonShot(name: String, exercise: Exercise) = shot(name) {
        LessonScreen("u1-learn", "Meet the letters", listOf(exercise), ProgressStore(), {}, {})
    }

    @Test
    fun render() {
        val progress = Progress(completed = setOf("u1-learn", "u1-practice"), xp = 25, streak = 3, lastActiveDay = todayEpochDay())
        shot("home") { HomeScreen(progress, {}, {}) }
        val b = LessonBuilder(quran, Random(7))
        val learn = b.build(Course.lesson("u1-learn"))
        lessonShot("intro", learn.first())
        lessonShot("letter-to-name", learn[1])
        lessonShot("name-to-letter", learn.last())
        val practice = b.build(Course.lesson("u4-practice"))
        lessonShot("match", practice.first { it is Exercise.Match })
        lessonShot("form", practice.first { it is Exercise.Choice && it.prompt.startsWith("This is") })
        val q = b.build(Course.lesson("u1-quran"))
        lessonShot("quran-word", q.first { it is Exercise.Choice && it.prompt.startsWith("Which word") })
        lessonShot("quran-yesno", q.first { it is Exercise.Choice && it.prompt.startsWith("Does") })
        lessonShot("vowel-intro", b.build(Course.lesson("u8-learn")).first())
        lessonShot("syllable", b.build(Course.lesson("u8-practice")).first())
        val op = b.build(Course.lesson("u9-listen"))
        lessonShot("opening-listen", op.first { it is Exercise.Choice && it.autoPlay })
        lessonShot("opening-letters", op.first { it is Exercise.Choice && !it.autoPlay })
        val sukun = b.build(Course.lesson("u10-learn"))
        lessonShot("rule-intro", sukun.first())
        lessonShot("rule-read", sukun.first { it is Exercise.Choice && it.prompt.startsWith("How") })
        lessonShot("rule-which", sukun.first { it is Exercise.Choice && it.prompt.startsWith("Which one") })
        lessonShot("rule-quran", b.build(Course.lesson("u11-quran")).first { it is Exercise.Choice && it.prompt.startsWith("Which word") })
        shot("result") { ResultScreen(LessonResult("Meet the letters", true, 15, 92), 3) {} }
    }
}
