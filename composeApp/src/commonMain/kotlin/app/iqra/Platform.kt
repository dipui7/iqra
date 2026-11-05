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

/** Decode an MP3 to mono samples, or null if it can't be decoded. */
expect suspend fun decodeMp3(bytes: ByteArray): app.iqra.engine.Pcm?

/** Download a recitation (or read it from the offline cache). Blocking: call off the UI thread. */
expect suspend fun fetchAudioBytes(url: String): ByteArray?

/**
 * Start recording from the microphone (asking for permission first where needed).
 * [onStarted] runs on the UI thread with false if there is no microphone or permission.
 */
expect fun startRecording(onStarted: (Boolean) -> Unit)

/** Stop recording and return what was heard (at most ~15 s), or null if nothing was recorded. */
expect fun stopRecording(): app.iqra.engine.Pcm?

/** Whether this platform can show a daily reminder notification (Android yes, desktop no). */
expect val remindersSupported: Boolean

/** Remind the learner every day at [hour] (0–23, local time), or stop reminding with null. */
expect fun scheduleReminder(hour: Int?)

/** Play a short sound effect (MP3 bytes) alongside any recitation, without stopping it. */
expect fun playEffect(bytes: ByteArray)
