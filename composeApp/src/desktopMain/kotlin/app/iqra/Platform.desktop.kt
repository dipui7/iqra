package app.iqra

import androidx.compose.runtime.Composable
import javazoom.jl.player.Player
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.time.LocalDate
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private val appDir: File by lazy {
    val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
    val dir = File(base, "AlifUp")
    // The app was called Iqra before: bring progress and cached recitations across once.
    val old = File(base, "Iqra")
    if (!dir.exists() && old.isDirectory) old.copyRecursively(dir)
    dir.apply { mkdirs() }
}

private val progressFile: File by lazy { appDir.resolve("progress.json") }

/** Recitations already heard, so they play again without internet. */
private val audioCache: File by lazy { appDir.resolve("audio-cache").apply { mkdirs() } }

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
private var download: CachingStream? = null
// Bumped on every play/stop so a download that finishes late doesn't start playing.
private var generation = 0

actual fun playAudio(url: String, onDone: () -> Unit) = play(url, onDone) {
    val cached = File(audioCache, cacheName(url))
    if (cached.exists()) return@play BufferedInputStream(cached.inputStream())
    val connection = URI(url).toURL().openConnection().apply {
        setRequestProperty("User-Agent", "AlifUp/0.2")
        connectTimeout = 10_000
        readTimeout = 15_000
    }
    val caching = CachingStream(connection.getInputStream(), cached)
    synchronized(lock) { download = caching }
    BufferedInputStream(caching)
}

/**
 * Passes a download through while saving it; only a download read to the end is kept, so a
 * recitation stopped half-way is fetched again next time.
 */
private class CachingStream(input: InputStream, private val dest: File) : FilterInputStream(input) {
    private val tmp = File(dest.path + ".part")
    private val out = tmp.outputStream().buffered()
    private var complete = false

    /** Set when playback is stopped: the rest is not downloaded (close() then runs on the UI thread). */
    @Volatile var abandoned = false

    override fun read(): Int = super.read().also { if (it < 0) complete = true else out.write(it) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it < 0) complete = true else out.write(b, off, it) }

    override fun close() {
        // Playback ended on its own (on the audio thread): save any bytes the decoder didn't need.
        if (!complete && !abandoned) {
            runCatching {
                val buf = ByteArray(8192)
                while (true) {
                    val n = `in`.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                complete = true
            }
        }
        super.close()
        out.close()
        if (complete && tmp.renameTo(dest)) pruneCache() else tmp.delete()
    }
}

/** The cached copy of a recitation, if it has been heard (or fetched) before. */
internal fun cachedAudio(url: String): File? = File(audioCache, cacheName(url)).takeIf { it.exists() }

internal fun saveToAudioCache(url: String, bytes: ByteArray) {
    val dest = File(audioCache, cacheName(url))
    val tmp = File(dest.path + ".part")
    tmp.writeBytes(bytes)
    if (tmp.renameTo(dest)) pruneCache() else tmp.delete()
}

private fun cacheName(url: String) = url.substringAfter("://").replace(Regex("[^A-Za-z0-9.]"), "_")

/** Keep the cache under ~300 MB by dropping the least recently written files. */
private fun pruneCache(limit: Long = 300L * 1024 * 1024) {
    val files = audioCache.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }
    var total = files.sumOf { it.length() }
    for (f in files) {
        if (total <= limit) break
        total -= f.length()
        f.delete()
    }
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
        download?.abandoned = true
        download = null
        player?.close()
        player = null
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back button; the close button in the UI is used instead.
}

actual val remindersSupported: Boolean = false

actual fun scheduleReminder(hour: Int?) {
    // The desktop app has no notifications; the setting isn't shown there.
}

actual fun playEffect(bytes: ByteArray) {
    // Its own player on its own thread, so it never stops a recitation that is playing.
    thread(isDaemon = true, name = "effect") {
        runCatching { Player(ByteArrayInputStream(bytes)).play() }
    }
}
