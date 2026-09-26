package app.flow.downloader

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONObject
import java.io.File

data class Choice(val selector: String, val label: String, val audio: Boolean, val mp3: Boolean = false, val extractAudio: Boolean = false)
data class Media(val url: String, val title: String, val choices: List<Choice>, val thumbnail: String = "")
data class PlaylistEntry(val url: String, val title: String, val channel: String, val duration: String, val thumbnail: String)
data class Playlist(val title: String, val channel: String, val entries: List<PlaylistEntry>)

class MediaEngine(private val context: Context) {
    private val preferences = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
    private var updated = false
    fun initialize() {
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
    }
    fun update() {
        initialize()
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
        updated = true
    }
    fun version(): String = YoutubeDL.getInstance().version(context) ?: "bundled"
    private fun ensureCurrent() {
        initialize()
        if (!updated && System.currentTimeMillis() - preferences.getLong("checked", 0) > 86_400_000L) update()
    }

    fun canonicalUrl(value: String): String {
        val uri = Uri.parse(value.trim())
        require(uri.scheme in listOf("https", "http")) { "URL" }
        val host = uri.host?.lowercase()
        val id = when (host) {
            "youtu.be" -> uri.pathSegments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                if (uri.path == "/watch") uri.getQueryParameter("v")
                else if (uri.pathSegments.firstOrNull() in listOf("shorts", "live", "embed")) uri.pathSegments.getOrNull(1) else null
            else -> null
        }
        require(id != null && Regex("[A-Za-z0-9_-]{11}").matches(id)) { "URL" }
        return "https://www.youtube.com/watch?v=$id"
    }
    fun playlistUrl(value: String): String? {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) return null
        val id = uri.getQueryParameter("list") ?: return null
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(id)) return null
        return "https://www.youtube.com/playlist?list=$id"
    }
    private fun request(url: String, playlist: Boolean = false) = YoutubeDLRequest(url).apply {
        addOption("--ignore-config")
        if (!playlist) addOption("--no-playlist")
        addOption("--socket-timeout", "20")
        addOption("--retries", "1")
    }
    fun analyzePlaylist(value: String, processId: String = "flow-playlist-analyze"): Playlist {
        val url = playlistUrl(value) ?: error("URL")
        ensureCurrent()
        val req = request(url, playlist = true).apply {
            addOption("--flat-playlist")
            addOption("--playlist-end", "50")
            addOption("--dump-single-json")
            addOption("--skip-download")
        }
        val json = JSONObject(YoutubeDL.getInstance().execute(req, processId, null).out)
        val entriesJson = json.optJSONArray("entries") ?: error("EMPTY_PLAYLIST")
        val entries = (0 until entriesJson.length()).mapNotNull { index ->
            val item = entriesJson.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
                ?: runCatching { Uri.parse(item.optString("url")).getQueryParameter("v") }.getOrNull()
                    ?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
                ?: return@mapNotNull null
            val thumb = item.optString("thumbnail").takeIf { it.startsWith("https://") }
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val seconds = item.optLong("duration", -1)
            val duration = if (seconds < 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)
            PlaylistEntry("https://www.youtube.com/watch?v=$id", item.optString("title").ifBlank { "YouTube" },
                item.optString("channel").ifBlank { item.optString("uploader") }, duration, thumb)
        }.distinctBy { it.url }
        require(entries.isNotEmpty()) { "EMPTY_PLAYLIST" }
        return Playlist(json.optString("title").ifBlank { "YouTube playlist" },
            json.optString("channel").ifBlank { json.optString("uploader") }, entries)
    }
    fun analyze(value: String, processId: String = "flow-analyze"): Media {
        val url = canonicalUrl(value)
        ensureCurrent()
        val req = request(url).apply { addOption("--dump-single-json"); addOption("--skip-download") }
        val json = JSONObject(YoutubeDL.getInstance().execute(req, processId, null).out)
        require(!json.optBoolean("is_live") && json.optString("live_status") != "is_upcoming") { "LIVE" }
        val formats = json.getJSONArray("formats")
        val rows = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { !it.optBoolean("has_drm") && it.optString("url").startsWith("https://") }
        val audioOnly = rows.filter { it.optString("vcodec") == "none" && it.optString("acodec", "none") != "none" }
        val embeddedAudio = rows.filter { it.optString("acodec", "none") != "none" }
        val audioSources = audioOnly.ifEmpty { embeddedAudio }
        val extractAudio = audioOnly.isEmpty()
        val choices = rows.filter { it.optInt("height") in 144..2160 && it.optString("vcodec", "none") != "none" }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("height") }.thenByDescending { it.optDouble("fps", 0.0) }
                .thenBy { if (it.optString("protocol") == "https") 0 else 1 })
            .distinctBy { "${it.optInt("height")}:${it.optDouble("fps", 0.0)}" }
            .mapNotNull { row ->
                val track = audioOnly.maxByOrNull { it.optDouble("abr", it.optDouble("tbr", 0.0)) }
                val separate = row.optString("acodec", "none") == "none"
                if (separate && track == null) null else Choice(
                    row.getString("format_id") + if (separate) "+${track!!.getString("format_id")}" else "",
                    "${row.optInt("height")}p · ${row.optDouble("fps", 0.0).toInt()} fps", false)
            }.toMutableList()
        audioSources.maxByOrNull { it.optDouble("abr", it.optDouble("tbr", 0.0)) }?.let { track ->
            val audioLabel = if (extractAudio) "M4A · только звук" else track.optString("ext").uppercase()
            choices += Choice(track.getString("format_id"), audioLabel, true, extractAudio = extractAudio)
            choices += Choice(track.getString("format_id"), "MP3 · 192 kbps", true, mp3 = true, extractAudio = extractAudio)
        }
        require(choices.isNotEmpty()) { "EMPTY" }
        return Media(url, json.optString("title", "YouTube"), choices, json.optString("thumbnail"))
    }
    fun download(media: Media, choice: Choice, progress: (Float, Long, String) -> Unit): File {
        ensureCurrent()
        try { return downloadOnce(media, choice, progress) }
        catch (failure: Exception) {
            if (!(failure.message.orEmpty().contains("403") || failure.message.orEmpty().contains("Requested format is not available"))) throw failure
            progress(-1f, -1L, "")
            update()
            val fresh = analyze(media.url)
            val replacement = fresh.choices.firstOrNull { it.label == choice.label && it.audio == choice.audio && it.mp3 == choice.mp3 }
                ?: error("FORMAT_CHANGED")
            return downloadOnce(fresh, replacement, progress)
        }
    }
    private fun downloadOnce(media: Media, choice: Choice, progress: (Float, Long, String) -> Unit): File {
        val dir = File(context.cacheDir, "download-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val req = request(media.url).apply {
                addOption("-f", choice.selector)
                addOption("-o", File(dir, "flow.%(ext)s").absolutePath)
                addOption("--max-filesize", "2G")
                if (!choice.audio) addOption("--merge-output-format", "mkv")
                if (choice.mp3) { addOption("-x"); addOption("--audio-format", "mp3"); addOption("--audio-quality", "192K") }
                else if (choice.extractAudio) { addOption("-x"); addOption("--audio-format", "m4a"); addOption("--audio-quality", "0") }
            }
            YoutubeDL.getInstance().execute(req, "flow-download") { value, eta, line -> progress(value, eta, line) }
            return dir.listFiles()?.singleOrNull { it.isFile && it.extension in listOf("mp4", "mkv", "webm", "m4a", "mp3", "opus") }
                ?: error("EMPTY")
        } catch (error: Exception) { dir.deleteRecursively(); throw error }
    }
}
