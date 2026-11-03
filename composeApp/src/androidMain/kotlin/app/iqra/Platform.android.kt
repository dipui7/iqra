package app.iqra

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.URL
import kotlin.concurrent.thread
import java.time.LocalDate

/** Set by MainActivity before the UI starts. */
@SuppressLint("StaticFieldLeak")
lateinit var appContext: Context

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private val progressFile: File get() = File(appContext.filesDir, "progress.json")

actual fun readProgress(): String? = progressFile.takeIf { it.exists() }?.readText()

actual fun writeProgress(text: String) {
    val tmp = File(progressFile.path + ".tmp")
    tmp.writeText(text)
    tmp.renameTo(progressFile)
}

private var player: MediaPlayer? = null
private var playerDone: (() -> Unit)? = null

/** Recitations already heard, so they play again without internet. */
private val audioCache: File get() = File(appContext.cacheDir, "audio").apply { mkdirs() }

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

// Bumped on every play/stop so a download that finishes late doesn't start playing.
private var generation = 0

actual fun playAudio(url: String, onDone: () -> Unit) {
    val cached = File(audioCache, url.substringAfter("://").replace(Regex("[^A-Za-z0-9.]"), "_"))
    if (cached.exists()) {
        play(onDone) { setDataSource(cached.path) }
        return
    }
    stopAudio()
    val gen = ++generation
    playerDone = onDone
    thread(isDaemon = true, name = "audio-download") {
        val ok = runCatching {
            val tmp = File(cached.path + ".part")
            val connection = URL(url).openConnection().apply {
                setRequestProperty("User-Agent", "AlifUp/0.2")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            connection.getInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(cached))
            pruneCache()
        }.isSuccess
        mainHandler.post {
            if (gen != generation) return@post
            if (ok) {
                playerDone = null // still the same play request: don't report it finished
                play(onDone) { setDataSource(cached.path) }
            } else {
                stopAudio()
            }
        }
    }
}

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

actual fun playAudioBytes(bytes: ByteArray, onDone: () -> Unit) = play(onDone) {
    setDataSource(object : MediaDataSource() {
        override fun getSize() = bytes.size.toLong()
        override fun close() {}
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(size, bytes.size - position.toInt())
            System.arraycopy(bytes, position.toInt(), buffer, offset, n)
            return n
        }
    })
}

private fun play(onDone: () -> Unit, setSource: MediaPlayer.() -> Unit) {
    stopAudio()
    generation++
    playerDone = onDone
    player = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        setOnPreparedListener { it.start() }
        setOnCompletionListener { stopAudio() }
        setOnErrorListener { _, _, _ -> stopAudio(); true }
        setSource()
        prepareAsync()
    }
}

actual fun stopAudio() {
    generation++
    player?.release()
    player = null
    val done = playerDone
    playerDone = null
    done?.invoke()
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
