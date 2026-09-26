package app.flow.downloader

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONObject
import java.io.File

data class Choice(val selector: String, val label: String, val audio: Boolean, val mp3: Boolean = false, val extractAudio: Boolean = false,
    val format: String = "", val bitrate: Int = 192, val metadata: Boolean = false, val cover: Boolean = false)
data class Media(val url: String, val title: String, val choices: List<Choice>, val thumbnail: String = "")

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
            val audioLabel = if (extractAudio) "M4A · только звук" else track.optString("ext").uppercase() + " · original"
            choices += Choice(track.getString("format_id"), audioLabel, true, extractAudio = extractAudio)
            listOf("mp3", "m4a", "opus", "flac", "wav").forEach { format ->
                choices += Choice(track.getString("format_id"), format.uppercase(), true, format = format)
            }
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
            return downloadOnce(fresh, replacement.copy(format = choice.format, bitrate = choice.bitrate, metadata = choice.metadata, cover = choice.cover), progress)
        }
    }
    private fun downloadOnce(media: Media, choice: Choice, progress: (Float, Long, String) -> Unit): File {
        val dir = File(context.cacheDir, "download-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val req = exportRequest(media, choice, dir)
            YoutubeDL.getInstance().execute(req, "flow-download") { value, eta, line -> progress(value, eta, line) }
            return dir.listFiles()?.singleOrNull { it.isFile && it.extension in listOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "flac", "wav") }
                ?: error("EMPTY")
        } catch (error: Exception) { dir.deleteRecursively(); throw error }
    }
    internal fun exportRequest(media: Media, choice: Choice, dir: File): YoutubeDLRequest = request(media.url).apply {
                addOption("-f", choice.selector)
                addOption("-o", File(dir, "flow.%(ext)s").absolutePath)
                addOption("--max-filesize", "2G")
                if (!choice.audio) {
                    val container = choice.format.ifBlank { "mkv" }
                    addOption("--merge-output-format", container)
                    addOption("--remux-video", container)
                }
                if (choice.audio && choice.format.isNotEmpty()) {
                    addOption("-x"); addOption("--audio-format", choice.format)
                    if (choice.format in listOf("mp3", "m4a", "opus")) {
                        // A different intermediate container prevents yt-dlp from skipping an
                        // already-matching codec, so the selected bitrate is always applied.
                        addOption("--use-postprocessor", "FFmpegVideoRemuxer:preferedformat=mka")
                        addOption("--audio-quality", "${choice.bitrate}K")
                        addOption("--postprocessor-args", "ExtractAudio+ffmpeg_o:-c:a ${when (choice.format) { "mp3" -> "libmp3lame"; "opus" -> "libopus"; else -> "aac" }} -b:a ${choice.bitrate}k")
                    }
                }
                else if (choice.mp3) { addOption("-x"); addOption("--audio-format", "mp3"); addOption("--audio-quality", "192K") }
                else if (choice.extractAudio) { addOption("-x"); addOption("--audio-format", "m4a"); addOption("--audio-quality", "0") }
                if (choice.metadata) addOption("--embed-metadata")
                if (choice.cover) {
                    addOption("--embed-thumbnail"); addOption("--convert-thumbnails", "jpg")
                }
    }
}
