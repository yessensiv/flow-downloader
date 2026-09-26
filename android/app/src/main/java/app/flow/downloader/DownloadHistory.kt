package app.flow.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedDownload(val uri: String, val title: String, val mime: String, val bytes: Long, val savedAt: Long)

/** Only completed, user-exported documents are listed, never temporary cache files. */
class DownloadHistory(context: Context, storeName: String = "saved_downloads") {
    private val preferences = context.getSharedPreferences(storeName, Context.MODE_PRIVATE)
    fun list(): List<SavedDownload> = runCatching {
        val array = JSONArray(preferences.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                SavedDownload(item.getString("uri"), item.getString("title"), item.getString("mime"),
                    item.getLong("bytes"), item.getLong("savedAt"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun add(item: SavedDownload) = write(listOf(item) + list().filterNot { it.uri == item.uri })
    fun remove(uri: String) = write(list().filterNot { it.uri == uri })
    private fun write(items: List<SavedDownload>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("uri", item.uri); put("title", item.title); put("mime", item.mime)
            put("bytes", item.bytes); put("savedAt", item.savedAt)
        }) }
        check(preferences.edit().putString("items", array.toString()).commit()) { "Cannot save download history" }
    }
}
