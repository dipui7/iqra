package app.iqra

import androidx.compose.runtime.Composable

// Small platform hooks; each has an Android and a desktop implementation.

expect fun nowMillis(): Long

/** Today's local date as days since 1970-01-01. */
expect fun todayEpochDay(): Long

expect fun readProgress(): String?

expect fun writeProgress(text: String)

/** Stream an MP3 from [url]; [onDone] runs on the UI thread when it ends, fails or is stopped. */
expect fun playAudio(url: String, onDone: () -> Unit)

/** Play an MP3 held in memory (a bundled recording); [onDone] as for [playAudio]. */
expect fun playAudioBytes(bytes: ByteArray, onDone: () -> Unit)

expect fun stopAudio()

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
