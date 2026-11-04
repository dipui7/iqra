package app.iqra

import app.iqra.engine.Pcm
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

actual suspend fun decodeMp3(bytes: ByteArray): Pcm? = runCatching {
    val bitstream = Bitstream(ByteArrayInputStream(bytes))
    val decoder = Decoder()
    val out = ArrayList<Float>(bytes.size * 4)
    var rate = 0
    while (true) {
        val header = bitstream.readFrame() ?: break
        val buf = decoder.decodeFrame(header, bitstream) as SampleBuffer
        rate = buf.sampleFrequency
        val ch = buf.channelCount
        val data = buf.buffer
        var i = 0
        while (i + ch - 1 < buf.bufferLength) {
            var s = 0f
            for (c in 0 until ch) s += data[i + c]
            out += s / ch / 32768f
            i += ch
        }
        bitstream.closeFrame()
    }
    bitstream.close()
    if (rate == 0 || out.isEmpty()) null else Pcm(out.toFloatArray(), rate)
}.getOrNull()

actual suspend fun fetchAudioBytes(url: String): ByteArray? = runCatching {
    cachedAudio(url)?.readBytes() ?: URI(url).toURL().openConnection().apply {
        setRequestProperty("User-Agent", "AlifUp/0.2")
        connectTimeout = 10_000
        readTimeout = 15_000
    }.getInputStream().use { it.readBytes() }.also { saveToAudioCache(url, it) }
}.getOrNull()

private const val REC_RATE = 16_000f
private const val MAX_BYTES = 16_000 * 2 * 15 // 15 s of 16-bit mono

private var line: TargetDataLine? = null
private var recorded: ByteArrayOutputStream? = null

actual fun startRecording(onStarted: (Boolean) -> Unit) {
    stopRecording()
    val ok = runCatching {
        val format = AudioFormat(REC_RATE, 16, 1, true, false)
        val l = AudioSystem.getTargetDataLine(format)
        l.open(format)
        l.start()
        val out = ByteArrayOutputStream()
        line = l
        recorded = out
        thread(isDaemon = true, name = "mic") {
            val buf = ByteArray(3200)
            while (l.isOpen) {
                val n = l.read(buf, 0, buf.size)
                if (n <= 0) break
                synchronized(out) { if (out.size() < MAX_BYTES) out.write(buf, 0, n) }
            }
        }
    }.isSuccess
    SwingUtilities.invokeLater { onStarted(ok) }
}

actual fun stopRecording(): Pcm? {
    val l = line ?: return null
    line = null
    l.stop()
    l.close()
    val bytes = recorded?.let { synchronized(it) { it.toByteArray() } } ?: return null
    recorded = null
    if (bytes.size < 3200) return null
    val samples = FloatArray(bytes.size / 2) { i ->
        ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
    }
    return Pcm(samples, REC_RATE.toInt())
}
