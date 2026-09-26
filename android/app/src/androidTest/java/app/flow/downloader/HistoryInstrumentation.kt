package app.flow.downloader

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Device checks without additional test dependencies. Never touches real history or files. */
class HistoryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val name = "history_test_${System.nanoTime()}"
        val preferences = targetContext.getSharedPreferences(name, 0)
        val result = Bundle()
        try {
            val store = DownloadHistory(targetContext, name)
            check(store.list().isEmpty())
            val first = SavedDownload("content://test/one", "Трек 🎵", "audio/mpeg", 123, 1000)
            val second = SavedDownload("content://test/two", "Video", "video/mp4", 456, 2000)
            store.add(first); store.add(second)
            check(DownloadHistory(targetContext, name).list() == listOf(second, first))
            store.add(first.copy(title = "Updated"))
            check(store.list().size == 2 && store.list().first().title == "Updated")
            store.remove(first.uri)
            check(DownloadHistory(targetContext, name).list() == listOf(second))
            preferences.edit().putString("items", "broken JSON").commit()
            check(store.list().isEmpty())
            store.add(first)
            check(store.list() == listOf(first))
            result.putString("stream", "PASS: empty, persistence, order, Unicode, deduplication, removal, corrupt-data recovery\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { preferences.edit().clear().commit() }
    }
}
