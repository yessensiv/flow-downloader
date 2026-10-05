package app.flow.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedDownload(val uri: String, val title: String, val mime: String, val bytes: Long, val savedAt: Long, val thumbnail: String = "", val sourceUrl: String = "") {
    val isAudio: Boolean get() = mime.startsWith("audio/", ignoreCase = true)
    fun matchesSource(media: Media, audio: Boolean): Boolean {
        val id = DownloadIdentity.sourceId(media.url) ?: return false
        return isAudio == audio && (DownloadIdentity.sourceId(sourceUrl) ?: DownloadIdentity.thumbnailId(thumbnail)) == id
    }
}

internal object DownloadIdentity {
    fun sourceId(value: String): String? = runCatching {
        val uri = java.net.URI(value.trim())
        if (uri.scheme !in listOf("https", "http")) return@runCatching null
        val segments = uri.path.orEmpty().trim('/').split('/')
        val id = when (uri.host?.lowercase()) {
            "youtu.be" -> segments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "v" }
                    ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                else if (segments.firstOrNull() in listOf("shorts", "live", "embed")) segments.getOrNull(1) else null
            else -> null
        }
        id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }.getOrNull()
    fun thumbnailId(value: String): String? = runCatching {
        val uri = java.net.URI(value)
        val host = uri.host.orEmpty().lowercase()
        if (uri.scheme !in listOf("https", "http") || (host != "i.ytimg.com" && !host.endsWith(".ytimg.com"))) return@runCatching null
        uri.path.orEmpty().trim('/').split('/').takeIf { it.firstOrNull() in listOf("vi", "vi_webp") }?.getOrNull(1)
            ?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }.getOrNull()
}

/** Only completed, user-exported documents are listed, never temporary cache files. */
class DownloadHistory(context: Context, storeName: String = "saved_downloads") {
    private val preferences = context.getSharedPreferences(storeName, Context.MODE_PRIVATE)
    fun list(): List<SavedDownload> = runCatching {
        val array = JSONArray(preferences.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                SavedDownload(item.getString("uri"), item.getString("title"), item.getString("mime"),
                    item.getLong("bytes"), item.getLong("savedAt"), item.optString("thumbnail", ""), item.optString("sourceUrl", ""))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun add(item: SavedDownload) = write(listOf(item) + list().filterNot { it.uri == item.uri })
    fun remove(uri: String) = write(list().filterNot { it.uri == uri })
    /** Match the source, never a user-editable title; ignore missing/inaccessible files. */
    fun duplicate(media: Media, audio: Boolean, accessible: (SavedDownload) -> Boolean): SavedDownload? {
        return list().firstOrNull { item ->
            item.matchesSource(media, audio) &&
                runCatching { accessible(item) }.getOrDefault(false)
        }
    }
    /** Never remove the history entry when the provider refuses deletion. */
    fun deleteFile(item: SavedDownload, deleteDocument: (String) -> Boolean): Boolean {
        if (!deleteDocument(item.uri)) return false
        remove(item.uri)
        return true
    }
    private fun write(items: List<SavedDownload>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("uri", item.uri); put("title", item.title); put("mime", item.mime)
            put("bytes", item.bytes); put("savedAt", item.savedAt)
            put("thumbnail", item.thumbnail)
            put("sourceUrl", item.sourceUrl)
        }) }
        check(preferences.edit().putString("items", array.toString()).commit()) { "Cannot save download history" }
    }
}
