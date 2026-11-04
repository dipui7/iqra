package app.iqra.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Mono audio samples in -1..1. */
class Pcm(val samples: FloatArray, val rate: Int) {
    val seconds: Float get() = samples.size.toFloat() / rate
}

/**
 * Checks a learner's recording against reference recordings, karaoke style.
 *
 * Each recording becomes a sequence of MFCC frames (a standard summary of how speech sounds
 * from moment to moment). Two sequences are compared with dynamic time warping, which lines
 * them up even if one is spoken faster. The learner passes if their voice is closer to the
 * right recording than to every decoy.
 *
 * This measures how alike the recordings sound. It is not a pronunciation or tajweed check.
 */
object VoiceMatch {
    private const val RATE = 16_000
    private const val FRAME = 400 // 25 ms
    private const val HOP = 160 // 10 ms
    private const val FFT = 512
    private const val MELS = 26
    private const val COEFFS = 12 // MFCC 1..12; 0 (loudness) is left out

    sealed interface Result {
        /** Too quiet or too short to be speech. */
        data object NoVoice : Result
        data class Scored(val passed: Boolean, val score: Int, val distance: Float, val closestDecoy: Float) : Result
    }

    fun check(user: Pcm, target: Pcm, decoys: List<Pcm>): Result {
        val u = features(user) ?: return Result.NoVoice
        val t = features(target) ?: return Result.NoVoice
        val d = distance(u, t)
        val others = decoys.mapNotNull { features(it) }.map { distance(u, it) }
        val closest = others.minOrNull() ?: Float.MAX_VALUE
        // 100 when much closer to the target than to any decoy, 50 when equally close.
        val score = if (others.isEmpty()) 100 else (100 / (1 + exp(4.0 * (d - closest) / closest))).toInt().coerceIn(0, 100)
        return Result.Scored(passed = d < closest, score = score, distance = d, closestDecoy = closest)
    }

    /** MFCC frames of the voiced part, or null if there is (almost) no voice. */
    fun features(pcm: Pcm): List<FloatArray>? {
        val x = resample(pcm)
        if (x.size < FRAME * 2) return null
        val frames = (0..(x.size - FRAME) / HOP).map { i -> x.copyOfRange(i * HOP, i * HOP + FRAME) }
        val energy = frames.map { f -> sqrt(f.fold(0f) { a, v -> a + v * v } / f.size) }
        val peak = energy.maxOrNull() ?: return null
        if (peak < 0.01f) return null
        // Keep from the first to the last frame that is clearly louder than the background.
        val loud = energy.indices.filter { energy[it] > peak * 0.1f }
        val voiced = frames.subList(loud.first(), loud.last() + 1)
        if (voiced.size < 15) return null
        val mfcc = voiced.map(::mfcc)
        // Cepstral mean normalisation removes the microphone's and voice's constant colouring.
        val mean = FloatArray(COEFFS) { c -> mfcc.sumOf { it[c].toDouble() }.toFloat() / mfcc.size }
        return mfcc.map { f -> FloatArray(COEFFS) { f[it] - mean[it] } }
    }

    /** Average frame distance along the best time alignment of [a] and [b]. */
    fun distance(a: List<FloatArray>, b: List<FloatArray>): Float {
        val n = a.size
        val m = b.size
        val inf = Float.MAX_VALUE / 4
        var prev = FloatArray(m + 1) { inf }.also { it[0] = 0f }
        var prevLen = IntArray(m + 1)
        for (i in 1..n) {
            val cur = FloatArray(m + 1) { inf }
            val curLen = IntArray(m + 1)
            for (j in 1..m) {
                val cost = frameDistance(a[i - 1], b[j - 1])
                // Diagonal, then the two single steps.
                var best = prev[j - 1]; var len = prevLen[j - 1]
                if (prev[j] < best) { best = prev[j]; len = prevLen[j] }
                if (cur[j - 1] < best) { best = cur[j - 1]; len = curLen[j - 1] }
                cur[j] = best + cost
                curLen[j] = len + 1
            }
            prev = cur
            prevLen = curLen
        }
        return prev[m] / prevLen[m]
    }

    private fun frameDistance(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) { val d = a[i] - b[i]; s += d * d }
        return sqrt(s)
    }

    /** Linear resampling to 16 kHz, with pre-emphasis to bring out consonants. */
    private fun resample(pcm: Pcm): FloatArray {
        val ratio = pcm.rate.toDouble() / RATE
        val n = (pcm.samples.size / ratio).toInt()
        val out = FloatArray(n) { i ->
            val pos = i * ratio
            val k = pos.toInt()
            val f = (pos - k).toFloat()
            val a = pcm.samples[min(k, pcm.samples.size - 1)]
            val b = pcm.samples[min(k + 1, pcm.samples.size - 1)]
            a + (b - a) * f
        }
        for (i in n - 1 downTo 1) out[i] -= 0.97f * out[i - 1]
        return out
    }

    private val window = FloatArray(FRAME) { (0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1))).toFloat() }

    private val melFilters: List<FloatArray> by lazy {
        fun mel(hz: Double) = 2595 * log10(1 + hz / 700)
        fun hz(mel: Double) = 700 * (10.0.pow(mel / 2595) - 1)
        val bins = FFT / 2 + 1
        val points = (0..MELS + 1).map { hz(mel(60.0) + it * (mel(7600.0) - mel(60.0)) / (MELS + 1)) * FFT / RATE }
        (1..MELS).map { m ->
            FloatArray(bins) { k ->
                val l = points[m - 1]; val c = points[m]; val r = points[m + 1]
                when {
                    k < l || k > r -> 0f
                    k <= c -> ((k - l) / (c - l)).toFloat()
                    else -> ((r - k) / (r - c)).toFloat()
                }
            }
        }
    }

    private fun mfcc(frame: FloatArray): FloatArray {
        val re = FloatArray(FFT)
        val im = FloatArray(FFT)
        for (i in frame.indices) re[i] = frame[i] * window[i]
        fft(re, im)
        val power = FloatArray(FFT / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
        val logMel = melFilters.map { f ->
            var e = 0f
            for (k in power.indices) e += f[k] * power[k]
            ln(max(e, 1e-10f))
        }
        return FloatArray(COEFFS) { c ->
            val k = c + 1
            var s = 0.0
            for (m in 0 until MELS) s += logMel[m] * cos(PI * k * (m + 0.5) / MELS)
            s.toFloat()
        }
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(ang * k).toFloat()
                    val wi = kotlin.math.sin(ang * k).toFloat()
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
            }
            len = len shl 1
        }
    }
}
