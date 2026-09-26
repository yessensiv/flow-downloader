package app.flow.downloader

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MediaStorage {
    fun mime(file: File, audio: Boolean): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        else -> "application/octet-stream"
    }

    fun save(context: Context, file: File, media: Media, audio: Boolean): String {
        check(android.os.Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val title = media.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(100).ifBlank { "Flow" }
        val mime = mime(file, audio)
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.${file.extension}")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "Music/Flow" else "Movies/Flow")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Could not create media entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Could not open media destination")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            DownloadHistory(context).add(SavedDownload(uri.toString(), title, mime, file.length(), System.currentTimeMillis(), media.thumbnail))
            return uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
