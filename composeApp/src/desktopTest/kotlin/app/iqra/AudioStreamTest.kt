package app.iqra

import app.iqra.data.AyahRef
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

/** Needs internet: checks that EveryAyah audio downloads and decodes with the desktop MP3 decoder. */
class AudioStreamTest {
    @Test
    fun everyAyahMp3Decodes() {
        val stream = BufferedInputStream(URI(AyahRef(2, 1).audioUrl).toURL().openStream())
        val bitstream = Bitstream(stream)
        val decoder = Decoder()
        var frames = 0
        while (frames < 100) {
            val header = bitstream.readFrame() ?: break
            val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
            assertTrue(out.bufferLength > 0)
            bitstream.closeFrame()
            frames++
        }
        bitstream.close()
        assertTrue(frames == 100, "decoded only $frames frames")
    }
}
