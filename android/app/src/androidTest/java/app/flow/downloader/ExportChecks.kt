package app.flow.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

/** Offline integration checks against the Android-bundled Python and FFmpeg. */
object ExportChecks {
    fun run(test: Context, app: Context) {
        val root = File(app.cacheDir, "export-check-${System.nanoTime()}").apply { mkdirs() }
        try {
            val input = File(root, "source.m4a")
            val cover = File(root, "cover.jpg")
            test.assets.open("export-source.m4a").use { src -> input.outputStream().use { src.copyTo(it) } }
            test.assets.open("export-cover.jpg").use { src -> cover.outputStream().use { src.copyTo(it) } }
            val info = File(root, "info.json").apply { writeText(JSONObject().apply {
                put("id", "flow-export-test"); put("title", "Flow Export Test"); put("artist", "Flow QA")
                put("url", input.toURI().toString()); put("webpage_url", input.toURI().toString())
                put("ext", "m4a"); put("extractor", "generic"); put("extractor_key", "Generic")
                put("thumbnail", cover.toURI().toString())
            }.toString()) }
            val engine = MediaEngine(app).apply { initialize() }
            for (format in listOf("mp3", "m4a", "opus", "flac", "wav")) {
                val dir = File(root, format).apply { mkdirs() }
                val choice = Choice("best", format, true, format = format, bitrate = 128, metadata = true, cover = format != "wav")
                val req = engine.exportRequest(Media(input.toURI().toString(), "Test", listOf(choice)), choice, dir).apply {
                    addOption("--enable-file-urls"); addOption("--load-info-json", info.absolutePath)
                }
                val offline = YoutubeDLRequest(emptyList<String>()).addCommands(req.buildCommand().filter { it != input.toURI().toString() })
                YoutubeDL.getInstance().execute(offline, "flow-export-test", null)
                val file = File(dir, "flow.$format")
                check(file.length() > 0) { "Missing $format output" }
                if (format in listOf("mp3", "m4a")) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) == "Flow Export Test")
                        check(retriever.embeddedPicture != null) { "Missing $format cover" }
                    } finally { retriever.release() }
                }
            }
        } finally { root.deleteRecursively() }
    }
}
