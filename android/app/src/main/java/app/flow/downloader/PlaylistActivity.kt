package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Polished multi-select screen for selecting items from a YouTube playlist. */
class PlaylistActivity : Activity() {
    private val lime = Color.rgb(194, 255, 112)
    private val bg = Color.rgb(15, 20, 16)
    private val card = Color.rgb(28, 36, 29)
    private val muted = Color.rgb(170, 185, 169)
    private val selectedUrls = linkedSetOf<String>()
    private val rows = mutableListOf<PlaylistEntry>()
    private val imageWorker = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var selectAll: CheckBox
    private lateinit var selectedCount: TextView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var videoTab: Button
    private lateinit var audioTab: Button
    private lateinit var m4aTab: Button
    private lateinit var mp3Tab: Button
    private lateinit var action: Button
    private lateinit var cancelAction: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var audio = false
    private var mp3 = false
    private var english = false
    private var playlistTitle = "YouTube playlist"
    private var playlistChannel = ""
    private val polling = object : Runnable {
        override fun run() {
            renderProgress()
            main.postDelayed(this, 500)
        }
    }
    private fun word(ru: String, en: String) = if (english) en else ru
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = intent.getBooleanExtra("english", false)
        audio = intent.getBooleanExtra("audio", false)
        playlistTitle = intent.getStringExtra("title") ?: playlistTitle
        playlistChannel = intent.getStringExtra("channel").orEmpty()
        val serialized = intent.getStringExtra("entries").orEmpty()
        runCatching {
            val json = JSONArray(serialized)
            (0 until json.length()).forEach { index ->
                val item = json.getJSONObject(index)
                rows += PlaylistEntry(item.getString("url"), item.optString("title", "YouTube"),
                    item.optString("channel"), item.optString("duration"), item.optString("thumbnail"))
            }
        }
        PlaylistBatchService.forgetCompleted()
        window.statusBarColor = bg; window.navigationBarColor = bg
        buildUi()
        if (rows.isEmpty()) {
            status.text = word("Не удалось прочитать элементы плейлиста.", "Could not read playlist items.")
            action.isEnabled = false
        }
        renderRows()
        renderProgress()
    }

    private fun buildUi() {
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
        }
        scroll.addView(content)
        val back = Button(this).apply {
            text = word("‹  Назад", "‹  Back"); isAllCaps = false; textSize = 15f
            setOnClickListener { finish() }; background = rounded(card, 18, Color.TRANSPARENT)
            setTextColor(lime); setPadding(dp(10), 0, dp(10), 0); minimumHeight = dp(44)
        }
        content.addView(back, LinearLayout.LayoutParams(-2, dp(44)))
        val eyebrow = label(word("ПЛЕЙЛИСТ YOUTUBE", "YOUTUBE PLAYLIST"), 12, muted).apply {
            letterSpacing = .08f; setPadding(0, dp(22), 0, dp(6))
        }
        content.addView(eyebrow)
        title = label(playlistTitle, 25, Color.WHITE, true).apply { setLineSpacing(dp(2).toFloat(), 1f) }
        content.addView(title)
        subtitle = label("", 14, muted).apply { setPadding(0, dp(6), 0, dp(16)) }
        content.addView(subtitle)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4)); background = rounded(card, 18)
        }
        videoTab = tabButton("") { audio = false; refreshMode() }
        audioTab = tabButton("") { audio = true; refreshMode() }
        tabs.addView(videoTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(audioTab, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        content.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val audioFormat = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(3), dp(3), dp(3), dp(3)); background = rounded(Color.rgb(20, 26, 21), 14)
        }
        m4aTab = tabButton("M4A") { mp3 = false; refreshMode() }
        mp3Tab = tabButton("MP3") { mp3 = true; refreshMode() }
        audioFormat.addView(m4aTab, LinearLayout.LayoutParams(0, dp(40), 1f))
        audioFormat.addView(mp3Tab, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(4) })
        content.addView(audioFormat, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val selectionBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        selectAll = CheckBox(this).apply {
            buttonTintList = ColorStateList.valueOf(lime); setTextColor(Color.WHITE); textSize = 15f
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedUrls.clear()
                    rows.take(MAX_SELECTED).forEach { selectedUrls += it.url }
                    if (rows.size > MAX_SELECTED) Toast.makeText(this@PlaylistActivity,
                        word("За раз можно выбрать не больше $MAX_SELECTED файлов.", "Select up to $MAX_SELECTED files at a time."), Toast.LENGTH_LONG).show()
                } else selectedUrls.clear()
                renderRows()
            }
        }
        selectionBar.addView(selectAll)
        selectedCount = label("", 14, muted).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        selectionBar.addView(selectedCount, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(selectionBar, LinearLayout.LayoutParams(-1, -2))
        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rowsContainer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })

        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16)); background = rounded(bg, 0, Color.TRANSPARENT)
        }
        val divider = View(this).apply { setBackgroundColor(Color.rgb(49, 62, 49)) }
        footer.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(11) })
        status = label("", 13, muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        footer.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(lime); indeterminateTintList = ColorStateList.valueOf(lime)
        }
        footer.addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { bottomMargin = dp(10) })
        cancelAction = Button(this).apply {
            text = word("Отменить очередь", "Cancel queue"); isAllCaps = false; textSize = 14f
            background = rounded(card, 16); setTextColor(Color.WHITE); visibility = View.GONE
            setOnClickListener {
                AlertDialog.Builder(this@PlaylistActivity).setTitle(word("Остановить загрузку?", "Cancel downloads?"))
                    .setMessage(word("Уже сохранённые файлы останутся в телефоне.", "Files already saved will stay on your phone."))
                    .setNegativeButton(word("Продолжить", "Keep downloading"), null)
                    .setPositiveButton(word("Остановить", "Cancel")) { _, _ ->
                        startService(Intent(this@PlaylistActivity, PlaylistBatchService::class.java).setAction(PlaylistBatchService.CANCEL))
                    }.show()
            }
        }
        footer.addView(cancelAction, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(7) })
        action = Button(this).apply {
            isAllCaps = false; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setOnClickListener { startBatch() }
        }
        footer.addView(action, LinearLayout.LayoutParams(-1, dp(54)))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(footer, LinearLayout.LayoutParams(-1, -2))
        setContentView(shell)
        shell.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        shell.requestApplyInsets()
        refreshMode()
    }

    private fun renderRows() {
        if (!::rowsContainer.isInitialized) return
        rowsContainer.removeAllViews()
        rows.forEachIndexed { index, entry ->
            val isSelected = entry.url in selectedUrls
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(if (isSelected) Color.rgb(36, 51, 34) else card, 18,
                    if (isSelected) Color.rgb(132, 185, 73) else Color.rgb(49, 62, 49))
                isClickable = true; isFocusable = true
                setOnClickListener { toggle(entry) }
            }
            val check = CheckBox(this).apply {
                buttonTintList = ColorStateList.valueOf(if (isSelected) lime else muted)
                isChecked = isSelected; isClickable = false; isFocusable = false
            }
            row.addView(check, LinearLayout.LayoutParams(dp(34), dp(42)))
            val imageFrame = FrameLayout(this).apply {
                background = rounded(Color.rgb(18, 24, 19), 12); clipToOutline = true
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = entry.thumbnail }
            imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val play = TextView(this).apply {
                text = "▶"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = rounded(Color.argb(165, 8, 12, 9), 99)
            }
            imageFrame.addView(play, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            row.addView(imageFrame, LinearLayout.LayoutParams(dp(112), dp(64)).apply { rightMargin = dp(11) })
            val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            val itemTitle = label(entry.title, 14, Color.WHITE, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            textBox.addView(itemTitle)
            val metadata = listOf(entry.channel, entry.duration).filter { it.isNotBlank() }.joinToString(" · ")
            if (metadata.isNotBlank()) textBox.addView(label(metadata, 11, muted).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
            })
            row.addView(textBox, LinearLayout.LayoutParams(0, -1, 1f))
            rowsContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
                if (index == 0) topMargin = dp(1)
            })
            loadRowThumbnail(entry.thumbnail, image)
            if (index < rows.lastIndex && !isSelected) row.animate().alpha(1f).setDuration(160).start()
        }
        updateSelectionUi()
    }

    private fun toggle(entry: PlaylistEntry) {
        if (entry.url in selectedUrls) selectedUrls.remove(entry.url)
        else if (selectedUrls.size >= MAX_SELECTED) {
            Toast.makeText(this, word("Можно выбрать максимум $MAX_SELECTED файлов за раз.", "You can select up to $MAX_SELECTED files at a time."), Toast.LENGTH_SHORT).show()
            return
        } else selectedUrls += entry.url
        renderRows()
    }

    private fun updateSelectionUi() {
        val chosen = selectedUrls.size
        selectedCount.text = word("$chosen из ${rows.size} выбрано", "$chosen of ${rows.size} selected")
        selectAll.setOnCheckedChangeListener(null)
        selectAll.isChecked = chosen == minOf(rows.size, MAX_SELECTED) && chosen > 0
        selectAll.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectedUrls.clear(); rows.take(MAX_SELECTED).forEach { selectedUrls += it.url }
                if (rows.size > MAX_SELECTED) Toast.makeText(this, word("За раз можно выбрать не больше $MAX_SELECTED файлов.", "Select up to $MAX_SELECTED files at a time."), Toast.LENGTH_LONG).show()
            } else selectedUrls.clear()
            renderRows()
        }
        val running = PlaylistBatchService.state?.running == true
        action.isEnabled = chosen > 0 && !running && Build.VERSION.SDK_INT >= 29
        action.text = when {
            running -> word("Загрузка идёт…", "Downloading…")
            audio -> word("↓  Скачать $chosen ${if (chosen == 1) "аудио" else "аудио"}", "↓  Download $chosen ${if (chosen == 1) "track" else "tracks"}")
            else -> word("↓  Скачать $chosen ${if (chosen == 1) "видео" else "видео"}", "↓  Download $chosen ${if (chosen == 1) "video" else "videos"}")
        }
        style(action, primary = true)
    }

    private fun refreshMode() {
        if (!::title.isInitialized) return
        subtitle.text = listOf(playlistChannel, word("Выбери, что скачать на телефон", "Choose what to save to your phone"))
            .filter { it.isNotBlank() }.joinToString(" · ")
        style(videoTab, primary = !audio); style(audioTab, primary = audio)
        listOf(m4aTab, mp3Tab).forEach { it.visibility = if (audio) View.VISIBLE else View.GONE }
        style(m4aTab, primary = !mp3); style(mp3Tab, primary = mp3)
        renderRows(); updateSelectionUi()
    }

    private fun renderProgress() {
        if (!::status.isInitialized) return
        val current = PlaylistBatchService.state
        val running = current?.running == true
        if (running && current != null) {
            val parts = mutableListOf("${current.index.coerceAtLeast(1)}/${current.total}")
            if (current.currentTitle.isNotBlank()) parts += current.currentTitle
            if (current.progress >= 0) parts += "${current.progress}%"
            if (current.speed.isNotBlank()) parts += current.speed
            status.text = parts.joinToString(" · ")
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = current.progress < 0
            progress.progress = current.progress.coerceAtLeast(0)
            cancelAction.visibility = View.VISIBLE
            action.isEnabled = false
            action.text = word("Обрабатываем список…", "Processing playlist…")
        } else if (current != null) {
            cancelAction.visibility = View.GONE
            progress.visibility = View.GONE
            val count = word("Готово ${current.completed} · ошибки ${current.failed}", "Done ${current.completed} · failed ${current.failed}")
            val ending = if (current.cancelled) word("Остановлено", "Stopped") else word("Очередь завершена", "Queue complete")
            status.text = "$ending\n$count${if (current.failed > 0 && current.error.isNotBlank()) "\n${current.error.take(100)}" else ""}"
            action.text = word("Открыть мои загрузки", "Open my downloads")
            action.isEnabled = true
            action.setOnClickListener { startActivity(Intent(this, DownloadsActivity::class.java).putExtra("english", english)) }
            style(action, true)
        } else {
            cancelAction.visibility = View.GONE
            progress.visibility = View.GONE
            status.text = if (Build.VERSION.SDK_INT < 29)
                word("Пакетное сохранение требует Android 10 или новее.", "Batch saving requires Android 10 or newer.")
            else word("За один раз можно скачать до $MAX_SELECTED пунктов · всего в списке ${rows.size}.",
                "Download up to $MAX_SELECTED items at a time · ${rows.size} in this playlist.")
            updateSelectionUi()
        }
    }

    private fun startBatch() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, word("Пакетное сохранение доступно с Android 10.", "Batch saving is available on Android 10+."), Toast.LENGTH_LONG).show()
            return
        }
        val selected = rows.filter { it.url in selectedUrls }
        if (selected.isEmpty()) return
        val json = JSONArray().apply { selected.forEach { entry -> put(org.json.JSONObject().apply {
            put("url", entry.url); put("title", entry.title); put("channel", entry.channel)
            put("duration", entry.duration); put("thumbnail", entry.thumbnail)
        }) } }
        PlaylistBatchService.forgetCompleted()
        selectedUrls.clear()
        renderRows()
        action.isEnabled = false
        startForegroundService(Intent(this, PlaylistBatchService::class.java).apply {
            putExtra("entries", json.toString()); putExtra("audio", audio); putExtra("mp3", mp3); putExtra("english", english)
        })
        renderProgress()
    }

    private fun loadRowThumbnail(raw: String, target: ImageView) {
        if (raw.isBlank()) return
        imageWorker.execute {
            val bitmap = runCatching {
                val url = URL(raw)
                require(url.protocol == "https" && (url.host == "i.ytimg.com" || url.host.endsWith(".ytimg.com")))
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                    require(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val size = stream.read(buffer); if (size < 0) break; require(output.size() + size < 2 * 1024 * 1024); output.write(buffer, 0, size) }
                        output.toByteArray()
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
            main.post { if (!isDestroyed && target.tag == raw && bitmap != null) target.setImageBitmap(bitmap) }
        }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun tabButton(value: String, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f
        setOnClickListener { click() }
    }

    private fun rounded(color: Int, radius: Int = 16, stroke: Int = Color.rgb(49, 62, 49)) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun style(button: Button, primary: Boolean) {
        val shape = rounded(if (primary) lime else card, 16, if (primary) Color.TRANSPARENT else Color.rgb(49, 62, 49))
        button.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), shape, null)
        button.setTextColor(if (primary) Color.rgb(20, 28, 16) else Color.rgb(225, 234, 222))
        button.setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        button.alpha = if (button.isEnabled) 1f else .5f
    }

    override fun onStart() { super.onStart(); main.post(polling) }
    override fun onStop() { main.removeCallbacks(polling); super.onStop() }
    override fun onDestroy() { imageWorker.shutdownNow(); main.removeCallbacks(polling); super.onDestroy() }

    private companion object { const val MAX_SELECTED = 10 }
}
