package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class DownloadsActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var history: DownloadHistory
    private var english = false
    private val worker = Executors.newSingleThreadExecutor()
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val lime = Color.rgb(194, 255, 112)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = intent.getBooleanExtra("english", false)
        history = DownloadHistory(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 20, 16)); isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        scroll.addView(list); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        scroll.requestApplyInsets()
        render()
    }
    private fun label(value: String, size: Int, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (muted) Color.rgb(175, 190, 174) else Color.WHITE)
        setPadding(0, dp(8), 0, dp(8)); setLineSpacing(dp(3).toFloat(), 1f)
        if (size >= 20) setTypeface(null, Typeface.BOLD)
    }
    private fun action(value: String, click: () -> Unit) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false; setTextColor(lime)
        background = GradientDrawable().apply { setColor(Color.rgb(28, 36, 29)); cornerRadius = dp(12).toFloat() }
        minHeight = dp(48); setOnClickListener { click() }
    }
    private fun render() {
        list.removeAllViews()
        list.addView(action(text("‹  Назад", "‹  Back")) { finish() }, LinearLayout.LayoutParams(dp(110), dp(48)))
        list.addView(label(text("Мои загрузки", "My downloads"), 28))
        list.addView(label(text("Файлы, которые вы сохранили из Flow. Удаление записи не удаляет сам файл.",
            "Files you saved from Flow. Removing an entry does not delete the file."), 15, true))
        val items = history.list()
        if (items.isEmpty()) list.addView(label(text("Здесь пока пусто\nСкачайте файл и нажмите «Сохранить файл» — он появится здесь.",
            "Nothing here yet\nDownload and save a file to see it here."), 17, true))
        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply { setColor(Color.rgb(28, 36, 29)); cornerRadius = dp(16).toFloat() }
            }
            card.addView(label(item.title, 20))
            card.addView(label("${android.text.format.Formatter.formatShortFileSize(this, item.bytes)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.savedAt))}", 13, true))
            val actions = LinearLayout(this)
            actions.addView(action(text("Открыть", "Open")) { access(item, false) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(action(text("Поделиться", "Share")) { access(item, true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(action("⋮") {
                AlertDialog.Builder(this).setTitle(text("Убрать из истории?", "Remove from history?"))
                    .setMessage(text("Файл останется в выбранной папке.", "The file stays in its folder."))
                    .setPositiveButton(text("Убрать", "Remove")) { _, _ ->
                        worker.execute {
                            val result = runCatching { history.remove(item.uri) }
                            runOnUiThread { if (!isDestroyed) { if (result.isSuccess) render() else unavailable() } }
                        }
                    }.setNegativeButton(text("Отмена", "Cancel"), null).show()
            }.apply { contentDescription = text("Убрать из истории", "Remove from history") }, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }
    }
    private fun access(item: SavedDownload, share: Boolean) {
        worker.execute {
            val available = runCatching { contentResolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")?.use { true } ?: false }.getOrDefault(false)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!available) { unavailable(); return@runOnUiThread }
                try {
                    val uri = Uri.parse(item.uri)
                    val request = if (share) Intent(Intent.ACTION_SEND).apply {
                        type = item.mime; putExtra(Intent.EXTRA_STREAM, uri)
                    } else Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime)
                    request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    request.clipData = ClipData.newRawUri(item.title, uri)
                    startActivity(Intent.createChooser(request, text("Выберите приложение", "Choose an app")))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, text("Нет приложения для этого формата.", "No app supports this format."), Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) { unavailable() }
            }
        }
    }
    private fun unavailable() {
        Toast.makeText(this, text("Файл недоступен: он мог быть удалён, перемещён или доступ отозван.",
            "File unavailable: it may have been moved, deleted, or access revoked."), Toast.LENGTH_LONG).show()
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
