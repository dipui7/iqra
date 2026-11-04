package app.iqra

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.iqra.engine.Pcm
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.nio.ByteOrder
import kotlin.concurrent.thread

/** Set by MainActivity: asks the user for microphone permission and reports the answer. */
var requestMicPermission: ((onResult: (Boolean) -> Unit) -> Unit)? = null

private val handler by lazy { Handler(Looper.getMainLooper()) }

actual suspend fun decodeMp3(bytes: ByteArray): Pcm? = runCatching {
    // MediaExtractor reads from a file, so the clip goes to a temporary one first.
    val tmp = File.createTempFile("clip", ".mp3", appContext.cacheDir)
    try {
        tmp.writeBytes(bytes)
        decodeFile(tmp)
    } finally {
        tmp.delete()
    }
}.getOrNull()

private fun decodeFile(file: File): Pcm? {
    val extractor = MediaExtractor().apply { setDataSource(file.path) }
    val track = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: return null
    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val out = ArrayList<Float>()
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    while (true) {
        if (!inputDone) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) {
                val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                if (n < 0) {
                    codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else if (o >= 0) {
            val shorts = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
            while (shorts.remaining() >= channels) {
                var s = 0f
                repeat(channels) { s += shorts.get() }
                out += s / channels / 32768f
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }
    codec.stop()
    codec.release()
    extractor.release()
    return if (out.isEmpty()) null else Pcm(out.toFloatArray(), rate)
}

actual suspend fun fetchAudioBytes(url: String): ByteArray? = runCatching {
    val dir = File(appContext.cacheDir, "audio").apply { mkdirs() }
    val cached = File(dir, url.substringAfter("://").replace(Regex("[^A-Za-z0-9.]"), "_"))
    if (cached.exists()) return@runCatching cached.readBytes()
    val bytes = URL(url).openConnection().apply {
        setRequestProperty("User-Agent", "AlifUp/0.2")
        connectTimeout = 10_000
        readTimeout = 15_000
    }.getInputStream().use { it.readBytes() }
    val tmp = File(cached.path + ".part")
    tmp.writeBytes(bytes)
    tmp.renameTo(cached)
    bytes
}.getOrNull()

private const val REC_RATE = 16_000
private const val MAX_BYTES = REC_RATE * 2 * 15 // 15 s of 16-bit mono

private var recorder: AudioRecord? = null
private var recorded: ByteArrayOutputStream? = null

actual fun startRecording(onStarted: (Boolean) -> Unit) {
    stopRecording()
    val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
        onStarted(begin())
    } else {
        val ask = requestMicPermission ?: return onStarted(false)
        ask { ok -> handler.post { onStarted(ok && begin()) } }
    }
}

@SuppressLint("MissingPermission") // checked in startRecording
private fun begin(): Boolean = runCatching {
    val size = maxOf(
        AudioRecord.getMinBufferSize(REC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
        REC_RATE / 5,
    )
    val r = AudioRecord(MediaRecorder.AudioSource.MIC, REC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
    if (r.state != AudioRecord.STATE_INITIALIZED) {
        r.release()
        return@runCatching false
    }
    val out = ByteArrayOutputStream()
    recorder = r
    recorded = out
    r.startRecording()
    thread(isDaemon = true, name = "mic") {
        val buf = ByteArray(size)
        while (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val n = r.read(buf, 0, buf.size)
            if (n <= 0) break
            synchronized(out) { if (out.size() < MAX_BYTES) out.write(buf, 0, n) }
        }
    }
    true
}.getOrDefault(false)

actual fun stopRecording(): Pcm? {
    val r = recorder ?: return null
    recorder = null
    runCatching { r.stop() }
    r.release()
    val bytes = recorded?.let { synchronized(it) { it.toByteArray() } } ?: return null
    recorded = null
    if (bytes.size < 3200) return null
    val samples = FloatArray(bytes.size / 2) { i ->
        ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
    }
    return Pcm(samples, REC_RATE)
}
