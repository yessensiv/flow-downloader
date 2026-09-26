package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.animation.ValueAnimator
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
    private var music = false
    private var deleting = false
    private var pendingDeletion: SavedDownload? = null
    private val worker = Executors.newSingleThreadExecutor()
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val lime = Color.rgb(194, 255, 112)
    private fun animateRows() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        (0 until list.childCount).forEach { index ->
            val child = list.getChildAt(index)
            child.alpha = 0f; child.translationY = dp(6).toFloat()
            child.animate().alpha(1f).translationY(0f).setStartDelay(index * 28L).setDuration(200L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = intent.getBooleanExtra("english", false)
        history = DownloadHistory(this)
        music = savedInstanceState?.getBoolean("music") ?: false
        pendingDeletion = savedInstanceState?.getString("pendingDeletion")?.let { uri -> history.list().find { it.uri == uri } }
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
        minHeight = dp(48)
        setOnTouchListener { view, event ->
            if (ValueAnimator.areAnimatorsEnabled()) when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(.98f).scaleY(.98f).setDuration(75L).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            false
        }
        setOnClickListener { click() }
    }
    private fun render() {
        list.removeAllViews()
        list.addView(action(text("‹  Назад", "‹  Back")) { finish() }, LinearLayout.LayoutParams(dp(110), dp(48)))
        list.addView(label(text("Мои загрузки", "My downloads"), 28))
        list.addView(label(text("Видео и музыка — каждый файл на своём месте.",
            "Your saved videos and music, neatly separated."), 15, true))
        val all = history.list()
        val tabs = LinearLayout(this)
        listOf(false, true).forEach { audio ->
            val count = all.count { it.isAudio == audio }
            val name = if (audio) text("Музыка", "Music") else text("Видео", "Video")
            tabs.addView(action("$name · $count") { music = audio; render() }.apply {
                if (music == audio) {
                    setTextColor(Color.rgb(15, 20, 16))
                    background = GradientDrawable().apply { setColor(lime); cornerRadius = dp(12).toFloat() }
                }
                isEnabled = !deleting
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if (audio) leftMargin = dp(8) })
        }
        list.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })
        val items = all.filter { it.isAudio == music }
        if (items.isEmpty()) list.addView(label(if (music) text("Музыки пока нет\nСохраните аудио из Flow — оно появится здесь.",
            "No music yet\nSave audio from Flow to see it here.") else text("Видео пока нет\nСохраните видео из Flow — оно появится здесь.",
            "No videos yet\nSave a video from Flow to see it here."), 17, true))
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
                AlertDialog.Builder(this).setTitle(item.title)
                    .setItems(arrayOf(text("Удалить файл и запись", "Delete file and entry"), text("Только убрать из истории", "Remove entry only"))) { _, which ->
                        if (which == 0) confirmDelete(item) else removeEntry(item)
                    }.show()
            }.apply { contentDescription = text("Действия с файлом", "File actions"); isEnabled = !deleting }, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }
        animateRows()
    }
    private fun removeEntry(item: SavedDownload) {
        AlertDialog.Builder(this).setTitle(text("Убрать из истории?", "Remove from history?"))
                    .setMessage(text("Файл останется в выбранной папке.", "The file stays in its folder."))
                    .setPositiveButton(text("Убрать", "Remove")) { _, _ ->
                        worker.execute {
                            val result = runCatching { history.remove(item.uri) }
                            runOnUiThread { if (!isDestroyed) { if (result.isSuccess) render() else unavailable() } }
                        }
                    }.setNegativeButton(text("Отмена", "Cancel"), null).show()
    }
    private fun confirmDelete(item: SavedDownload) {
        if (deleting) return
        AlertDialog.Builder(this).setTitle(text("Удалить файл?", "Delete file?"))
            .setMessage(item.title + "\n\n" + text("Файл будет удалён из выбранной папки и из Flow. Отменить удаление в приложении нельзя.",
                "The file will be deleted from its folder and from Flow. This cannot be undone in the app."))
            .setNegativeButton(text("Отмена", "Cancel"), null)
            .setPositiveButton(text("Удалить файл", "Delete file")) { _, _ -> deleteFile(item) }.show()
    }
    private fun deleteFile(item: SavedDownload) {
        if (deleting) return
        deleting = true; render()
        worker.execute {
            var fileDeleted = false
            val result = runCatching {
                history.deleteFile(item) { value ->
                    val uri = Uri.parse(value)
                    if (checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        throw SecurityException("Write permission required")
                    require(android.provider.DocumentsContract.isDocumentUri(this, uri))
                    val supports = contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                        it.moveToFirst() && it.getInt(0) and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
                    } ?: false
                    if (!supports) false else android.provider.DocumentsContract.deleteDocument(contentResolver, uri).also { fileDeleted = it }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                deleting = false; render()
                when {
                    result.getOrDefault(false) -> Toast.makeText(this, text("Файл и запись удалены.", "File and entry deleted."), Toast.LENGTH_LONG).show()
                    fileDeleted -> AlertDialog.Builder(this).setMessage(text("Файл удалён, но запись не удалось обновить. Уберите её из истории вручную.", "File deleted, but history could not be updated. Remove the entry manually.")).setPositiveButton("OK", null).show()
                    result.exceptionOrNull() is SecurityException -> requestDeleteAccess(item)
                    else -> AlertDialog.Builder(this).setMessage(text("Не удалось удалить файл. Возможно, он уже удалён или хранилище не поддерживает удаление. Запись оставлена в истории.",
                        "Could not delete the file. It may be missing or the provider does not support deletion. The history entry was kept.")).setPositiveButton("OK", null).show()
                }
            }
        }
    }
    private fun requestDeleteAccess(item: SavedDownload) {
        AlertDialog.Builder(this).setTitle(text("Нужен доступ к файлу", "File access needed"))
            .setMessage(text("Выберите этот же файл в системном окне, чтобы разрешить удаление. После выбора снова появится подтверждение.",
                "Select this same file in the system picker to allow deletion. You will be asked to confirm again."))
            .setNegativeButton(text("Отмена", "Cancel"), null)
            .setPositiveButton(text("Выбрать файл", "Select file")) { _, _ ->
                pendingDeletion = item
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(item.uri))
                }, 30)
            }.show()
    }
    @Deprecated("Uses system document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 30) return
        val item = pendingDeletion ?: return
        pendingDeletion = null
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri != Uri.parse(item.uri)) {
            Toast.makeText(this, text("Выбран другой файл. Ничего не удалено.", "A different file was selected. Nothing deleted."), Toast.LENGTH_LONG).show()
            return
        }
        if (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
            Toast.makeText(this, text("Хранилище не предоставило доступ на удаление.", "The provider did not grant write access."), Toast.LENGTH_LONG).show()
            return
        }
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        confirmDelete(item)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("music", music); outState.putString("pendingDeletion", pendingDeletion?.uri)
        super.onSaveInstanceState(outState)
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
