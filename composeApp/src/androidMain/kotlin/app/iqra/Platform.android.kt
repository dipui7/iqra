package app.iqra

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.io.File
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

actual fun playAudio(url: String, onDone: () -> Unit) = play(onDone) { setDataSource(url) }

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
    player?.release()
    player = null
    playerDone?.invoke()
    playerDone = null
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
