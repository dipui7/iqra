package app.iqra.engine

import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Progress as one block of text, to move it to a new phone or keep it safe: copy it, send it to
 * yourself, paste it back. It's the saved progress in Base64, behind a short prefix so a wrong
 * paste is recognised.
 */
@OptIn(ExperimentalEncodingApi::class)
object Backup {
    private const val PREFIX = "ALIFUP1:"
    private val json = Json { ignoreUnknownKeys = true }

    fun export(progress: Progress): String =
        PREFIX + Base64.encode(json.encodeToString(Progress.serializer(), progress).encodeToByteArray())

    /** The progress in a backup code, or null if the text isn't one (spaces and line breaks are ignored). */
    fun import(text: String): Progress? {
        val code = text.filterNot { it.isWhitespace() }
        if (!code.startsWith(PREFIX)) return null
        return runCatching {
            json.decodeFromString(Progress.serializer(), Base64.decode(code.removePrefix(PREFIX)).decodeToString())
        }.getOrNull()
    }
}
