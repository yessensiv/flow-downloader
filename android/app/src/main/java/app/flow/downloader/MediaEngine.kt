package app.flow.downloader

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONObject
import java.io.File

data class Choice(val selector: String, val label: String, val audio: Boolean, val mp3: Boolean = false)
data class Media(val url: String, val title: String, val choices: List<Choice>)

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
    private fun request(url: String) = YoutubeDLRequest(url).apply {
        addOption("--ignore-config")
        addOption("--no-playlist")
        addOption("--socket-timeout", "20")
        addOption("--retries", "1")
    }
    fun analyze(value: String): Media {
        val url = canonicalUrl(value)
        ensureCurrent()
        val req = request(url).apply { addOption("--dump-single-json"); addOption("--skip-download") }
        val json = JSONObject(YoutubeDL.getInstance().execute(req, "flow-analyze", null).out)
        require(!json.optBoolean("is_live") && json.optString("live_status") != "is_upcoming") { "LIVE" }
        val formats = json.getJSONArray("formats")
        val rows = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { !it.optBoolean("has_drm") && it.optString("url").startsWith("https://") }
        val audio = rows.filter { it.optString("vcodec") == "none" && it.optString("acodec", "none") != "none" }
        val choices = rows.filter { it.optInt("height") in 144..2160 && it.optString("vcodec", "none") != "none" }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("height") }.thenByDescending { it.optDouble("fps", 0.0) }
                .thenBy { if (it.optString("protocol") == "https") 0 else 1 })
            .distinctBy { "${it.optInt("height")}:${it.optDouble("fps", 0.0)}" }
            .mapNotNull { row ->
                val track = audio.maxByOrNull { it.optDouble("abr", 0.0) }
                val separate = row.optString("acodec", "none") == "none"
                if (separate && track == null) null else Choice(
                    row.getString("format_id") + if (separate) "+${track!!.getString("format_id")}" else "",
                    "${row.optInt("height")}p · ${row.optDouble("fps", 0.0).toInt()} fps", false)
            }.toMutableList()
        audio.maxByOrNull { it.optDouble("abr", 0.0) }?.let { track ->
            choices += Choice(track.getString("format_id"), track.optString("ext").uppercase(), true)
            choices += Choice(track.getString("format_id"), "MP3 · 192 kbps", true, true)
        }
        require(choices.isNotEmpty()) { "EMPTY" }
        return Media(url, json.optString("title", "YouTube"), choices)
    }
    fun download(media: Media, choice: Choice, progress: (Float) -> Unit): File {
        ensureCurrent()
        try { return downloadOnce(media, choice, progress) }
        catch (failure: Exception) {
            if (!(failure.message.orEmpty().contains("403") || failure.message.orEmpty().contains("Requested format is not available"))) throw failure
            progress(-1f)
            update()
            val fresh = analyze(media.url)
            val replacement = fresh.choices.firstOrNull { it.label == choice.label && it.audio == choice.audio && it.mp3 == choice.mp3 }
                ?: error("FORMAT_CHANGED")
            return downloadOnce(fresh, replacement, progress)
        }
    }
    private fun downloadOnce(media: Media, choice: Choice, progress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "download-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val req = request(media.url).apply {
                addOption("-f", choice.selector)
                addOption("-o", File(dir, "flow.%(ext)s").absolutePath)
                addOption("--max-filesize", "2G")
                if (!choice.audio) addOption("--merge-output-format", "mkv")
                if (choice.mp3) { addOption("-x"); addOption("--audio-format", "mp3"); addOption("--audio-quality", "192K") }
            }
            YoutubeDL.getInstance().execute(req, "flow-download") { value, _, _ -> progress(value) }
            return dir.listFiles()?.singleOrNull { it.isFile && it.extension in listOf("mp4", "mkv", "webm", "m4a", "mp3", "opus") }
                ?: error("EMPTY")
        } catch (error: Exception) { dir.deleteRecursively(); throw error }
    }
}
