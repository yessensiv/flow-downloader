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
    private val updateWorker = Executors.newSingleThreadExecutor()
    private lateinit var thumbnail: ImageView
    private lateinit var engine: MediaEngine
    private lateinit var root: LinearLayout
    private lateinit var input: EditText
    private lateinit var clearLink: TextView
    private lateinit var status: TextView
    private lateinit var cancelDownload: Button
    private lateinit var pauseDownload: Button
    private lateinit var retryDownload: Button
    private lateinit var fileSize: TextView
    private lateinit var editInfo: Button
    private var infoDialog: android.app.Dialog? = null
    private lateinit var codecLabel: TextView
    private lateinit var codecSpinner: Spinner
    private lateinit var audioLanguageLabel: TextView
    private lateinit var audioLanguageSpinner: Spinner
    private var selectedCodec = "Auto"
    private var selectedAudioLanguage: String? = null
    private var codecKeys = emptyList<String>()
    private var languageKeys = emptyList<String>()
    private var updatingFilters = false
    private var checkingUpdate = false
    private var checkingDuplicate = false
    private var duplicateDialog: AlertDialog? = null
    private var updateNotice: LinearLayout? = null
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
    private var appSettingsDialog: android.app.Dialog? = null
    private var cancelDialog: android.app.Dialog? = null
    private lateinit var settingsButton: ImageButton
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
    private var exportSourceUrl = ""
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
    private var checkClipboardOnFocus = false
    private var openedFromShare = false
    private var clipboardOffer: AlertDialog? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val downloadPoll = object : Runnable {
        override fun run() {
            if (followingDownload) syncDownload()
            main.postDelayed(this, 500)
        }
    }
    private val palette get() = AppTheme.colors(this)
    private val lime get() = palette.accent
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)
        english = getPreferences(0).getBoolean("english", false)
        engine = MediaEngine(applicationContext)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.color(15, 20, 16)); isFillViewport = true
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
            settingsButton = ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_settings_outline)
                imageTintList = ColorStateList.valueOf(lime)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = surface()
                setOnClickListener { showAppSettings() }
            }
            addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        })
        heading = label("", 24).apply { setPadding(0, dp(16), 0, dp(4)) }
        subtitle = label("", 14).apply { setTextColor(palette.color(170, 185, 169)); setPadding(0, 0, 0, dp(8)) }
        mode = button("") { switchMode(false) }
        audioMode = button("") { switchMode(true) }
        root.removeView(mode); root.removeView(audioMode)
        root.addView(LinearLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = surface()
            addView(mode, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(audioMode, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(20) })
        linkLabel = label("", 14).apply { setTextColor(palette.color(175, 190, 174)); setPadding(0, 0, 0, dp(10)) }
        input = EditText(this).apply {
            textSize = 16f; setSingleLine(); setTextColor(palette.text); setHintTextColor(palette.color(144, 159, 144))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = surface(); setPadding(dp(16), dp(14), dp(54), dp(14))
            minimumHeight = dp(58)
            setSelectAllOnFocus(true)
        }
        clearLink = TextView(this).apply {
            text = "×"; textSize = 28f; gravity = android.view.Gravity.CENTER
            setTextColor(palette.color(175, 190, 174)); isFocusable = false
            contentDescription = text("Очистить ссылку", "Clear link")
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(50, 194, 255, 112)), null,
                GradientDrawable().apply { setColor(palette.text); cornerRadius = dp(16).toFloat() })
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
            background = surface(palette.color(15, 20, 16)); clipToOutline = true
            visibility = View.GONE
        }
        title = label("", 18).apply { setPadding(0, 0, 0, dp(4)); setTypeface(null, Typeface.BOLD); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        qualityLabel = label("", 14).apply { setTextColor(palette.color(175, 190, 174)); setPadding(0, 0, 0, dp(6)) }
        spinner = optionSpinner { qualityLabel.text }.apply { background = surface(); minimumHeight = dp(56); setPadding(dp(10), 0, dp(10), 0) }
        root.addView(spinner)
        codecLabel = label("", 14)
        codecSpinner = optionSpinner { codecLabel.text }.apply { background = surface(); minimumHeight = dp(52) }
        audioLanguageLabel = label("", 14)
        audioLanguageSpinner = optionSpinner { audioLanguageLabel.text }.apply { background = surface(); minimumHeight = dp(52) }
        fun filterListener(codec: Boolean) = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingFilters) return
                if (position != (if (codec) codecSpinner else audioLanguageSpinner).selectedItemPosition) return
                if (codec) {
                    val key = codecKeys.getOrNull(position) ?: return
                    if (key == selectedCodec) return
                    selectedCodec = key
                } else {
                    val key = languageKeys.getOrNull(position) ?: return
                    if (key == selectedAudioLanguage) return
                    selectedAudioLanguage = key
                }
                refreshChoices(); refreshExportOptions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        codecSpinner.onItemSelectedListener = filterListener(true)
        audioLanguageSpinner.onItemSelectedListener = filterListener(false)
        val exportPrefs = getSharedPreferences("export", MODE_PRIVATE)
        containerLabel = label("", 14)
        containerSpinner = optionSpinner { containerLabel.text }.apply {
            background = surface(); minimumHeight = dp(54)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("MKV", "MP4"))
        }
        bitrateLabel = label("", 14)
        bitrateHint = label("", 12).apply {
            setTextColor(palette.color(153, 170, 150)); setPadding(dp(2), 0, dp(2), dp(6))
        }
        bitrateSpinner = optionSpinner { bitrateLabel.text }.apply {
            background = surface(); minimumHeight = dp(54)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bitrates.map { "$it kbps" })
            setSelection(bitrates.indexOf(exportPrefs.getInt("bitrate", 192)).coerceAtLeast(0))
        }
        embedMetadata = Switch(this).apply {
            setTextColor(palette.text); isChecked = exportPrefs.getBoolean("metadata", true)
            setOnCheckedChangeListener { _, checked -> exportPrefs.edit().putBoolean("metadata", checked).apply() }
        }
        embedCover = Switch(this).apply {
            setTextColor(palette.text); isChecked = exportPrefs.getBoolean("cover", true)
            setOnCheckedChangeListener { _, checked -> exportPrefs.edit().putBoolean("cover", checked).apply() }
        }
        listOf(containerLabel, bitrateLabel).forEach {
            it.setTextColor(palette.color(175, 190, 174)); it.setPadding(0, dp(4), 0, dp(4))
        }
        listOf(spinner, containerSpinner, bitrateSpinner).forEach {
            it.background = surface(palette.color(18, 25, 19)); it.minimumHeight = dp(52)
            it.setPadding(dp(2), 0, dp(2), 0)
        }
        listOf(embedMetadata, embedCover).forEach {
            it.textSize = 15f; it.minimumHeight = dp(64)
            it.setPadding(dp(12), dp(10), dp(12), dp(10))
            it.background = surface(palette.color(22, 30, 23))
            it.switchPadding = dp(12)
            it.thumbTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(palette.color(78, 91, 77), lime, palette.color(151, 166, 147)))
            it.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(palette.color(80, 107, 49), palette.color(55, 66, 55)))
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
                checkDuplicate(found, selected.audio) {
                    if (busy || media !== found || audio != selected.audio) return@checkDuplicate
                    ready?.parentFile?.deleteRecursively(); ready = null
                    if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20)
                    try {
                        DownloadService.forgetCompleted()
                        startForegroundService(Intent(this, DownloadService::class.java).apply {
                            putExtra("mediaJson", MediaTransfer.encode(found)); putExtra("choiceJson", MediaTransfer.choice(selected).toString())
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
        }
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surface(); setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        exportFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(codecLabel, codecSpinner, audioLanguageLabel, audioLanguageSpinner, qualityLabel, spinner, containerLabel, containerSpinner, bitrateLabel, bitrateSpinner, bitrateHint).forEach {
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
        fileSize = TextView(this).apply { textSize = 13f; setTextColor(palette.color(175, 190, 174)); setPadding(0, dp(4), 0, dp(4)) }
        editInfo = button("") { showMediaEditor() }
        listOf(resultLabel, thumbnail, title, editInfo, exportSummary, fileSize, download).forEach {
            root.removeView(it); resultCard.addView(it, LinearLayout.LayoutParams(-1, if (it == download || it is Spinner) dp(54) else -2).apply { bottomMargin = dp(if (it == qualityLabel || it == bitrateLabel || it == containerLabel) 4 else 10) })
        }
        root.addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        progress.progressTintList = ColorStateList.valueOf(lime)
        progress.indeterminateTintList = ColorStateList.valueOf(lime)
        root.addView(progress)
        status = label("", 14).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        cancelDownload = button("") { showCancelDownload() }
        style(cancelDownload, false)
        pauseDownload = button("") {
            val current = DownloadService.state ?: return@button
            val request = Intent(this, DownloadService::class.java).setAction(if (current.paused) DownloadService.RESUME else DownloadService.PAUSE)
            if (current.paused) startForegroundService(request) else startService(request)
        }
        retryDownload = button("") {
            if (busy) return@button
            startForegroundService(Intent(this, DownloadService::class.java).setAction(DownloadService.RETRY).putExtra("english", english))
            followingDownload = true; busy = true; saved = false; lastError = ""; refresh()
        }
        save = button("") {
            if (saved || busy) return@button
            val file = ready ?: return@button
            val mime = MediaStorage.mime(file, audio)
            exportTitle = media?.title ?: "Flow"
            exportMime = mime
            exportThumbnail = media?.thumbnail.orEmpty()
            exportSourceUrl = media?.url.orEmpty()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                saveToMediaStore(file, exportTitle, mime)
                return@button
            }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = mime
                putExtra(Intent.EXTRA_TITLE, MediaStorage.displayName(media ?: Media("", "Flow", emptyList()), file.extension))
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
        root.removeView(update)
        historyButton.apply {
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            setPadding(dp(16), 0, dp(16), 0)
            compoundDrawablePadding = dp(12)
            compoundDrawableTintList = ColorStateList.valueOf(lime)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_folder_outline, 0, 0, 0)
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
        DownloadService.restore(applicationContext)
        if (DownloadService.state != null) {
            followingDownload = true; syncDownload()
        }
        handleShare(intent)
        savedInstanceState?.let { state ->
            input.setText(state.getString("link", ""))
            audio = state.getBoolean("audio")
            selectedCodec = state.getString("codec", "Auto")
            selectedAudioLanguage = state.getString("track")
            media = state.getString("media")?.let { runCatching { MediaTransfer.decode(it) }.getOrNull() }
            media?.let { title.text = it.title; refreshChoices(); loadThumbnail(it) }
            spinner.setSelection(state.getInt("choice", 0).coerceAtLeast(0))
            containerSpinner.setSelection(state.getInt("container", 0))
            if (DownloadService.state != null) { followingDownload = true; syncDownload() }
            refresh()
        }
        checkStartupUpdate()
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
    private fun surface(color: Int = palette.color(28, 36, 29)) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), palette.color(49, 62, 49))
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
        button.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), surface(if (primary) lime else palette.color(28, 36, 29)), null)
        button.setTextColor(if (primary) palette.onAccent else palette.color(220, 231, 216))
        button.setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        button.alpha = if (button.isEnabled) 1f else .45f
    }
    private fun refresh() {
        settingsButton.contentDescription = text("Настройки", "Settings")
        settingsButton.isEnabled = !busy
        language.text = if (english) "EN · RU" else "RU · EN"
        heading.text = text("Любимое — с собой.", "Keep what you love.")
        subtitle.text = text("Видео и музыка прямо на телефоне.", "Video and music, right on your phone.")
        linkLabel.text = text("Ссылка на YouTube", "YouTube link")
        resultLabel.text = text("✓  Найдено на YouTube", "✓  Found on YouTube")
        mode.text = text("Видео", "Video"); audioMode.text = text("Аудио", "Audio")
        qualityLabel.text = if (audio) text("Формат аудио", "Audio format") else text("Качество видео", "Video quality")
        input.hint = text("Вставьте ссылку YouTube", "Paste a YouTube link")
        analyze.text = text("Показать варианты", "Show options")
        download.text = if (checkingDuplicate) text("Проверяем загрузки…", "Checking downloads…") else text("↓  Подготовить файл", "↓  Prepare download")
        editInfo.text = text("Название и файл  ›", "Title and file  ›")
        editInfo.isEnabled = !busy && ready == null && !saved
        style(editInfo, false)
        save.text = if (saved) text("✓  Уже сохранено", "✓  Already saved") else text("Сохранить в папку Flow", "Save to Flow folder")
        update.text = text("↻  Обновить движок", "↻  Update engine")
        historyButton.text = text("Мои загрузки", "My downloads")
        style(historyButton, false)
        listOf(mode, audioMode, input, analyze, update).forEach { it.isEnabled = !busy }
        analyze.isEnabled = !busy && input.text.isNotBlank()
        spinner.isEnabled = !busy && choices.isNotEmpty()
        refreshExportOptions()
        download.isEnabled = !busy && !checkingDuplicate && choices.isNotEmpty()
        save.visibility = if (ready != null && !busy && !saved) View.VISIBLE else View.GONE
        save.isEnabled = !busy && !saved
        title.visibility = if (media != null) View.VISIBLE else View.GONE
        val wasCardVisible = resultCard.visibility == View.VISIBLE
        resultCard.visibility = title.visibility
        if (!wasCardVisible && resultCard.visibility == View.VISIBLE) revealResultCard()
        qualityLabel.visibility = title.visibility; spinner.visibility = title.visibility
        download.visibility = if (media != null && ready == null && !saved) View.VISIBLE else View.GONE
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        cancelDownload.text = text("✕  Отменить загрузку", "✕  Cancel download")
        cancelDownload.visibility = if (busy && DownloadService.state?.running == true && DownloadService.state?.saving != true) View.VISIBLE else View.GONE
        val transfer = DownloadService.state
        pauseDownload.visibility = if (busy && transfer?.running == true && !transfer.saving) View.VISIBLE else View.GONE
        pauseDownload.text = if (transfer?.pausing == true) text("Ставим на паузу…", "Pausing…") else if (transfer?.paused == true) text("Продолжить загрузку", "Resume download") else text("Пауза", "Pause")
        pauseDownload.isEnabled = transfer?.pausing != true && transfer?.processing != true
        style(pauseDownload, transfer?.paused == true)
        retryDownload.text = text("Повторить с теми же настройками", "Retry with the same settings")
        retryDownload.visibility = if (!busy && transfer != null && transfer.error.isNotBlank() && transfer.error != DownloadService.CANCELLED && transfer.file == null) View.VISIBLE else View.GONE
        style(retryDownload, true)
        details.text = text("Подробности ошибки", "Error details")
        details.visibility = if (lastError.isNotEmpty() && lastError != DownloadService.CANCELLED) View.VISIBLE else View.GONE
        style(language, false); style(mode, !audio); style(audioMode, audio)
        style(analyze, analyze.isEnabled); style(download, true); style(save, true); style(update, false); style(details, false)
        listOf(mode, audioMode).forEach { tab ->
            val selected = (tab === audioMode) == audio
            tab.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(35, 255, 255, 255)),
                GradientDrawable().apply { setColor(if (selected) lime else Color.TRANSPARENT); cornerRadius = dp(13).toFloat() }, null)
        }
        if (!busy) status.text = if (lastError.isNotEmpty()) friendlyError(lastError) else if (saved) text("✓ Сохранено в ${if (audio) "Music/Flow" else "Movies/Flow"}.", "✓ Saved to ${if (audio) "Music/Flow" else "Movies/Flow"}.") else if (ready != null) text("Готово! Сохраните файл на устройстве.", "Ready! Save the file to your device.") else text("Без сервера · Загрузка работает в фоне\nВ YouTube нажмите «Поделиться» → Flow.", "No server · Downloads work in the background\nIn YouTube, tap Share → Flow.")
        status.setTextColor(if (lastError.isNotEmpty() && lastError != DownloadService.CANCELLED) palette.color(255, 171, 151) else palette.color(175, 190, 174))
        if (!busy && lastError.isEmpty() && !saved && ready == null) {
            status.text = text("Вставьте ссылку или поделитесь видео из YouTube.", "Paste a link or share a video from YouTube.")
        }
    }
    private fun showCancelDownload() {
        if (cancelDialog?.isShowing == true) return
        val dialog = android.app.Dialog(this)
        cancelDialog = dialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = surface(palette.color(24, 32, 25))
        }
        panel.addView(TextView(this).apply {
            text = text("Остановить загрузку?", "Stop downloading?")
            textSize = 22f; setTextColor(palette.text); setTypeface(null, Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = text("Незавершённый файл будет удалён. Загрузку можно начать заново.", "The unfinished file will be removed. You can start the download again.")
            textSize = 15f; setTextColor(palette.color(175, 190, 174)); setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(24) })
        panel.addView(Button(this).apply {
            text = text("Продолжить загрузку", "Keep downloading")
            textSize = 16f; isAllCaps = false; style(this, true)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        panel.addView(Button(this).apply {
            text = text("Остановить", "Stop download")
            textSize = 16f; isAllCaps = false; style(this, false)
            setTextColor(palette.color(255, 174, 157))
            setOnClickListener {
                val current = DownloadService.state
                if (current?.running == true && !current.saving)
                    startService(Intent(this@MainActivity, DownloadService::class.java).setAction(DownloadService.CANCEL))
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        dialog.setContentView(ScrollView(this).apply { addView(panel) })
        dialog.setOnDismissListener { cancelDialog = null }
        dialog.window?.apply {
            setWindowAnimations(0)
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels - dp(40)).coerceAtLeast(dp(240)), -2)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(.55f)
        }
        dialog.show()
    }

    private fun checkDuplicate(found: Media, audio: Boolean, proceed: () -> Unit) {
        if (checkingDuplicate || busy) return
        checkingDuplicate = true; refresh()
        worker.execute {
            val existing = DownloadHistory(applicationContext).duplicate(found, audio) { item ->
                contentResolver.openAssetFileDescriptor(android.net.Uri.parse(item.uri), "r")?.use { true } ?: false
            }
            runOnUiThread {
                checkingDuplicate = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                refresh()
                if (media !== found || this.audio != audio || busy) return@runOnUiThread
                if (existing == null) { proceed(); return@runOnUiThread }
                duplicateDialog?.dismiss()
                duplicateDialog = AlertDialog.Builder(this)
                    .setTitle(text("Уже скачано", "Already downloaded"))
                    .setMessage(text("Этот ${if (audio) "аудиофайл" else "ролик"} уже есть на устройстве:\n${existing.title}\n\nСкачать ещё одну копию?",
                        "This ${if (audio) "audio" else "video"} is already on your device:\n${existing.title}\n\nDownload another copy?"))
                    .setPositiveButton(text("Скачать ещё раз", "Download again")) { _, _ -> proceed() }
                    .setNeutralButton(text("Открыть", "Open")) { _, _ ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(existing.uri), existing.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            .onFailure { Toast.makeText(this, text("Не удалось открыть файл", "Could not open file"), Toast.LENGTH_SHORT).show() }
                    }
                    .setNegativeButton(text("Отмена", "Cancel"), null).show()
            }
        }
    }
    private fun checkStartupUpdate() {
        val prefs = getSharedPreferences("updates", 0)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked", 0) < 86_400_000L) return
        prefs.edit().putLong("checked", now).apply()
        updateWorker.execute {
            val release = runCatching { AppUpdates.latest() }.getOrNull() ?: return@execute
            val installed = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            if (!runCatching { AppUpdates.isNewer(release.version, installed) }.getOrDefault(false)) return@execute
            runOnUiThread {
                if (isDestroyed || isFinishing || (prefs.getString("dismissed", "") == release.version &&
                    System.currentTimeMillis() - prefs.getLong("dismissedAt", 0) < 86_400_000L)) return@runOnUiThread
                val notice = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = surface()
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    addView(TextView(this@MainActivity).apply {
                        text = text("Доступна Flow ${release.version}", "Flow ${release.version} is available")
                        textSize = 14f; setTextColor(palette.text)
                    })
                    val actions = LinearLayout(this@MainActivity)
                    actions.addView(Button(this@MainActivity).apply {
                        text = text("Обновить", "Update"); isAllCaps = false; style(this, true)
                        setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.url))) }
                    }, LinearLayout.LayoutParams(0, dp(48), 1f))
                    actions.addView(Button(this@MainActivity).apply {
                        text = text("Позже", "Later"); isAllCaps = false; style(this, false)
                        setOnClickListener { prefs.edit().putString("dismissed", release.version).putLong("dismissedAt", System.currentTimeMillis()).apply(); updateNotice?.let { root.removeView(it) }; updateNotice = null }
                    }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
                    addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                }
                updateNotice = notice
                root.addView(notice, 1, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            }
        }
    }
    private fun showMediaEditor() {
        val original = media ?: return
        if (busy || ready != null || saved) return
        infoDialog?.dismiss()
        val dialog = android.app.Dialog(this)
        infoDialog = dialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = surface()
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        panel.addView(TextView(this).apply {
            text = text("Название и файл", "Title and file"); textSize = 22f
            setTextColor(palette.text); setTypeface(null, Typeface.BOLD)
        })
        fun field(caption: String, value: String, hint: String): EditText {
            panel.addView(TextView(this).apply {
                text = caption; textSize = 14f; setTextColor(palette.color(175, 190, 174))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(8) })
            return EditText(this).apply {
                setText(value); this.hint = hint; textSize = 16f; setSingleLine()
                setTextColor(palette.text); setHintTextColor(palette.color(144, 159, 144))
                background = surface(); minimumHeight = dp(52); setPadding(dp(12), dp(8), dp(12), dp(8))
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters = arrayOf(android.text.InputFilter.LengthFilter(200))
                contentDescription = caption
                panel.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val name = field(text("Название в плеере", "Title in player"), original.title, "")
        val artist = field(text("Исполнитель", "Artist"), original.artist, text("Необязательно", "Optional"))
        val file = field(text("Имя файла", "File name"), original.fileName.ifBlank { MediaStorage.safeName(original.title) }, "")
        panel.addView(TextView(this).apply {
            text = text("Расширение добавится автоматически. Название и исполнитель записываются в файл, если включены метаданные.",
                "The extension is added automatically. Title and artist are written into the file when metadata is enabled.")
            textSize = 12f; setTextColor(palette.color(175, 190, 174))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply {
            text = text("Отмена", "Cancel"); isAllCaps = false; style(this, false)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(Button(this).apply {
            text = text("Сохранить", "Save"); isAllCaps = false; style(this, true)
            setOnClickListener {
                if (name.text.isBlank()) { name.error = text("Введите название", "Enter a title"); name.requestFocus(); return@setOnClickListener }
                if (file.text.isBlank()) { file.error = text("Введите имя файла", "Enter a file name"); file.requestFocus(); return@setOnClickListener }
                media = original.copy(title = name.text.toString().trim(), artist = artist.text.toString().trim(), fileName = MediaStorage.safeName(file.text.toString()))
                title.text = media!!.title; refresh(); dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        dialog.setContentView(ScrollView(this).apply { addView(panel) })
        dialog.setOnDismissListener { if (infoDialog === dialog) infoDialog = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); setGravity(android.view.Gravity.BOTTOM)
            setLayout(-1, -2); setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    private fun showAppSettings() {
        if (busy) return
        appSettingsDialog?.dismiss()
        val dialog = android.app.Dialog(this)
        appSettingsDialog = dialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surface()
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        fun caption(value: String, size: Int, muted: Boolean = false) = TextView(this).apply {
            text = value; textSize = size.toFloat()
            setTextColor(if (muted) palette.color(175, 190, 174) else palette.text)
        }
        val heading = caption("", 22).apply { setTypeface(null, Typeface.BOLD) }
        panel.addView(heading)
        val languageLabel = caption("", 14, true)
        panel.addView(languageLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(10) })
        val languages = LinearLayout(this)
        val russian = Button(this).apply { text = "Русский"; isAllCaps = false; textSize = 15f }
        val englishButton = Button(this).apply { text = "English"; isAllCaps = false; textSize = 15f }
        languages.addView(russian, LinearLayout.LayoutParams(0, dp(48), 1f))
        languages.addView(englishButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        panel.addView(languages)
        val themeCaption = caption(text("Тема оформления", "Appearance"), 14, true)
        panel.addView(themeCaption,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(10) })
        val themes = LinearLayout(this)
        listOf("dark" to text("Тёмная", "Dark"), "light" to text("Светлая", "Light"), "system" to text("Системная", "System")).forEachIndexed { index, (key, label) ->
            themes.addView(Button(this).apply {
                this.text = label; textSize = 13f; isAllCaps = false; setPadding(dp(4), 0, dp(4), 0)
                style(this, AppTheme.mode(this@MainActivity) == key)
                setOnClickListener {
                    if (AppTheme.mode(this@MainActivity) != key) {
                        getSharedPreferences("appearance", 0).edit().putString("theme", key).apply()
                        dialog.dismiss(); recreate()
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (index > 0) leftMargin = dp(6) })
        }
        panel.addView(themes)
        val qualityCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(palette.color(24, 31, 25)); setStroke(dp(1), palette.color(54, 66, 54)); cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }
        val qualityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val qualityTitle = caption("", 15)
        val qualityDescription = caption("", 12, true).apply { setLineSpacing(dp(2).toFloat(), 1f) }
        qualityText.addView(qualityTitle)
        qualityText.addView(qualityDescription, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        qualityCard.addView(qualityText, LinearLayout.LayoutParams(0, -2, 1f))
        val allQualities = Switch(this).apply {
            isChecked = getPreferences(0).getBoolean("allQualities", false)
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(lime, palette.color(142, 153, 143)))
            setOnCheckedChangeListener { _, checked ->
                getPreferences(0).edit().putBoolean("allQualities", checked).apply()
                refreshChoices()
            }
        }
        qualityCard.addView(allQualities, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        panel.addView(qualityCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        val updateAction = Button(this).apply {
            isAllCaps = false; textSize = 16f
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { dialog.dismiss(); update.performClick() }
        }
        panel.addView(updateAction, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(20) })
        val appUpdate = Button(this).apply {
            isAllCaps = false; textSize = 16f; minHeight = dp(52)
            setOnClickListener { dialog.dismiss(); checkAppUpdate() }
        }
        panel.addView(appUpdate, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        val done = Button(this).apply { isAllCaps = false; textSize = 16f; setOnClickListener { dialog.dismiss() } }
        panel.addView(done, LinearLayout.LayoutParams(-1, dp(48)))
        fun refreshPanel() {
            heading.text = text("Настройки", "Settings")
            languageLabel.text = text("Язык приложения", "App language")
            themeCaption.text = text("Тема оформления", "Appearance")
            listOf(text("Тёмная", "Dark"), text("Светлая", "Light"), text("Системная", "System")).forEachIndexed { index, value ->
                (themes.getChildAt(index) as Button).text = value
            }
            qualityTitle.text = text("Показывать все качества", "Show all qualities")
            qualityDescription.text = text("Включая форматы, которые телефон может не воспроизвести", "Also show formats your phone may not play")
            style(russian, !english); style(englishButton, english)
            updateAction.text = optionCaption(text("Обновить движок YouTube", "Update YouTube engine"),
                text("Может помочь при ошибках скачивания", "May help with download errors"))
            style(updateAction, false)
            appUpdate.text = text("Проверить обновление Flow", "Check for Flow updates"); style(appUpdate, false)
            done.text = text("Готово", "Done"); style(done, true)
        }
        fun setLanguage(value: Boolean) {
            english = value
            getPreferences(0).edit().putBoolean("english", english).apply()
            exportLanguage = null; refreshChoices(); refresh(); refreshPanel()
        }
        russian.setOnClickListener { setLanguage(false) }
        englishButton.setOnClickListener { setLanguage(true) }
        refreshPanel()
        dialog.setContentView(ScrollView(this).apply { addView(panel) })
        dialog.setOnDismissListener { if (appSettingsDialog === dialog) appSettingsDialog = null }
        dialog.window?.apply {
            setWindowAnimations(0)
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(-1, -2)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.55f)
        }
        dialog.show()
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
        codecLabel.text = text("Кодек видео", "Video codec")
        codecLabel.visibility = if (audio) View.GONE else View.VISIBLE
        codecSpinner.visibility = codecLabel.visibility
        codecSpinner.isEnabled = !busy
        audioLanguageLabel.text = text("Язык аудиодорожки", "Audio track language")
        audioLanguageLabel.visibility = if (languageKeys.size > 1) View.VISIBLE else View.GONE
        audioLanguageSpinner.visibility = audioLanguageLabel.visibility
        audioLanguageSpinner.isEnabled = !busy
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
        if (::fileSize.isInitialized) {
            val bytes = DownloadOptions.estimatedSize(choice, rate)
            val size = if (bytes > 0) android.text.format.Formatter.formatShortFileSize(this, bytes) else ""
            fileSize.text = if (size.isNotBlank()) text("Примерный размер: $size", "Estimated size: $size") else text("Размер файла пока неизвестен", "File size is not available yet")
            if (!audio && choice != null) fileSize.text = fileSize.text.toString() + " · ${choice.codec}" + if (!choice.deviceCompatible) text("\nТелефон может не воспроизвести этот формат", "\nYour phone may not play this format") else ""
        }
    }

    private fun optionSpinner(caption: () -> CharSequence) = object : Spinner(this, Spinner.MODE_DIALOG) {
        override fun performClick(): Boolean {
            if (!isEnabled || adapter == null || adapter.count == 0) return false
            val labels = Array(adapter.count) { adapter.getItem(it).toString() }
            val options = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_single_choice, labels) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    return (super.getView(position, convertView, parent) as CheckedTextView).apply {
                        textSize = 16f
                        setTextColor(if (position == selectedItemPosition) lime else palette.color(235, 241, 232))
                        checkMarkTintList = ColorStateList.valueOf(lime)
                        minHeight = dp(54)
                        setPadding(dp(20), dp(12), dp(20), dp(12))
                    }
                }
            }
            val picker = AlertDialog.Builder(this@MainActivity)
                .setTitle(caption())
                .setSingleChoiceItems(options, selectedItemPosition) { dialog, position ->
                    setSelection(position)
                    refreshExportOptions()
                    dialog.dismiss()
                }
                .setNegativeButton(text("Отмена", "Cancel"), null)
                .create()
            picker.window?.apply {
                setWindowAnimations(0)
                setBackgroundDrawable(surface(palette.color(22, 30, 23)))
            }
            picker.show()
            picker.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(lime)
            return true
        }
    }

    private fun showExportSettings() {
        if (busy || choices.isEmpty()) return
        val originalChoice = spinner.selectedItemPosition
        val originalContainer = containerSpinner.selectedItemPosition
        val originalBitrate = bitrateSpinner.selectedItemPosition
        val originalMetadata = embedMetadata.isChecked
        val originalCover = embedCover.isChecked
        val originalCodec = selectedCodec
        val originalLanguage = selectedAudioLanguage
        var confirmed = false
        (exportFields.parent as? android.view.ViewGroup)?.removeView(exportFields)
        val dialog = android.app.Dialog(this)
        settingsDialog = dialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
            background = surface()
            addView(TextView(this@MainActivity).apply {
                text = text("Настройки скачивания", "Download settings"); textSize = 21f
                setTextColor(palette.text); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(16))
            })
            addView(exportFields)
            val actions = LinearLayout(this@MainActivity)
            actions.addView(Button(this@MainActivity).apply {
                text = text("Отмена", "Cancel"); isAllCaps = false; style(this, false)
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
            actions.addView(Button(this@MainActivity).apply {
                text = text("Готово", "Done"); isAllCaps = false; style(this, true)
                setOnClickListener { confirmed = true; dialog.dismiss() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        dialog.setContentView(ScrollView(this).apply { addView(panel) })
        dialog.setOnDismissListener {
            if (!confirmed) {
                selectedCodec = originalCodec; selectedAudioLanguage = originalLanguage; refreshChoices()
                spinner.setSelection(originalChoice)
                containerSpinner.setSelection(originalContainer)
                bitrateSpinner.setSelection(originalBitrate)
                embedMetadata.isChecked = originalMetadata
                embedCover.isChecked = originalCover
                refreshExportOptions()
            }
            (exportFields.parent as? android.view.ViewGroup)?.removeView(exportFields)
            settingsDialog = null
        }
        dialog.window?.apply {
            setWindowAnimations(0)
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(-1, -2)
            attributes = attributes.apply { height = (resources.displayMetrics.heightPixels * .75f).toInt() }
        }
        refreshExportOptions()
        dialog.show()
    }

    private fun optionCaption(title: String, subtitle: String): CharSequence = android.text.SpannableString("$title\n$subtitle").apply {
        setSpan(android.text.style.RelativeSizeSpan(.8f), title.length + 1, length, 0)
        setSpan(android.text.style.ForegroundColorSpan(palette.color(153, 170, 150)), title.length + 1, length, 0)
    }

    private fun exportAdapter(labels: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
        private fun row(position: Int, dropdown: Boolean) = TextView(this@MainActivity).apply {
            text = getItem(position) + if (dropdown) "" else "   ▾"
            textSize = 15f; setTextColor(palette.color(235, 241, 232)); gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12)); minHeight = dp(52)
            setBackgroundColor(if (dropdown) palette.color(28, 36, 29) else Color.TRANSPARENT)
        }
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, false)
        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, true)
    }

    private fun refreshChoices() {
        val showAll = getPreferences(0).getBoolean("allQualities", false)
        val previous = choices.getOrNull(spinner.selectedItemPosition)
        val available = media?.choices?.filter { it.audio == audio && (audio || showAll || it.deviceCompatible) } ?: emptyList()
        val languages = available.distinctBy { it.language }
        val nextLanguages = languages.map { it.language }
        val nextCodecs = listOf("Auto") + available.filterNot { it.audio }.map { it.codec }.distinct()
        updatingFilters = true
        if (selectedAudioLanguage !in nextLanguages) selectedAudioLanguage = nextLanguages.firstOrNull()
        if (selectedCodec !in nextCodecs) selectedCodec = "Auto"
        if (languageKeys != nextLanguages || exportLanguage != english) {
            languageKeys = nextLanguages
            audioLanguageSpinner.adapter = exportAdapter(languages.map { languageName(it.languageLabel) })
        }
        if (codecKeys != nextCodecs || exportLanguage != english) {
            codecKeys = nextCodecs
            codecSpinner.adapter = exportAdapter(nextCodecs.map { when(it) {
                "Auto" -> text("Автоматически · совместимый", "Auto · compatible")
                "H.264" -> text("H.264 · широкая совместимость", "H.264 · wide compatibility")
                else -> it
            } })
        }
        audioLanguageSpinner.setSelection(nextLanguages.indexOf(selectedAudioLanguage).coerceAtLeast(0))
        codecSpinner.setSelection(nextCodecs.indexOf(selectedCodec).coerceAtLeast(0))
        choices = DownloadOptions.select(available, audio, selectedCodec, selectedAudioLanguage)
        val labels = choices.map { choice ->
            if (choice.extractAudio && choice.format.isEmpty() && !choice.mp3) text("M4A · только звук", "M4A · audio only") else choice.label.replace("original", text("оригинал", "original"))
        }.ifEmpty {
            listOf(if (audio) text("Аудио для этого видео недоступно", "Audio is unavailable for this video")
                else text("Нет доступных форматов", "No formats available"))
        }
        spinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            private fun row(position: Int, dropdown: Boolean) = TextView(this@MainActivity).apply {
                text = getItem(position) + if (dropdown) "" else "   ▾"
                textSize = 16f; setTextColor(if (choices.isEmpty()) palette.color(144, 159, 144) else palette.color(235, 241, 232))
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(16))
                setBackgroundColor(if (dropdown) palette.color(28, 36, 29) else Color.TRANSPARENT)
                minHeight = dp(52)
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, false)
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = row(position, true)
        }
        spinner.setSelection(choices.indexOfFirst { it.label == previous?.label }.coerceAtLeast(0))
        updatingFilters = false
        refreshExportOptions()
    }
    private fun languageName(value: String): String {
        val key = value.substringBefore(" ·")
        val name = if (key == "und" || key.isBlank()) text("Основная дорожка", "Default track") else
            java.util.Locale.forLanguageTag(key).getDisplayLanguage(if (english) java.util.Locale.ENGLISH else java.util.Locale.forLanguageTag("ru")).ifBlank { key }
        return name + if (value.endsWith("original")) text(" · оригинал", " · original") else if (value.endsWith("auto")) text(" · автоперевод", " · auto-dubbed") else ""
    }
    private fun checkAppUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        Toast.makeText(this, text("Проверяем обновления…", "Checking for updates…"), Toast.LENGTH_SHORT).show()
        worker.execute {
            val result = runCatching { AppUpdates.latest() }
            runOnUiThread {
                checkingUpdate = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess { release ->
                    val installed = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                    val newer = AppUpdates.isNewer(release.version, installed)
                    AlertDialog.Builder(this).setTitle(if (newer) text("Доступна Flow ${release.version}", "Flow ${release.version} is available") else text("У вас последняя версия", "You're up to date"))
                        .setMessage(text("Установлена: $installed\nGitHub: ${release.version}", "Installed: $installed\nGitHub: ${release.version}"))
                        .setPositiveButton(if (newer) text("Скачать APK", "Download APK") else "OK") { _, _ ->
                            if (newer) startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.url)))
                        }.setNegativeButton(if (newer) text("Позже", "Later") else null, null).show()
                }.onFailure { Toast.makeText(this, text("Не удалось проверить. Проверьте интернет и повторите позже.", "Could not check. Check your connection and try later."), Toast.LENGTH_LONG).show() }
            }
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
            selectedCodec = current.choice.codec.ifBlank { "Auto" }; selectedAudioLanguage = current.choice.language
            title.text = current.media.title; refreshChoices(); loadThumbnail(current.media)
            spinner.setSelection(choices.indexOfFirst { it.selector == current.choice.selector && it.format == if (audio) current.choice.format else "" }.coerceAtLeast(0))
            containerSpinner.setSelection(if (current.choice.format == "mp4") 1 else 0)
            bitrateSpinner.setSelection(bitrates.indexOf(current.choice.bitrate).coerceAtLeast(0))
            embedMetadata.isChecked = current.choice.metadata; embedCover.isChecked = current.choice.cover
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
            status.text = if (current.paused || current.pausing) current.stage.ifBlank { text("На паузе", "Paused") } + if (current.paused) text("\nНажмите «Продолжить». Прогресс сохранён.", "\nTap Resume. Download progress is kept.") else "" else
                (current.stage.ifBlank { text("Загрузка", "Downloading") }) + "\n" + details
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
        val safeTitle = MediaStorage.safeName(rawTitle)
        val displayName = MediaStorage.displayName(media ?: Media("", rawTitle, emptyList()), file.extension)
        val sourceUrl = media?.url.orEmpty()
        val extension = file.extension.lowercase()
        val collection = if (mime.startsWith("audio/")) android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val relativePath = if (mime.startsWith("audio/")) "Music/Flow" else "Movies/Flow"
        worker.execute {
            var uri: android.net.Uri? = null
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
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
                DownloadHistory(applicationContext).add(SavedDownload(uri.toString(), safeTitle, mime, file.length(), System.currentTimeMillis(), exportThumbnail, sourceUrl))
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
        checkClipboardOnFocus = !openedFromShare
        main.post(downloadPoll)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && checkClipboardOnFocus && ::input.isInitialized) {
            checkClipboardOnFocus = false
            offerClipboardLink()
        }
    }
    private fun offerClipboardLink() {
        if (busy || followingDownload || pendingShare != null || clipboardOffer != null) return
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        val raw = runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.take(8192)?.toString() }.getOrNull() ?: return
        val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(raw)
            .mapNotNull { runCatching { engine.canonicalUrl(it.value.trimEnd('.', ',', ')', ']')) }.getOrNull() }.firstOrNull() ?: return
        val prefs = getPreferences(0)
        if (prefs.getString("lastClipboardUrl", null) == url) return
        prefs.edit().putString("lastClipboardUrl", url).apply()
        if (runCatching { engine.canonicalUrl(input.text.toString()) }.getOrNull() == url) return
        fun useLink() {
            if (busy || isFinishing || isDestroyed) return
            input.setText(url)
            input.setSelection(0)
            analyze.performClick()
        }
        if (input.text.isBlank() && ready == null && media == null) {
            useLink()
        } else {
            clipboardOffer = AlertDialog.Builder(this)
                .setTitle(text("Новая ссылка из буфера", "New link from clipboard"))
                .setMessage(text("Открыть эту ссылку и показать варианты скачивания?", "Open this link and show download options?") + "\n\n" + url)
                .setPositiveButton(text("Открыть", "Open")) { _, _ -> useLink() }
                .setNegativeButton(text("Не сейчас", "Not now"), null)
                .create().apply {
                    setOnDismissListener { clipboardOffer = null }
                    show()
                }
        }
    }
    override fun onStop() {
        openedFromShare = false
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
        openedFromShare = true
        checkClipboardOnFocus = false
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
                DownloadHistory(applicationContext).add(SavedDownload(uri.toString(), savedTitle, savedMime, file.length(), System.currentTimeMillis(), savedThumbnail, exportSourceUrl))
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
        text = value; textSize = size.toFloat(); setTextColor(palette.text)
        setPadding(0, dp(12), 0, dp(12)); if (size >= 24) setTypeface(null, Typeface.BOLD)
        root.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false; setTextColor(palette.onAccent)
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
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("link", input.text.toString()); outState.putBoolean("audio", audio)
        outState.putString("media", media?.let { MediaTransfer.encode(it) })
        outState.putString("codec", selectedCodec); outState.putString("track", selectedAudioLanguage)
        outState.putInt("choice", spinner.selectedItemPosition); outState.putInt("container", containerSpinner.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        duplicateDialog?.dismiss()
        infoDialog?.dismiss()
        cancelDialog?.dismiss()
        appSettingsDialog?.dismiss()
        clipboardOffer?.dismiss()
        settingsDialog?.dismiss()
        super.onDestroy()
        worker.shutdownNow()
        imageWorker.shutdownNow()
        updateWorker.shutdownNow()
        main.removeCallbacks(downloadPoll)
        if (DownloadService.state?.running != true)
            Thread { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById("flow-analyze") }.start()
    }
}
