package app.iqra

import app.iqra.data.LetterAudio
import app.iqra.data.Letters
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LetterAudioTest {
    private val resources = File("src/commonMain/composeResources")

    @Test
    fun bundledClipsAreKnownAndDecode() {
        val dir = File(resources, LetterAudio.DIR)
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".mp3") }
        // The app finds its clips through index.txt, so it must list exactly the bundled ones.
        val index = File(dir, "index.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        kotlin.test.assertEquals(files.map { it.name }.toSet(), index, "files/letters/index.txt is out of date: run tools/letter-audio/make_clips.py index")
        for (file in files) {
            val path = "${LetterAudio.DIR}/${file.name}"
            assertTrue(path in LetterAudio.allPaths, "$path is not a recording the app uses (typo in the name?)")
            val bitstream = Bitstream(file.inputStream().buffered())
            val decoder = Decoder()
            var frames = 0
            while (true) {
                val header = bitstream.readFrame() ?: break
                decoder.decodeFrame(header, bitstream)
                bitstream.closeFrame()
                frames++
            }
            bitstream.close()
            assertTrue(frames > 5, "$path decoded only $frames MP3 frames")
        }
    }

    /** tools/letter-audio/make_clips.py names its files from its own copy of the letter list. */
    @Test
    fun recordingToolMatchesLetters() {
        val script = File("../tools/letter-audio/make_clips.py").readText()
            .substringAfter("LETTERS = [").substringBefore("\n]")
        val rows = Regex("""\("(\w+)", "(.)", "[^"]+", (?:"([^"]+)"|None)\)""").findAll(script).toList()
        if (rows.isEmpty()) fail("No letter rows found in make_clips.py")
        assertEquals(Letters.all.map { it.key }, rows.map { it.groupValues[1] })
        assertEquals(Letters.all.map { it.char }, rows.map { it.groupValues[2] })
        val sounds = rows.map { it.groupValues[3] }
        Letters.all.zip(sounds).filter { it.first != Letters.alif }.forEach { (letter, sound) ->
            assertEquals(letter.sound, sound, "sound of ${letter.name}")
        }
    }
}
