package app.flow.downloader

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONObject
import java.io.File

data class Choice(val selector: String, val label: String, val audio: Boolean, val mp3: Boolean = false, val extractAudio: Boolean = false,
    val format: String = "", val bitrate: Int = 192, val metadata: Boolean = false, val cover: Boolean = false,
    val deviceCompatible: Boolean = true, val codec: String = "", val language: String = "",
    val languageLabel: String = "", val estimatedBytes: Long = 0, val duration: Double = 0.0)
data class Media(val url: String, val title: String, val choices: List<Choice>, val thumbnail: String = "",
    val artist: String = "", val fileName: String = "")

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
        return parseMedia(url, json)
    }
    internal fun parseMedia(url: String, json: JSONObject): Media {
        require(!json.optBoolean("is_live") && json.optString("live_status") != "is_upcoming") { "LIVE" }
        val formats = json.getJSONArray("formats")
        val rows = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { !it.optBoolean("has_drm") && it.optString("url").startsWith("https://") }
        val audioOnly = rows.filter { it.optString("vcodec") == "none" && it.optString("acodec", "none") != "none" }
        val embeddedAudio = rows.filter { it.optString("acodec", "none") != "none" }
        val audioSources = audioOnly.ifEmpty { embeddedAudio }
        val extractAudio = audioOnly.isEmpty()
        val duration = json.optDouble("duration", 0.0)
        fun size(row: JSONObject): Long = row.optLong("filesize").takeIf { it > 0 }
            ?: row.optLong("filesize_approx").takeIf { it > 0 }
            ?: (row.optDouble("tbr", row.optDouble("abr", 0.0)) * 1000 * duration / 8).toLong().coerceAtLeast(0)
        fun language(row: JSONObject) = row.optString("language", "").takeUnless { it == "null" }.orEmpty()
        fun languageLabel(row: JSONObject): String = language(row).ifBlank { "und" } +
            if (row.optString("format_note").contains("original", true)) " · original" else if (row.optString("format_note").contains("auto", true)) " · auto" else ""
        fun codec(row: JSONObject) = when {
            row.optString("vcodec").startsWith("avc") -> "H.264"
            row.optString("vcodec").startsWith("vp9") || row.optString("vcodec").startsWith("vp09") -> "VP9"
            row.optString("vcodec").startsWith("vp8") || row.optString("vcodec").startsWith("vp08") -> "VP8"
            row.optString("vcodec").startsWith("av01") -> "AV1"
            else -> row.optString("vcodec").substringBefore('.')
        }
        val tracks = audioSources.sortedWith(compareByDescending<JSONObject> { it.optString("format_note").contains("original", true) }
            .thenByDescending { it.optInt("language_preference", -1) }
            .thenByDescending { it.optDouble("abr", it.optDouble("tbr", 0.0)) }).distinctBy { language(it) }
        val choices = rows.filter { it.optInt("height") in 144..2160 && it.optString("vcodec", "none") != "none" }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("height") }.thenByDescending { it.optDouble("fps", 0.0) }
                .thenBy { if (it.optString("protocol") == "https") 0 else 1 })
            .flatMap { row ->
                val separate = row.optString("acodec", "none") == "none"
                val availableTracks = if (separate) tracks.filter { it.optString("vcodec") == "none" } else listOf(row)
                availableTracks.map { track -> Choice(
                    row.getString("format_id") + if (separate) "+${track.getString("format_id")}" else "",
                    "${row.optInt("height")}p · ${row.optDouble("fps", 0.0).toInt()} fps", false,
                    deviceCompatible = supportsVideo(row.optString("vcodec"), row.optInt("width"), row.optInt("height"), row.optDouble("fps", 30.0)),
                    codec = codec(row), language = language(track), languageLabel = languageLabel(track),
                    estimatedBytes = if (separate) { if (size(row) > 0 && size(track) > 0) size(row) + size(track) else 0 } else size(row), duration = duration) }
            }.sortedBy { if (it.deviceCompatible) 0 else 1 }
            .distinctBy { "${it.label}:${it.codec}:${it.language}" }.toMutableList()
        tracks.forEach { track ->
            val audioLabel = if (extractAudio) "M4A · только звук" else track.optString("ext").uppercase() + " · original"
            val source = Choice(track.getString("format_id"), audioLabel, true, extractAudio = extractAudio,
                language = language(track), languageLabel = languageLabel(track), estimatedBytes = if (extractAudio) 0 else size(track), duration = duration)
            choices += source
            listOf("mp3", "m4a", "opus", "flac", "wav").forEach { format ->
                choices += source.copy(label = format.uppercase(), format = format, estimatedBytes = 0)
            }
        }
        require(choices.isNotEmpty()) { "EMPTY" }
        return Media(url, json.optString("title", "YouTube"), choices, json.optString("thumbnail"),
            json.optString("artist").takeUnless { it == "null" }.orEmpty())
    }
    fun downloadResumable(media: Media, choice: Choice, dir: File, processId: String, interrupted: () -> Boolean,
        progress: (Float, Long, String) -> Unit): File {
        ensureCurrent()
        check(!interrupted()) { "INTERRUPTED" }
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create download folder" }
        fun execute(current: Media, selected: Choice) {
            val req = exportRequest(current, selected, dir, resumable = true)
            YoutubeDL.getInstance().execute(req, processId) { value, eta, line -> progress(value, eta, line) }
        }
        try { execute(media, choice) }
        catch (failure: Exception) {
            if (interrupted() || !(failure.message.orEmpty().contains("403") || failure.message.orEmpty().contains("Requested format is not available"))) throw failure
            progress(-1f, -1L, "")
            update()
            check(!interrupted()) { "INTERRUPTED" }
            val fresh = analyze(media.url)
            val replacement = fresh.choices.firstOrNull { it.label == choice.label && it.audio == choice.audio && it.codec == choice.codec && it.language == choice.language && (if (choice.audio) it.format == choice.format else true) }
                ?: error("FORMAT_CHANGED")
            // A changed format ID must never reuse bytes downloaded from a different stream.
            if (replacement.selector != choice.selector) {
                dir.listFiles()?.forEach { file -> if (file.isFile) check(file.delete()) { "Cannot clear stale download" } }
            }
            check(!interrupted()) { "INTERRUPTED" }
            execute(fresh.copy(title = media.title, artist = media.artist, fileName = media.fileName), replacement.copy(format = choice.format, bitrate = choice.bitrate, metadata = choice.metadata, cover = choice.cover))
        }
        check(!interrupted()) { "INTERRUPTED" }
        return dir.listFiles()?.singleOrNull { it.isFile && it.nameWithoutExtension == "flow" && it.extension in listOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "flac", "wav") }
            ?: error("EMPTY")
    }

    private fun supportsVideo(codec: String, width: Int, height: Int, fps: Double): Boolean {
        if (width <= 0 || height <= 0) return false
        val mime = when {
            codec.startsWith("avc1", true) || codec.contains("h264", true) -> "video/avc"
            codec.startsWith("hev1", true) || codec.startsWith("hvc1", true) || codec.contains("hevc", true) -> "video/hevc"
            codec.startsWith("vp9", true) || codec.startsWith("vp09", true) -> "video/x-vnd.on2.vp9"
            codec.startsWith("av01", true) || codec.contains("av1", true) -> "video/av01"
            codec.startsWith("vp8", true) || codec.startsWith("vp08", true) -> "video/x-vnd.on2.vp8"
            else -> return false
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence().filterNot { it.isEncoder }
                .flatMap { info -> info.supportedTypes.asSequence().filter { it.equals(mime, true) }.map { info } }
                .any { info ->
                    val caps = info.getCapabilitiesForType(mime).videoCapabilities ?: return@any false
                    caps.areSizeAndRateSupported(width, height, fps.coerceAtLeast(1.0))
                }
        }.getOrDefault(false)
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
            return downloadOnce(fresh.copy(title = media.title, artist = media.artist, fileName = media.fileName), replacement.copy(format = choice.format, bitrate = choice.bitrate, metadata = choice.metadata, cover = choice.cover), progress)
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
    internal fun exportRequest(media: Media, choice: Choice, dir: File, resumable: Boolean = false): YoutubeDLRequest = request(media.url).apply {
                addOption("-f", choice.selector)
                addOption("-o", File(dir, "flow.%(ext)s").absolutePath)
                // Service sessions keep .part files for HTTP Range / fragment continuation.
                // One-shot offline exports retain the direct-output mode.
                addOption(if (resumable) "--part" else "--no-part")
                if (resumable) addOption("--continue")
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
                if (choice.metadata) {
                    addOption("--embed-metadata")
                    addOption("--parse-metadata", "title:%(meta_title)s")
                    addCommands(listOf("--replace-in-metadata", "meta_title", "(?s)^.*$", media.title.replace("\\", "\\\\")))
                    if (media.artist.isNotBlank()) {
                        addOption("--parse-metadata", "title:%(meta_artist)s")
                        addCommands(listOf("--replace-in-metadata", "meta_artist", "(?s)^.*$", media.artist.replace("\\", "\\\\")))
                    }
                }
                if (choice.cover) {
                    addOption("--embed-thumbnail"); addOption("--convert-thumbnails", "jpg")
                }
    }
}
