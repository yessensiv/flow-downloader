package app.flow.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Deterministic checks: no YouTube request, no history changes, no saved user files. */
object FeatureChecks {
    fun run(app: Context) {
        check(AppUpdates.isNewer("0.21.0", "0.20.1"))
        check(AppUpdates.isNewer("v1.0.0", "0.99.99"))
        check(!AppUpdates.isNewer("0.20.1", "0.20.1"))
        check(!AppUpdates.isNewer("0.19.9", "0.20.1"))
        check(runCatching { AppUpdates.isNewer("0.21.0-beta", "0.20.1") }.isFailure)
        val selected = Choice("137+140-ru", "1080p · 30 fps", false, format = "mp4", bitrate = 256,
            metadata = true, cover = false, deviceCompatible = false, codec = "H.264", language = "ru",
            languageLabel = "ru · original", estimatedBytes = 5000000, duration = 60.0)
        val media = Media("https://www.youtube.com/watch?v=0FGtKwcxOrQ", "Test", listOf(selected, selected.copy(codec = "AV1", selector = "399+140-ru")), artist = "Artist", fileName = "My file")
        check(MediaStorage.displayName(media, "mp4") == "My file.mp4")
        check(MediaStorage.displayName(media.copy(fileName = "My file.MP4"), "mp4") == "My file.MP4")
        check(MediaStorage.displayName(media.copy(fileName = "../bad/name"), "m4a") == "_bad_name.m4a")
        check(MediaTransfer.decode(MediaTransfer.encode(media)) == media)
        check(MediaTransfer.choice(MediaTransfer.choice(selected)) == selected)
        val engine = MediaEngine(app)
        fun video(id: String, codec: String) = JSONObject().apply {
            put("format_id", id); put("url", "https://example.test/video"); put("height", 1080); put("width", 1920)
            put("fps", 30); put("vcodec", codec); put("acodec", "none"); put("filesize", 1000000)
        }
        fun audio(id: String, lang: String, original: Boolean) = JSONObject().apply {
            put("format_id", id); put("url", "https://example.test/audio"); put("vcodec", "none"); put("acodec", "mp4a.40.2")
            put("ext", "m4a"); put("language", lang); put("language_preference", if (original) 10 else 0)
            put("format_note", if (original) "original" else "auto-dubbed"); put("abr", 128); put("filesize", 100000)
        }
        val parsed = engine.parseMedia(media.url, JSONObject().apply {
            put("title", "Test"); put("duration", 60)
            put("formats", JSONArray().put(video("137", "avc1.640028")).put(video("399", "av01.0.08M.08"))
                .put(audio("140-ru", "ru", true)).put(audio("140-en", "en", false)))
        })
        check(parsed.choices.filterNot { it.audio }.map { it.codec }.toSet() == setOf("H.264", "AV1"))
        check(parsed.choices.filterNot { it.audio }.map { it.language }.toSet() == setOf("ru", "en"))
        check(parsed.choices.filterNot { it.audio }.all { it.estimatedBytes == 1100000L })
        check(parsed.choices.filter { it.audio && it.format == "mp3" }.size == 2)
        check(parsed.choices.all { it.label.contains("1080") || it.audio })
        val folder = File(app.cacheDir, "feature-check")
        val command = engine.exportRequest(media, selected, folder, resumable = true).buildCommand()
        check("--part" in command && "--continue" in command && "--no-part" !in command)
        check(command.contains(selected.selector))
        check("--embed-metadata" in command && "--embed-thumbnail" !in command)
        check("--replace-in-metadata" in command && media.artist in command)
        val plain = engine.exportRequest(media, selected.copy(metadata = false), folder).buildCommand()
        check("--replace-in-metadata" !in plain && "--embed-metadata" !in plain)
    }
}
