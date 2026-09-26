package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        label("flow.", 40).setTextColor(lime)
        language = button("RU / EN") { english = !english; getPreferences(0).edit().putBoolean("english", english).apply(); refresh() }
        mode = button("") { audio = !audio; refreshChoices(); refresh() }
        input = EditText(this).apply {
            textSize = 18f; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
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
        title = label("", 24)
        spinner = Spinner(this)
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
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
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
        update = button("") { job(text("Обновляем обработчик…", "Updating engine…")) { engine.update() } }
        refresh()
        job(text("Первый запуск…", "Initializing…")) { engine.initialize() }
    }
    private fun refresh() {
        language.text = if (english) "EN · Русский" else "RU · English"
        mode.text = text(if (audio) "Аудио · переключить на видео" else "Видео · переключить на аудио", if (audio) "Audio · switch to video" else "Video · switch to audio")
        input.hint = text("Вставьте ссылку YouTube", "Paste a YouTube link")
        analyze.text = text("Показать варианты", "Show options")
        download.text = text("Скачать на телефон", "Download to phone")
        save.text = text("Сохранить файл…", "Save file…")
        update.text = text("Обновить обработчик YouTube", "Update YouTube engine")
        listOf(language, mode, input, analyze, update).forEach { it.isEnabled = !busy }
        spinner.isEnabled = !busy
        download.isEnabled = !busy && choices.isNotEmpty()
        save.visibility = if (ready != null && !busy) View.VISIBLE else View.GONE
        if (!busy) status.text = if (ready != null) text("Файл готов. Нажмите «Сохранить файл». После сохранения копия останется в выбранной папке.", "File ready. Tap Save file. Your saved copy stays in the folder you choose.") else text("Без сервера. Во время подготовки оставьте приложение открытым. Видео со звуком сохраняется в MKV, аудио — в исходном формате или MP3.", "No server needed. Keep the app open during preparation. Merged video uses MKV; audio uses its original format or MP3.")
    }
    private fun refreshChoices() {
        choices = media?.choices?.filter { it.audio == audio } ?: emptyList()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { it.label })
    }
    private fun job(message: String, block: () -> Unit) {
        if (busy) return
        busy = true; refresh(); status.text = message; progress.isIndeterminate = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker.execute {
            var problem: Exception? = null
            try { block() } catch (e: Exception) { problem = e }
            runOnUiThread {
                if (!isDestroyed) {
                    busy = false; progress.isIndeterminate = false; refresh()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    problem?.let { error ->
                        status.text = text("Не получилось. Проверьте ссылку и интернет или обновите обработчик.", "Could not complete the request. Check the link and connection, or update the engine.")
                        AlertDialog.Builder(this).setTitle(text("Подробности ошибки", "Error details"))
                            .setMessage(error.message?.take(2500)).setPositiveButton("OK", null).show()
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
