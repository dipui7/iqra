package app.iqra

import androidx.compose.runtime.Composable
import app.iqra.engine.Pcm
import kotlinx.coroutines.await
import kotlin.js.Promise

// The web version's platform hooks, written as small pieces of JavaScript: browser storage for
// progress, an <audio> element for recitation, Web Audio for decoding and the microphone.
// Byte and sample arrays cross between Kotlin and JavaScript one element at a time, which is
// fast enough for clips of a few seconds.

private fun jsNow(): Double = js("Date.now()")

private fun jsLocalEpochDay(): Double = js("Math.floor((Date.now() - new Date().getTimezoneOffset() * 60000) / 86400000)")

private fun jsGetItem(key: String): String? = js("(function () { try { return window.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun jsSetItem(key: String, value: String) {
    js("try { window.localStorage.setItem(key, value); } catch (e) {}")
}

private fun jsNewBytes(size: Int): JsAny = js("new Uint8Array(size)")

private fun jsSetByte(array: JsAny, index: Int, value: Int) {
    js("array[index] = value")
}

private fun jsLength(array: JsAny): Int = js("array.length")

private fun jsByteAt(array: JsAny, index: Int): Int = js("array[index]")

private fun jsFloatAt(array: JsAny, index: Int): Double = js("array[index]")

private fun jsField(obj: JsAny, name: String): JsAny = js("obj[name]")

private fun jsIntField(obj: JsAny, name: String): Int = js("obj[name]")

private fun jsBlobUrl(bytes: JsAny): String = js("URL.createObjectURL(new Blob([bytes], { type: 'audio/mpeg' }))")

/** Plays [url] in the one shared <audio> element; [onDone] runs once when it ends, fails or is stopped. */
private fun jsPlay(url: String, onDone: () -> Unit) {
    js(
        """
        var old = window.__alifupAudio;
        if (old) { old.__finish(); old.pause(); }
        var a = new Audio(url);
        var done = false;
        a.__finish = function () { if (done) return; done = true; onDone(); };
        a.onended = a.__finish;
        a.onerror = a.__finish;
        window.__alifupAudio = a;
        var p = a.play();
        if (p && p.catch) p.catch(function () { a.__finish(); });
        """,
    )
}

private fun jsStop() {
    js("var a = window.__alifupAudio; window.__alifupAudio = null; if (a) { a.pause(); a.__finish(); }")
}

/** A separate, fire-and-forget <audio> element, so effects never stop a recitation. */
private fun jsPlayEffect(url: String) {
    js("var e = new Audio(url); var p = e.play(); if (p && p.catch) p.catch(function () {});")
}

private fun jsFetchBytes(url: String): Promise<JsAny?> =
    js("fetch(url).then(function (r) { return r.ok ? r.arrayBuffer() : null; }).then(function (b) { return b ? new Uint8Array(b) : null; }).catch(function () { return null; })")

/** Decodes MP3 bytes to mono samples: { data: Float32Array, rate: number }, or null. */
private fun jsDecode(bytes: JsAny): Promise<JsAny?> = js(
    """
    (function () {
      try {
        var ctx = new OfflineAudioContext(1, 1, 44100);
        var buf = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
        return ctx.decodeAudioData(buf)
          .then(function (audio) { return { data: audio.getChannelData(0), rate: audio.sampleRate }; })
          .catch(function () { return null; });
      } catch (e) { return Promise.resolve(null); }
    })()
    """,
)

private fun jsStartRecording(onStarted: (Boolean) -> Unit) {
    js(
        """
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { onStarted(false); return; }
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
          var Ctx = window.AudioContext || window.webkitAudioContext;
          var ctx = new Ctx();
          var src = ctx.createMediaStreamSource(stream);
          var node = ctx.createScriptProcessor(4096, 1, 1);
          var rec = { ctx: ctx, stream: stream, src: src, node: node, chunks: [], total: 0, max: ctx.sampleRate * 20 };
          node.onaudioprocess = function (e) {
            if (rec.total >= rec.max) return;
            var d = new Float32Array(e.inputBuffer.getChannelData(0));
            rec.chunks.push(d);
            rec.total += d.length;
          };
          src.connect(node);
          node.connect(ctx.destination);
          window.__alifupRec = rec;
          onStarted(true);
        }).catch(function () { onStarted(false); });
        """,
    )
}

/** Stops the microphone and returns { data: Float32Array, rate: number }, or null if not recording. */
private fun jsStopRecording(): JsAny? = js(
    """
    (function () {
      var r = window.__alifupRec;
      if (!r) return null;
      window.__alifupRec = null;
      try { r.src.disconnect(); r.node.disconnect(); } catch (e) {}
      r.stream.getTracks().forEach(function (t) { t.stop(); });
      var rate = r.ctx.sampleRate;
      try { r.ctx.close(); } catch (e) {}
      var out = new Float32Array(r.total), o = 0;
      r.chunks.forEach(function (c) { out.set(c, o); o += c.length; });
      return { data: out, rate: rate };
    })()
    """,
)

private fun ByteArray.toJs(): JsAny {
    val array = jsNewBytes(size)
    for (i in indices) jsSetByte(array, i, this[i].toInt() and 0xFF)
    return array
}

private fun JsAny.toByteArray(): ByteArray = ByteArray(jsLength(this)) { jsByteAt(this, it).toByte() }

private fun JsAny.toPcm(): Pcm {
    val data = jsField(this, "data")
    return Pcm(FloatArray(jsLength(data)) { jsFloatAt(data, it).toFloat() }, jsIntField(this, "rate"))
}

private const val PROGRESS_KEY = "alifup.progress"

actual fun nowMillis(): Long = jsNow().toLong()

actual fun todayEpochDay(): Long = jsLocalEpochDay().toLong()

actual fun readProgress(): String? = jsGetItem(PROGRESS_KEY)

actual fun writeProgress(text: String) = jsSetItem(PROGRESS_KEY, text)

actual fun playAudio(url: String, onDone: () -> Unit) = jsPlay(url, onDone)

actual fun playAudioBytes(bytes: ByteArray, onDone: () -> Unit) = jsPlay(jsBlobUrl(bytes.toJs()), onDone)

actual fun stopAudio() = jsStop()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The browser's own back button leaves the page; the in-app back and close buttons are used instead.
}

actual suspend fun decodeMp3(bytes: ByteArray): Pcm? = jsDecode(bytes.toJs()).await<JsAny?>()?.toPcm()

/** Needs the server to allow it (CORS); if it doesn't, voice questions say the recording couldn't be loaded. */
actual suspend fun fetchAudioBytes(url: String): ByteArray? = jsFetchBytes(url).await<JsAny?>()?.toByteArray()

actual fun startRecording(onStarted: (Boolean) -> Unit) {
    jsStopRecording()
    jsStartRecording(onStarted)
}

actual fun stopRecording(): Pcm? = jsStopRecording()?.toPcm()

actual val remindersSupported: Boolean = false

actual fun scheduleReminder(hour: Int?) {
    // Browsers can't wake the app at a set time; the setting isn't shown on the web.
}

// Effects are the same few byte arrays every time, so each gets one browser URL.
private val effectUrls = HashMap<ByteArray, String>()

actual fun playEffect(bytes: ByteArray) = jsPlayEffect(effectUrls.getOrPut(bytes) { jsBlobUrl(bytes.toJs()) })
