package app.flow.downloader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Saves a completed file to the user's media library and records its URI once. */
object MediaStoreSaver {
    fun mimeFor(file: File, audio: Boolean): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        else -> "application/octet-stream"
    }

    fun save(context: Context, file: File, rawTitle: String, mime: String, thumbnail: String): SavedDownload {
        check(Build.VERSION.SDK_INT >= 29) { "MEDIASTORE_UNAVAILABLE" }
        val resolver = context.contentResolver
        val safeTitle = rawTitle.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(100).ifBlank { "Flow" }
        val audio = mime.startsWith("audio/")
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val relativePath = if (audio) "Music/Flow" else "Movies/Flow"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeTitle.${file.extension.lowercase()}")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create media entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Could not open media destination")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) {
                "Could not finish media file"
            }
            val saved = SavedDownload(uri.toString(), safeTitle, mime, file.length(), System.currentTimeMillis(), thumbnail)
            DownloadHistory(context.applicationContext).add(saved)
            return saved
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
