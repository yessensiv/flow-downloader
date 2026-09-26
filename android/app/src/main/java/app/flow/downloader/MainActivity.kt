package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.animation.ValueAnimator
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

/** Screen work stays here; foreground downloads belong to DownloadService. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val imageWorker = Executors.newSingleThreadExecutor()
    private lateinit var thumbnail: ImageView
    private lateinit var engine: MediaEngine
    private lateinit var root: LinearLayout
    private lateinit var input: EditText
    private lateinit var clearLink: TextView
    private lateinit var status: TextView
    private lateinit var cancelDownload: Button
    private lateinit var title: TextView
    private lateinit var spinner: Spinner
    private lateinit var bitrateSpinner: Spinner
    private lateinit var bitrateLabel: TextView
    private lateinit var bitrateHint: TextView
    private lateinit var exportSummary: Button
    private lateinit var exportFields: LinearLayout
    private lateinit var advancedFields: LinearLayout
    private lateinit var advancedToggle: Button
    private var settingsDialog: android.app.Dialog? = null
    private var exportLanguage: Boolean? = null
    private lateinit var containerSpinner: Spinner
    private lateinit var containerLabel: TextView
    private lateinit var embedCover: Switch
    private lateinit var embedMetadata: Switch
    private val bitrates = listOf(64, 96, 128, 160, 192, 256, 320)
    private lateinit var progress: ProgressBar
    private lateinit var analyze: Button
    private lateinit var download: Button
    private lateinit var save: Button
    private lateinit var language: Button
    private lateinit var historyButton: Button
    private var exportTitle = "Flow"
    private var exportMime = "application/octet-stream"
    private var exportThumbnail = ""
    private lateinit var mode: Button
    private lateinit var audioMode: Button
    private lateinit var subtitle: TextView
    private lateinit var qualityLabel: TextView
    private lateinit var details: Button
    private lateinit var resultCard: LinearLayout
    private lateinit var heading: TextView
    private lateinit var linkLabel: TextView
    private lateinit var resultLabel: TextView
    private var saved = false
    private var lastError = ""
    private lateinit var update: Button
    private var english = false
    private var audio = false
    private var busy = false
    private var media: Media? = null
    private var ready: File? = null
    private var choices = emptyList<Choice>()
    private var followingDownload = false
    private var pendingShare: String? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val downloadPoll = object : Runnable {
        override fun run() {
            if (followingDownload) syncDownload()
            main.postDelayed(this, 500)
        }
    }
    private val lime = Color.rgb(194, 255, 112)
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = getPreferences(0).getBoolean("english", false)
        engine = MediaEngine(applicationContext)
        window.statusBarColor = Color.rgb(15, 20, 16)
        window.navigationBarColor = Color.rgb(15, 20, 16)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(15, 20, 16)); isFillViewport = true
            clipToPadding = true
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        scroll.addView(root)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        scroll.requestApplyInsets()
        val brand = label("flow.", 34).apply { setTextColor(lime) }
        language = button("RU / EN") { english = !english; getPreferences(0).edit().putBoolean("english", english).apply(); refresh() }
        root.removeView(brand); root.removeView(language)
        root.addView(LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
            addView(language, LinearLayout.LayoutParams(dp(90), dp(44)))
        })
        heading = label("", 24).apply { setPadding(0, dp(16), 0, dp(4)) }
        subtitle = label("", 14).apply { setTextColor(Color.rgb(170, 185, 169)); setPadding(0, 0, 0, dp(8)) }
        mode = button("") { switchMode(false) }
        audioMode = button("") { switchMode(true) }
        root.removeView(mode); root.removeView(audioMode)
        root.addView(LinearLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = surface()
            addView(mode, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(audioMode, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(20) })
        linkLabel = label("", 14).apply { setTextColor(Color.rgb(175, 190, 174)); setPadding(0, 0, 0, dp(10)) }
        input = EditText(this).apply {
            textSize = 16f; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.rgb(144, 159, 144))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = surface(); setPadding(dp(16), dp(14), dp(54), dp(14))
            minimumHeight = dp(58)
            setSelectAllOnFocus(true)
        }
        clearLink = TextView(this).apply {
            text = "×"; textSize = 28f; gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(175, 190, 174)); isFocusable = false
            contentDescription = text("Очистить ссылку", "Clear link")
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(50, 194, 255, 112)), null,
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() })
            visibility = View.GONE
            setOnClickListener { input.text.clear(); input.requestFocus() }
        }
        val linkField = FrameLayout(this).apply {
            addView(input, FrameLayout.LayoutParams(-1, -1))
            addView(clearLink, FrameLayout.LayoutParams(dp(48), dp(48), android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        }
        root.addView(linkField, LinearLayout.LayoutParams(-1, dp(62)))
        analyze = button("") {
            val url = input.text.toString()
            job(text("Ищем варианты…", "Finding options…")) {
                val found = engine.analyze(url)
                runOnUiThread {
                    ready?.parentFile?.deleteRecursively(); ready = null
                    media = found; title.text = found.title; refreshChoices(); loadThumbnail(found)
                }
            }
        }
        resultLabel = label("", 13).apply { setTextColor(lime); setPadding(0, 0, 0, dp(8)) }
        thumbnail = object : ImageView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            }
        }.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = surface(Color.rgb(15, 20, 16)); clipToOutline = true
            visibility = View.GONE
        }
        title = label("", 18).apply { setPadding(0, 0, 0, dp(4)); setTypeface(null, Typeface.BOLD); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        qualityLabel = label("", 14).apply { setTextColor(Color.rgb(175, 190, 174)); setPadding(0, 0, 0, dp(6)) }
        spinner = Spinner(this).apply { background = surface(); minimumHeight = dp(56); setPadding(dp(10), 0, dp(10), 0) }
        root.addView(spinner)
        val exportPrefs = getSharedPreferences("export", MODE_PRIVATE)
        containerLabel = label("", 14)
        containerSpinner = Spinner(this).apply {
            background = surface(); minimumHeight = dp(54)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("MKV", "MP4"))
        }
        bitrateLabel = label("", 14)
        bitrateHint = label("", 12).apply {
            setTextColor(Color.rgb(153, 170, 150)); setPadding(dp(2), 0, dp(2), dp(6))
        }
        bitrateSpinner = Spinner(this).apply {
            background = surface(); minimumHeight = dp(54)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bitrates.map { "$it kbps" })
            setSelection(bitrates.indexOf(exportPrefs.getInt("bitrate", 192)).coerceAtLeast(0))
        }
        embedMetadata = Switch(this).apply {
            setTextColor(Color.WHITE); isChecked = exportPrefs.getBoolean("metadata", true)
            setOnCheckedChangeListener { _, checked -> exportPrefs.edit().putBoolean("metadata", checked).apply() }
        }
        embedCover = Switch(this).apply {
            setTextColor(Color.WHITE); isChecked = exportPrefs.getBoolean("cover", true)
            setOnCheckedChangeListener { _, checked -> exportPrefs.edit().putBoolean("cover", checked).apply() }
        }
        listOf(containerLabel, bitrateLabel).forEach {
            it.setTextColor(Color.rgb(175, 190, 174)); it.setPadding(0, dp(4), 0, dp(4))
        }
        listOf(spinner, containerSpinner, bitrateSpinner).forEach {
            it.background = surface(Color.rgb(18, 25, 19)); it.minimumHeight = dp(52)
            it.setPadding(dp(2), 0, dp(2), 0)
        }
        listOf(embedMetadata, embedCover).forEach {
            it.textSize = 15f; it.minimumHeight = dp(64)
            it.setPadding(dp(12), dp(10), dp(12), dp(10))
            it.background = surface(Color.rgb(22, 30, 23))
            it.switchPadding = dp(12)
            it.thumbTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.rgb(78, 91, 77), lime, Color.rgb(151, 166, 147)))
            it.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.rgb(80, 107, 49), Color.rgb(55, 66, 55)))
        }
        bitrateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshExportOptions() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshExportOptions() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        download = button("") {
            val selected = choices.getOrNull(spinner.selectedItemPosition)?.copy(
                format = if (audio) choices.getOrNull(spinner.selectedItemPosition)?.format.orEmpty() else if (containerSpinner.selectedItemPosition == 1) "mp4" else "mkv",
                bitrate = bitrates.getOrElse(bitrateSpinner.selectedItemPosition) { 192 },
                metadata = embedMetadata.isChecked, cover = embedCover.isEnabled && embedCover.isChecked)
            exportPrefs.edit().putInt("bitrate", selected?.bitrate ?: 192).apply()
            val found = media
            if (selected != null && found != null) {
                ready?.parentFile?.deleteRecursively(); ready = null
                if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20)
                try {
                    DownloadService.forgetCompleted()
                    startForegroundService(Intent(this, DownloadService::class.java).apply {
                        putExtra("url", found.url); putExtra("title", found.title); putExtra("thumbnail", found.thumbnail)
                        putExtra("selector", selected.selector); putExtra("label", selected.label)
                        putExtra("audio", selected.audio); putExtra("mp3", selected.mp3)
                        putExtra("extractAudio", selected.extractAudio); putExtra("english", english)
                        putExtra("format", selected.format); putExtra("bitrate", selected.bitrate)
                        putExtra("metadata", selected.metadata); putExtra("cover", selected.cover)
                    })
                    followingDownload = true; busy = true; saved = false; lastError = ""
                    refresh(); status.text = text("Готовим файл. Можно свернуть приложение.", "Preparing file. You can leave the app.")
                } catch (e: Exception) { lastError = e.message.orEmpty(); refresh() }
            }
        }
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surface(); setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        exportFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(qualityLabel, spinner, containerLabel, containerSpinner, bitrateLabel, bitrateSpinner, bitrateHint).forEach {
            root.removeView(it)
            exportFields.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        advancedFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
            addView(embedMetadata, LinearLayout.LayoutParams(-1, -2))
            addView(embedCover, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        advancedToggle = button("") {
            advancedFields.visibility = if (advancedFields.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshExportOptions()
        }
        root.removeView(advancedToggle)
        exportFields.addView(advancedToggle, LinearLayout.LayoutParams(-1, dp(48)))
        exportFields.addView(advancedFields)
        exportSummary = button("") { showExportSettings() }.apply {
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0); textSize = 14f
        }
        containerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshExportOptions() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        listOf(resultLabel, thumbnail, title, exportSummary, download).forEach {
            root.removeView(it); resultCard.addView(it, LinearLayout.LayoutParams(-1, if (it == download || it is Spinner) dp(54) else -2).apply { bottomMargin = dp(if (it == qualityLabel || it == bitrateLabel || it == containerLabel) 4 else 10) })
        }
        root.addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        progress.progressTintList = ColorStateList.valueOf(lime)
        progress.indeterminateTintList = ColorStateList.valueOf(lime)
        root.addView(progress)
        status = label("", 14).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        cancelDownload = button("") {
            AlertDialog.Builder(this).setTitle(text("Отменить загрузку?", "Cancel download?"))
                .setMessage(text("Текущий файл будет удалён из временных данных приложения.", "The in-progress file will be discarded."))
                .setNegativeButton(text("Продолжить", "Keep downloading"), null)
                .setPositiveButton(text("Отменить загрузку", "Cancel download")) { _, _ ->
                    startService(Intent(this, DownloadService::class.java).setAction(DownloadService.CANCEL))
                }.show()
        }
        style(cancelDownload, false)
        save = button("") {
            if (saved || busy) return@button
            val file = ready ?: return@button
            val mime = MediaStorage.mime(file, audio)
            exportTitle = media?.title ?: "Flow"
            exportMime = mime
            exportThumbnail = media?.thumbnail.orEmpty()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                saveToMediaStore(file, exportTitle, mime)
                return@button
            }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = mime
                putExtra(Intent.EXTRA_TITLE, "${media?.title?.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")?.take(100) ?: "Flow"}.${file.extension}")
            }, 1)
        }
        details = button("") {
            AlertDialog.Builder(this).setTitle(text("Подробности", "Details"))
                .setMessage(lastError.takeLast(4000)).setPositiveButton("OK", null).show()
        }
        update = button("") { job(text("Обновляем обработчик…", "Updating engine…")) { engine.update(); runOnUiThread { media = null; title.text = ""; refreshChoices() } } }
        historyButton = button("") {
            startActivity(Intent(this, DownloadsActivity::class.java).putExtra("english", english))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearLink.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (!followingDownload) DownloadService.forgetCompleted()
                media = null; title.text = ""; lastError = ""
                ready?.parentFile?.deleteRecursively(); ready = null
                refreshChoices(); refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        refresh()
        animateEntrance(listOf(brand, heading, subtitle, mode.parent as View, linkLabel, input, analyze), 24L)
        if (DownloadService.state != null) {
            followingDownload = true; syncDownload()
        }
        handleShare(intent)
    }
    private fun switchMode(next: Boolean) {
        if (audio == next) return
        DownloadService.forgetCompleted()
        audio = next; saved = false; ready?.parentFile?.deleteRecursively(); ready = null
        lastError = ""; refreshChoices(); refresh()
        if (motionEnabled()) {
            val selected = if (next) audioMode else mode
            selected.animate().cancel()
            selected.scaleX = .97f; selected.scaleY = .97f
            selected.animate().scaleX(1f).scaleY(1f).setDuration(180L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.15f)).start()
        }
    }
    private fun surface(color: Int = Color.rgb(28, 36, 29)) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(49, 62, 49))
    }
    private fun motionEnabled() = ValueAnimator.areAnimatorsEnabled()
    private fun animateEntrance(views: List<View>, offset: Long = 0L) {
        if (!motionEnabled()) return
        views.filter { it.visibility == View.VISIBLE }.forEachIndexed { index, view ->
            view.alpha = 0f; view.translationY = dp(8).toFloat()
            view.animate().cancel()
            view.animate().alpha(1f).translationY(0f).setStartDelay(offset + index * 35L)
                .setDuration(230L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }
    private fun revealResultCard() {
        if (!motionEnabled()) return
        resultCard.alpha = 0f; resultCard.translationY = dp(10).toFloat()
        resultCard.animate().alpha(1f).translationY(0f).setDuration(260L)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }
    private fun style(button: Button, primary: Boolean) {
        button.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), surface(if (primary) lime else Color.rgb(28, 36, 29)), null)
        button.setTextColor(if (primary) Color.rgb(20, 28, 16) else Color.rgb(220, 231, 216))
        button.setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        button.alpha = if (button.isEnabled) 1f else .45f
    }
    private fun refresh() {
        language.text = if (english) "EN · RU" else "RU · EN"
        heading.text = text("Любимое — с собой.", "Keep what you love.")
        subtitle.text = text("Видео и музыка прямо на телефоне.", "Video and music, right on your phone.")
        linkLabel.text = text("Ссылка на YouTube", "YouTube link")
        resultLabel.text = text("✓  Найдено на YouTube", "✓  Found on YouTube")
        mode.text = text("Видео", "Video"); audioMode.text = text("Аудио", "Audio")
        qualityLabel.text = if (audio) text("Формат аудио", "Audio format") else text("Качество видео", "Video quality")
        input.hint = text("Вставьте ссылку YouTube", "Paste a YouTube link")
        analyze.text = text("Показать варианты", "Show options")
        download.text = text("↓  Подготовить файл", "↓  Prepare download")
        save.text = if (saved) text("✓  Уже сохранено", "✓  Already saved") else text("Сохранить в папку Flow", "Save to Flow folder")
        update.text = text("↻  Обновить движок", "↻  Update engine")
        historyButton.text = text("Мои загрузки", "My downloads")
        style(historyButton, false)
        listOf(mode, audioMode, input, analyze, update).forEach { it.isEnabled = !busy }
        spinner.isEnabled = !busy && choices.isNotEmpty()
        refreshExportOptions()
        download.isEnabled = !busy && choices.isNotEmpty()
        save.visibility = if (ready != null && !busy && !saved) View.VISIBLE else View.GONE
        save.isEnabled = !busy && !saved
        title.visibility = if (media != null) View.VISIBLE else View.GONE
        val wasCardVisible = resultCard.visibility == View.VISIBLE
        resultCard.visibility = title.visibility
        if (!wasCardVisible && resultCard.visibility == View.VISIBLE) revealResultCard()
        qualityLabel.visibility = title.visibility; spinner.visibility = title.visibility
        download.visibility = if (media != null && ready == null) View.VISIBLE else View.GONE
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        cancelDownload.text = text("✕  Отменить загрузку", "✕  Cancel download")
        cancelDownload.visibility = if (busy && DownloadService.state?.running == true && DownloadService.state?.saving != true) View.VISIBLE else View.GONE
        details.text = text("Подробности ошибки", "Error details")
        details.visibility = if (lastError.isNotEmpty()) View.VISIBLE else View.GONE
        style(language, false); style(mode, !audio); style(audioMode, audio)
        style(analyze, false); style(download, true); style(save, true); style(update, false); style(details, false)
        if (!busy) status.text = if (lastError.isNotEmpty()) friendlyError(lastError) else if (saved) text("✓ Сохранено в ${if (audio) "Music/Flow" else "Movies/Flow"}.", "✓ Saved to ${if (audio) "Music/Flow" else "Movies/Flow"}.") else if (ready != null) text("Готово! Сохраните файл на устройстве.", "Ready! Save the file to your device.") else text("Без сервера · Загрузка работает в фоне\nВ YouTube нажмите «Поделиться» → Flow.", "No server · Downloads work in the background\nIn YouTube, tap Share → Flow.")
        status.setTextColor(if (lastError.isNotEmpty()) Color.rgb(255, 171, 151) else Color.rgb(175, 190, 174))
    }
    private fun friendlyError(error: String): String = when {
        error == "URL" -> text("Вставьте корректную ссылку YouTube.", "Paste a valid YouTube link.")
        error.contains("403") -> text("YouTube отклонил скачивание даже после обновления. Попробуйте другую ссылку или сеть.", "YouTube refused the download after the update. Try another link or network.")
        error == "LIVE" -> text("Дождитесь завершения трансляции.", "Wait for the livestream to finish.")
        error == "FORMAT_CHANGED" -> text("Набор форматов изменился. Нажмите «Показать варианты» заново.", "Formats have changed. Tap Show options again.")
        error == DownloadService.CANCELLED -> text("Загрузка отменена. Временный файл удалён.", "Download cancelled. The temporary file was removed.")
        error.contains("update", true) -> text("Не удалось обновить обработчик. Проверьте интернет и повторите обновление.", "Could not update the engine. Check your connection and retry the update.")
        else -> text("Не удалось завершить операцию. Подробности доступны ниже.", "Could not finish. Error details are available below.")
    }
    private fun refreshExportOptions() {
        if (!::embedCover.isInitialized) return
        if (exportLanguage != english) {
            exportLanguage = english
            val selectedBitrate = bitrateSpinner.selectedItemPosition.coerceAtLeast(0)
            val selectedContainer = containerSpinner.selectedItemPosition.coerceAtLeast(0)
            bitrateSpinner.adapter = exportAdapter(bitrates.map { rate -> "$rate " + text("кбит/с", "kbps") + if (rate == 192) text(" · Рекомендуем", " · Recommended") else "" })
            bitrateSpinner.setSelection(selectedBitrate)
            containerSpinner.adapter = exportAdapter(listOf("MKV", "MP4"))
            containerSpinner.setSelection(selectedContainer)
        }
        val choice = choices.getOrNull(spinner.selectedItemPosition)
        containerLabel.text = text("Формат видео", "Video format")
        containerLabel.visibility = if (audio) View.GONE else View.VISIBLE
        containerSpinner.visibility = containerLabel.visibility
        containerSpinner.isEnabled = !busy
        val lossy = choice?.audio == true && choice.format in listOf("mp3", "m4a", "opus")
        bitrateLabel.text = text("Качество звука", "Audio quality")
        bitrateLabel.visibility = if (lossy) View.VISIBLE else View.GONE
        bitrateSpinner.visibility = bitrateLabel.visibility
        bitrateHint.visibility = if (audio) View.VISIBLE else View.GONE
        val rate = bitrates.getOrElse(bitrateSpinner.selectedItemPosition) { 192 }
        bitrateHint.text = when {
            !lossy && choice?.format in listOf("flac", "wav") -> text("Без дополнительного сжатия · большой файл", "No additional lossy compression · larger file")
            !lossy -> text("Исходное качество · без выбора битрейта", "Source quality · no bitrate selection")
            rate < 192 -> text("Меньше размер файла · сильнее сжатие", "Smaller file · more compression")
            rate == 192 -> text("Баланс размера и качества для повседневного прослушивания", "Balanced size and quality for everyday listening")
            else -> text("Больше размер файла. Качество ограничено оригиналом.", "Larger file. Quality is limited by the source.")
        }
        bitrateSpinner.isEnabled = !busy
        val supportsCover = choice != null && (!choice.audio || choice.format in listOf("mp3", "m4a", "opus", "flac") || (choice.format.isEmpty() && (choice.extractAudio || choice.label.substringBefore(" ·") in listOf("M4A", "MP3", "OPUS"))))
        embedCover.text = optionCaption(text("Обложка", "Cover art"), if (supportsCover) text("Картинка внутри файла для плеера", "Artwork saved inside the file") else text("Этот формат не поддерживает обложку", "This format does not support cover art"))
        embedCover.isEnabled = !busy && supportsCover
        embedCover.alpha = if (supportsCover) 1f else .45f
        embedMetadata.text = optionCaption(text("Название и исполнитель", "Title and artist"), text("Сведения о треке, если доступны", "Track details, when available"))
        embedMetadata.isEnabled = !busy
        if (::exportSummary.isInitialized) {
            val format = if (audio) choice?.label.orEmpty() else if (containerSpinner.selectedItemPosition == 1) "MP4" else "MKV"
            val quality = if (audio) { if (lossy) "$rate " + text("кбит/с", "kbps") else text("Исходное качество", "Source quality") } else choice?.label.orEmpty()
            exportSummary.text = "$format · $quality   ›"
            exportSummary.isEnabled = !busy && choices.isNotEmpty()
            style(exportSummary, false)
            advancedToggle.text = text("Дополнительно", "Advanced") + if (advancedFields.visibility == View.VISIBLE) "  −" else "  +"
            style(advancedToggle, false)
        }
    }

    private fun showExportSettings() {
        if (busy || choices.isEmpty()) return
        (exportFields.parent as? android.view.ViewGroup)?.removeView(exportFields)
        val dialog = android.app.Dialog(this)
        settingsDialog = dialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
            background = surface()
            addView(TextView(this@MainActivity).apply {
                text = text("Настройки скачивания", "Download settings"); textSize = 21f
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(16))
            })
            addView(exportFields)
            addView(Button(this@MainActivity).apply {
                text = text("Готово", "Done"); isAllCaps = false; style(this, true)
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        }
        dialog.setContentView(ScrollView(this).apply { addView(panel) })
        dialog.setOnDismissListener {
            (exportFields.parent as? android.view.ViewGroup)?.removeView(exportFields)
            settingsDialog = null
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(-1, -2)
            attributes = attributes.apply { height = (resources.displayMetrics.heightPixels * .75f).toInt() }
        }
        refreshExportOptions()
    }

    private fun optionCaption(title: String, subtitle: String): CharSequence = android.text.SpannableString("$title\n$subtitle").apply {
        setSpan(android.text.style.RelativeSizeSpan(.8f), title.length + 1, length, 0)
        setSpan(android.text.style.ForegroundColorSpan(Color.rgb(153, 170, 150)), title.length + 1, length, 0)
    }

    private fun exportAdapter(labels: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
        private fun row(position: Int, dropdown: Boolean) = TextView(this@MainActivity).apply {
            text = getItem(position) + if (dropdown) "" else "   ▾"
            textSize = 15f; setTextColor(Color.rgb(235, 241, 232)); gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12)); minHeight = dp(52)
            setBackgroundColor(if (dropdown) Color.rgb(28, 36, 29) else Color.TRANSPARENT)
        }
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, false)
        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, true)
    }

    private fun refreshChoices() {
        choices = media?.choices?.filter { it.audio == audio } ?: emptyList()
        val labels = choices.map { choice ->
            if (choice.extractAudio && choice.format.isEmpty() && !choice.mp3) text("M4A · только звук", "M4A · audio only") else choice.label.replace("original", text("оригинал", "original"))
        }.ifEmpty {
            listOf(if (audio) text("Аудио для этого видео недоступно", "Audio is unavailable for this video")
                else text("Нет доступных форматов", "No formats available"))
        }
        spinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            private fun row(position: Int, dropdown: Boolean) = TextView(this@MainActivity).apply {
                text = getItem(position) + if (dropdown) "" else "   ▾"
                textSize = 16f; setTextColor(if (choices.isEmpty()) Color.rgb(144, 159, 144) else Color.rgb(235, 241, 232))
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(16))
                setBackgroundColor(if (dropdown) Color.rgb(28, 36, 29) else Color.TRANSPARENT)
                minHeight = dp(52)
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, false)
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, true)
        }
    }
    private fun loadThumbnail(found: Media) {
        thumbnail.setImageDrawable(null)
        thumbnail.visibility = View.GONE
        thumbnail.contentDescription = found.title
        imageWorker.execute {
            // Preview failure must never prevent analyzing or downloading the media.
            val bitmap = runCatching {
                val url = java.net.URL(found.thumbnail)
                require(url.protocol == "https" && (url.host == "i.ytimg.com" || url.host.endsWith(".ytimg.com")))
                val connection = url.openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 8000; connection.readTimeout = 8000
                    connection.instanceFollowRedirects = false
                    require(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 2 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 1280 || bounds.outHeight / inSampleSize > 1280) inSampleSize *= 2
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } finally { connection.disconnect() }
            }.getOrNull()
            runOnUiThread {
                if (!isDestroyed && media === found && bitmap != null) {
                    thumbnail.setImageBitmap(bitmap)
                    thumbnail.visibility = View.VISIBLE
                }
            }
        }
    }
    private fun job(message: String, block: () -> Unit) {
        if (busy) return
        saved = false; lastError = ""; busy = true; refresh(); status.text = message; progress.isIndeterminate = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker.execute {
            var problem: Exception? = null
            try { block() } catch (e: Exception) { problem = e }
            runOnUiThread {
                if (!isDestroyed) {
                    busy = false; progress.isIndeterminate = false; refresh()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    problem?.let { error ->
                        lastError = error.message ?: "Unknown error"
                        refresh()
                    }
                    offerPendingShare()
                }
            }
        }
    }
    private fun syncDownload() {
        val current = DownloadService.state ?: return
        if (media !== current.media) {
            input.setText(current.media.url)
            media = current.media; audio = current.choice.audio
            title.text = current.media.title; refreshChoices(); loadThumbnail(current.media)
        }
        busy = current.running; ready = current.file?.takeIf { it.exists() }; lastError = current.error
        saved = current.savedUri.isNotEmpty() || saved
        progress.isIndeterminate = current.running && current.progress < 0
        progress.setProgress(current.progress.coerceAtLeast(0), motionEnabled())
        refresh()
        if (busy) {
            val parts = mutableListOf<String>()
            if (current.progress >= 0) parts += "${current.progress}%"
            if (current.speed.isNotBlank()) parts += text("скорость ${current.speed}", "speed ${current.speed}")
            if (current.etaSeconds >= 0 && current.progress in 0..99)
                parts += text("осталось ${formatDuration(current.etaSeconds)}", "${formatDuration(current.etaSeconds)} left")
            val details = parts.joinToString(" · ").ifBlank { text("Вычисляем скорость и время…", "Calculating speed and time…") }
            status.text = (current.stage.ifBlank { text("Загрузка", "Downloading") }) + "\n" + details
        }
        else {
            followingDownload = false
            if (android.os.Build.VERSION.SDK_INT < 29 && ready != null && !saved && lastError.isEmpty()) save.performClick()
            offerPendingShare()
        }
    }
    private fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> text("${seconds} сек", "${seconds}s")
        seconds < 3600 -> text("${seconds / 60} мин ${seconds % 60} сек", "${seconds / 60}m ${seconds % 60}s")
        else -> text("${seconds / 3600} ч ${(seconds % 3600) / 60} мин", "${seconds / 3600}h ${(seconds % 3600) / 60}m")
    }
    private fun saveToMediaStore(file: File, rawTitle: String, mime: String) {
        if (busy) return
        busy = true; saved = false; lastError = ""; refresh()
        status.text = text("Сохраняем в папку устройства…", "Saving to your device folders…")
        val safeTitle = rawTitle.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(100).ifBlank { "Flow" }
        val extension = file.extension.lowercase()
        val collection = if (mime.startsWith("audio/")) android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val relativePath = if (mime.startsWith("audio/")) "Music/Flow" else "Movies/Flow"
        worker.execute {
            var uri: android.net.Uri? = null
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$safeTitle.$extension")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(collection, values) ?: error("Could not create media entry")
                contentResolver.openOutputStream(uri!!, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: error("Could not open media destination")
                check(contentResolver.update(uri!!, android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) == 1) { "Could not finish media file" }
                DownloadHistory(applicationContext).add(SavedDownload(uri.toString(), safeTitle, mime, file.length(), System.currentTimeMillis(), exportThumbnail))
                DownloadService.markSaved(file, uri.toString())
                runOnUiThread { if (!isDestroyed) saved = true }
            } catch (e: Exception) {
                uri?.let { runCatching { contentResolver.delete(it, null, null) } }
                runOnUiThread { if (!isDestroyed) lastError = e.message ?: "Save failed" }
            } finally {
                runOnUiThread { if (!isDestroyed) { busy = false; refresh() } }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        main.post(downloadPoll)
    }
    override fun onStop() {
        main.removeCallbacks(downloadPoll)
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }
    private fun handleShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND || incoming.type != "text/plain") return
        val shared = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(8192)
        val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(shared)
            .mapNotNull { runCatching { engine.canonicalUrl(it.value.trimEnd('.', ',', ')', ']')) }.getOrNull() }.firstOrNull()
        incoming.action = null
        if (url == null) {
            Toast.makeText(this, text("В сообщении нет ссылки YouTube.", "No YouTube link in the shared text."), Toast.LENGTH_LONG).show()
            return
        }
        if (busy || ready != null) {
            pendingShare = url
            Toast.makeText(this, text("Ссылка принята. Текущая загрузка не прервана.", "Link received. Your current download is safe."), Toast.LENGTH_LONG).show()
            if (!busy) offerPendingShare()
        } else input.setText(url)
    }
    private fun offerPendingShare() {
        val url = pendingShare ?: return
        if (busy || isFinishing || isDestroyed) return
        pendingShare = null
        AlertDialog.Builder(this).setTitle(text("Открыть новую ссылку?", "Open the shared link?"))
            .setMessage(text("Сначала сохраните готовый файл, если он нужен. Новая ссылка заменит текущий результат.", "Save the prepared file first if you need it. The new link replaces the current result."))
            .setPositiveButton(text("Открыть", "Open")) { _, _ -> input.setText(url) }
            .setNegativeButton(text("Позже", "Later")) { _, _ -> pendingShare = url }.show()
    }
    @Deprecated("Used for the minimal prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val file = ready ?: return
        val savedTitle = exportTitle
        val savedMime = exportMime
        val savedThumbnail = exportThumbnail
        val permissionFlags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        job(text("Сохраняем…", "Saving…")) {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot open destination")
            // Some document providers do not support persistent grants; the file is still saved.
            val retained = runCatching {
                require(permissionFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (permissionFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess
            val recorded = runCatching {
                DownloadHistory(applicationContext).add(SavedDownload(uri.toString(), savedTitle, savedMime, file.length(), System.currentTimeMillis(), savedThumbnail))
            }.isSuccess
            runOnUiThread {
                saved = true
                DownloadService.markSaved(file, uri.toString())
                if (!retained || !recorded) Toast.makeText(this,
                    text("Файл сохранён. Постоянный доступ через историю недоступен; откройте файл из выбранной папки.",
                        "File saved. Persistent history access is unavailable; open it from your chosen folder."), Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun label(value: String, size: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.WHITE)
        setPadding(0, dp(12), 0, dp(12)); if (size >= 24) setTypeface(null, Typeface.BOLD)
        root.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false; setTextColor(Color.rgb(20, 28, 16))
        background = GradientDrawable().apply { setColor(lime); cornerRadius = dp(16).toFloat() }
        root.addView(this, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12); bottomMargin = dp(8) })
        setOnTouchListener { view, event ->
            if (motionEnabled()) when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(.985f).scaleY(.985f).setDuration(80L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
            }
            false
        }
        setOnClickListener { action() }
    }
    override fun onDestroy() {
        settingsDialog?.dismiss()
        super.onDestroy()
        worker.shutdownNow()
        imageWorker.shutdownNow()
        main.removeCallbacks(downloadPoll)
        if (DownloadService.state?.running != true)
            Thread { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById("flow-analyze") }.start()
    }
}
