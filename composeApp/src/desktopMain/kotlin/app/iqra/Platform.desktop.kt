package app.iqra

import androidx.compose.runtime.Composable
import javazoom.jl.player.Player
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.time.LocalDate
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private val progressFile: File by lazy {
    val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
    File(base, "Iqra").apply { mkdirs() }.resolve("progress.json")
}

actual fun readProgress(): String? = progressFile.takeIf { it.exists() }?.readText()

actual fun writeProgress(text: String) {
    // Write to a temp file first so a crash mid-write never corrupts saved progress.
    val tmp = File(progressFile.path + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(progressFile)) {
        progressFile.delete()
        tmp.renameTo(progressFile)
    }
}

private val lock = Any()
private var player: Player? = null
// Bumped on every play/stop so a download that finishes late doesn't start playing.
private var generation = 0

actual fun playAudio(url: String, onDone: () -> Unit) = play(url, onDone) {
    val connection = URI(url).toURL().openConnection().apply {
        setRequestProperty("User-Agent", "Iqra/0.1")
        connectTimeout = 10_000
        readTimeout = 15_000
    }
    BufferedInputStream(connection.getInputStream())
}

actual fun playAudioBytes(bytes: ByteArray, onDone: () -> Unit) =
    play("bundled clip", onDone) { ByteArrayInputStream(bytes) }

private fun play(what: String, onDone: () -> Unit, open: () -> InputStream) {
    stopAudio()
    val gen = synchronized(lock) { ++generation }
    thread(isDaemon = true, name = "audio") {
        try {
            open().use { stream ->
                val p = Player(stream)
                val current = synchronized(lock) { (gen == generation).also { if (it) player = p } }
                if (current) p.play() else p.close()
            }
        } catch (e: Exception) {
            System.err.println("Audio failed for $what: ${e.message}")
        } finally {
            SwingUtilities.invokeLater(onDone)
        }
    }
}

actual fun stopAudio() {
    synchronized(lock) {
        generation++
        player?.close()
        player = null
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back button; the close button in the UI is used instead.
}
