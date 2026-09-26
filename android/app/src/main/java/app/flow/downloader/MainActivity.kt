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
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

/** First prototype: one job at a time, while the screen is open. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var engine: MediaEngine
    private lateinit var root: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var spinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var analyze: Button
    private lateinit var download: Button
    private lateinit var save: Button
    private lateinit var language: Button
    private lateinit var mode: Button
    private lateinit var audioMode: Button
    private lateinit var subtitle: TextView
    private lateinit var qualityLabel: TextView
    private lateinit var details: Button
    private lateinit var resultCard: LinearLayout
    private var saved = false
    private var lastError = ""
    private lateinit var update: Button
    private var english = false
    private var audio = false
    private var busy = false
    private var media: Media? = null
    private var ready: File? = null
    private var choices = emptyList<Choice>()
    private val lime = Color.rgb(194, 255, 112)
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = getPreferences(0).getBoolean("english", false)
        engine = MediaEngine(applicationContext)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 20, 16)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        scroll.addView(root)
        setContentView(scroll)
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(24), dp(24) + insets.systemWindowInsetTop, dp(24), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
        val brand = label("flow.", 34).apply { setTextColor(lime) }
        language = button("RU / EN") { english = !english; getPreferences(0).edit().putBoolean("english", english).apply(); refresh() }
        root.removeView(brand); root.removeView(language)
        root.addView(LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
            addView(language, LinearLayout.LayoutParams(dp(90), dp(44)))
        })
        subtitle = label("", 18).apply { setTextColor(Color.rgb(170, 185, 169)) }
        mode = button("") { switchMode(false) }
        audioMode = button("") { switchMode(true) }
        root.removeView(mode); root.removeView(audioMode)
        root.addView(LinearLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = surface()
            addView(mode, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(audioMode, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(20) })
        input = EditText(this).apply {
            textSize = 18f; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = surface(); setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(58)
        }
        root.addView(input)
        analyze = button("") {
            val url = input.text.toString()
            job(text("Ищем варианты…", "Finding options…")) {
                val found = engine.analyze(url)
                runOnUiThread {
                    ready?.parentFile?.deleteRecursively(); ready = null
                    media = found; title.text = found.title; refreshChoices()
                }
            }
        }
        title = label("", 22).apply { setPadding(0, dp(26), 0, dp(12)) }
        qualityLabel = label("", 14).apply { setTextColor(Color.LTGRAY) }
        spinner = Spinner(this).apply { background = surface(); minimumHeight = dp(56); setPadding(dp(10), 0, dp(10), 0) }
        root.addView(spinner)
        download = button("") {
            val selected = choices.getOrNull(spinner.selectedItemPosition)
            val found = media
            if (selected != null && found != null) {
                ready?.parentFile?.deleteRecursively(); ready = null
                job(text("Готовим файл…", "Preparing file…")) {
                    val file = engine.download(found, selected) { value ->
                        runOnUiThread { progress.isIndeterminate = value < 0; progress.progress = value.toInt().coerceIn(0, 100) }
                    }
                    runOnUiThread { ready = file }
                }
            }
        }
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surface(); setPadding(dp(18), dp(6), dp(18), dp(18))
        }
        listOf(title, qualityLabel, spinner, download).forEach {
            root.removeView(it); resultCard.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        root.addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22); bottomMargin = dp(14) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        progress.progressTintList = ColorStateList.valueOf(lime)
        progress.indeterminateTintList = ColorStateList.valueOf(lime)
        root.addView(progress)
        status = label("", 17)
        save = button("") {
            val file = ready ?: return@button
            val mime = when (file.extension) { "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "mkv" -> "video/x-matroska"; "mp4" -> "video/mp4"; "webm" -> if (audio) "audio/webm" else "video/webm"; else -> "application/octet-stream" }
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
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                media = null; title.text = ""; lastError = ""
                ready?.parentFile?.deleteRecursively(); ready = null
                refreshChoices(); refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        refresh()
        job(text("Подготавливаем приложение…", "Setting things up…")) { engine.initialize() }
    }
    private fun switchMode(next: Boolean) {
        audio = next; ready?.parentFile?.deleteRecursively(); ready = null
        lastError = ""; refreshChoices(); refresh()
    }
    private fun surface(color: Int = Color.rgb(28, 36, 29)) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(49, 62, 49))
    }
    private fun style(button: Button, primary: Boolean) {
        button.background = surface(if (primary) lime else Color.rgb(28, 36, 29))
        button.setTextColor(if (primary) Color.rgb(20, 28, 16) else Color.rgb(220, 231, 216))
        button.alpha = if (button.isEnabled) 1f else .45f
    }
    private fun refresh() {
        language.text = if (english) "EN / RU" else "RU / EN"
        subtitle.text = text("Твои видео. Твоя музыка. С собой.", "Your videos. Your music. To go.")
        mode.text = text("Видео", "Video"); audioMode.text = text("Аудио", "Audio")
        qualityLabel.text = text("КАЧЕСТВО И ФОРМАТ", "QUALITY & FORMAT")
        input.hint = text("Вставьте ссылку YouTube", "Paste a YouTube link")
        analyze.text = text("Показать варианты", "Show options")
        download.text = text("Скачать на телефон", "Download to phone")
        save.text = text("Сохранить файл…", "Save file…")
        update.text = text("Обновить обработчик YouTube", "Update YouTube engine")
        listOf(mode, audioMode, input, analyze, update).forEach { it.isEnabled = !busy }
        spinner.isEnabled = !busy
        download.isEnabled = !busy && choices.isNotEmpty()
        save.visibility = if (ready != null && !busy) View.VISIBLE else View.GONE
        title.visibility = if (media != null) View.VISIBLE else View.GONE
        resultCard.visibility = title.visibility
        qualityLabel.visibility = title.visibility; spinner.visibility = title.visibility
        download.visibility = if (media != null && ready == null) View.VISIBLE else View.GONE
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        details.text = text("Подробности ошибки", "Error details")
        details.visibility = if (lastError.isNotEmpty()) View.VISIBLE else View.GONE
        style(language, false); style(mode, !audio); style(audioMode, audio)
        style(analyze, media == null); style(download, true); style(save, true); style(update, false); style(details, false)
        if (!busy) status.text = if (lastError.isNotEmpty()) friendlyError(lastError) else if (saved) text("Файл сохранён в выбранную папку.", "File saved to your chosen folder.") else if (ready != null) text("Готово! Сохраните файл в удобную папку.", "Ready! Save your file to a folder you choose.") else text("Скачивание прямо на телефон. Оставьте приложение открытым во время подготовки. Видео со звуком — MKV.", "Downloads directly to your phone. Keep the app open while preparing. Video with sound uses MKV.")
        status.setTextColor(if (lastError.isNotEmpty()) Color.rgb(255, 171, 151) else Color.rgb(175, 190, 174))
    }
    private fun friendlyError(error: String): String = when {
        error == "URL" -> text("Вставьте корректную ссылку YouTube.", "Paste a valid YouTube link.")
        error.contains("403") -> text("YouTube отклонил скачивание даже после обновления. Попробуйте другую ссылку или сеть.", "YouTube refused the download after the update. Try another link or network.")
        error == "LIVE" -> text("Дождитесь завершения трансляции.", "Wait for the livestream to finish.")
        error == "FORMAT_CHANGED" -> text("Набор форматов изменился. Нажмите «Показать варианты» заново.", "Formats have changed. Tap Show options again.")
        error.contains("update", true) -> text("Не удалось обновить обработчик. Проверьте интернет и повторите обновление.", "Could not update the engine. Check your connection and retry the update.")
        else -> text("Не удалось завершить операцию. Подробности доступны ниже.", "Could not finish. Error details are available below.")
    }
    private fun refreshChoices() {
        choices = media?.choices?.filter { it.audio == audio } ?: emptyList()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { it.label })
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
                }
            }
        }
    }
    @Deprecated("Used for the minimal prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val file = ready ?: return
        job(text("Сохраняем…", "Saving…")) {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot open destination")
            runOnUiThread { saved = true }
        }
    }
    private fun label(value: String, size: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.WHITE)
        setPadding(0, dp(12), 0, dp(12)); if (size >= 24) setTypeface(null, Typeface.BOLD)
        root.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 17f; isAllCaps = false; setTextColor(Color.rgb(20, 28, 16))
        background = GradientDrawable().apply { setColor(lime); cornerRadius = dp(16).toFloat() }
        root.addView(this, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12); bottomMargin = dp(8) })
        setOnClickListener { action() }
    }
    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
        Thread { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById("flow-download"); com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById("flow-analyze") }.start()
    }
}
