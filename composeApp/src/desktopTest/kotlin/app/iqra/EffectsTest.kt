package app.iqra

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EffectsTest {
    @Test
    fun everyEffectIsBundledShortAndDecodes() {
        for (e in Effect.entries) {
            val file = File("src/commonMain/composeResources/files/sfx/${e.file}.mp3")
            assertTrue(file.exists(), "missing ${file.path}")
            assertTrue(file.length() < 40_000, "${e.file} is large for a sound effect")
            val bits = Bitstream(file.inputStream().buffered())
            val decoder = Decoder()
            var frames = 0
            while (true) {
                val h = bits.readFrame() ?: break
                decoder.decodeFrame(h, bits); bits.closeFrame(); frames++
            }
            bits.close()
            // At 44.1 kHz an MP3 frame is ~26 ms: effects should last between ~0.1 s and 1.5 s.
            assertTrue(frames in 4..60, "${e.file}: $frames frames")
        }
    }
}
