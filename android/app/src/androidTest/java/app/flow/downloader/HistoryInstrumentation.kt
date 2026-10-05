package app.flow.downloader

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Device checks without additional test dependencies. Never touches real history or files. */
class HistoryInstrumentation : Instrumentation() {
    private var exportChecks = false
    private var designChecks = false
    private var transferChecks = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); exportChecks = arguments?.getString("exports") == "true"; designChecks = arguments?.getString("design") == "true"; transferChecks = arguments?.getString("transfer") == "true"; start() }
    override fun onStart() {
        val name = "history_test_${System.nanoTime()}"
        val preferences = targetContext.getSharedPreferences(name, 0)
        val result = Bundle()
        try {
            if (transferChecks) {
                result.putString("stream", TransferChecks.run(targetContext) + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            FeatureChecks.run(targetContext)
            if (designChecks) DesignChecks.run(this)
            if (exportChecks) ExportChecks.run(context, targetContext)
            val store = DownloadHistory(targetContext, name)
            check(store.list().isEmpty())
            val source = Media("https://www.youtube.com/watch?v=0FGtKwcxOrQ", "Renamed", emptyList())
            val duplicate = SavedDownload("content://test/video", "Original", "video/mp4", 123, 1000, sourceUrl = "https://youtu.be/0FGtKwcxOrQ")
            store.add(duplicate)
            check(DownloadHistory(targetContext, name).duplicate(source, false) { true } == duplicate)
            check(store.duplicate(source, false) { false } == null)
            check(store.duplicate(source, false) { throw SecurityException() } == null)
            check(store.duplicate(source, true) { true } == null)
            check(store.duplicate(source.copy(url = "https://youtu.be/rKJuFfmG_Gk"), false) { true } == null)
            val legacy = duplicate.copy(uri = "content://test/legacy", sourceUrl = "", thumbnail = "https://i.ytimg.com/vi/0FGtKwcxOrQ/hqdefault.jpg")
            store.add(legacy)
            check(store.duplicate(source, false) { it.uri == legacy.uri } == legacy)
            store.remove(legacy.uri); store.remove(duplicate.uri)
            val first = SavedDownload("content://test/one", "Трек 🎵", "audio/mpeg", 123, 1000)
            val second = SavedDownload("content://test/two", "Video", "video/mp4", 456, 2000)
            store.add(first); store.add(second)
            check(first.isAudio && !second.isAudio)
            check(first.copy(mime = "audio/webm").isAudio)
            check(!first.copy(mime = "video/webm").isAudio)
            check(DownloadHistory(targetContext, name).list() == listOf(second, first))
            store.add(first.copy(title = "Updated"))
            check(store.list().size == 2 && store.list().first().title == "Updated")
            store.remove(first.uri)
            check(DownloadHistory(targetContext, name).list() == listOf(second))
            preferences.edit().putString("items", "broken JSON").commit()
            check(store.list().isEmpty())
            store.add(first)
            check(store.list() == listOf(first))
            check(!store.deleteFile(first) { false })
            check(store.list() == listOf(first))
            check(runCatching { store.deleteFile(first) { throw SecurityException("denied") } }.isFailure)
            check(store.list() == listOf(first))
            var target = ""
            check(store.deleteFile(first) { uri -> target = uri; true })
            check(target == first.uri && store.list().isEmpty())
            result.putString("stream", "PASS: history persistence, categories including WEBM, refused/denied deletion preserves entry, confirmed deletion targets exact URI\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { preferences.edit().clear().commit() }
    }
}
