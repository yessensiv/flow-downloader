package app.flow.downloader

import android.app.*
import android.content.Intent
import android.os.*
import java.io.File
import java.util.concurrent.Executors

/** Owns the download independently of the screen. One explicit user job at a time. */
class DownloadService : Service() {
    data class State(val id: Long, val media: Media, val choice: Choice,
        val running: Boolean = true, val progress: Int = -1, val etaSeconds: Long = -1,
        val speed: String = "", val stage: String = "", val file: File? = null, val error: String = "", val savedUri: String = "", val saving: Boolean = false,
        val paused: Boolean = false, val pausing: Boolean = false, val processing: Boolean = false)
    companion object {
        @Volatile var state: State? = null
            private set
        const val CANCEL = "app.flow.downloader.CANCEL"
        const val CANCELLED = "CANCELLED"
        const val PAUSE = "app.flow.downloader.PAUSE"
        const val RESUME = "app.flow.downloader.RESUME"
        const val RETRY = "app.flow.downloader.RETRY"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION = 10
        private const val JOURNAL = "download_session"
        fun restore(context: android.content.Context) {
            if (state != null) return
            val preferences = context.getSharedPreferences(JOURNAL, 0)
            val raw = preferences.getString("session", null) ?: return
            runCatching {
                val json = org.json.JSONObject(raw)
                val id = json.getLong("id")
                require(File(context.filesDir, "transfer-$id").isDirectory)
                state = State(id, MediaTransfer.decode(json.getString("media")), MediaTransfer.choice(json.getJSONObject("choice")),
                    progress = json.optInt("progress", -1), paused = true, stage = "")
            }.onFailure { preferences.edit().remove("session").apply() }
        }
        fun forgetCompleted() { if (state?.running != true) state = null }
        fun markSaved(file: File, uri: String) {
            if (state?.file == file && state?.running == false) state = state?.copy(savedUri = uri, error = "")
        }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var english = false
    @Volatile private var finished = false
    @Volatile private var saving = false
    @Volatile private var pauseRequested = false
    @Volatile private var preserveSession = false
    private lateinit var sessionDir: File
    private var processId = ""
    private var jobId = 0L
    private val killWorker = Executors.newSingleThreadExecutor()
    private val stopProcess = object : Runnable {
        override fun run() {
            if (state?.pausing != true && !finished) return
            val target = processId
            killWorker.execute { runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(target) } }
            if (state?.pausing == true) main.postDelayed(this, 250)
        }
    }
    private val deadline = Runnable { finish(error = word("Превышено время загрузки. Повторите попытку.", "Download timed out. Please retry.")) }
    private fun word(ru: String, en: String) = if (english) en else ru
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action in listOf(RESUME, PAUSE, CANCEL)) {
            restore(this)
            if (!::sessionDir.isInitialized && state?.paused == true) {
                jobId = state!!.id
                sessionDir = File(filesDir, "transfer-$jobId")
                english = getSharedPreferences(JOURNAL, 0).getBoolean("english", false)
                prepareForeground()
            }
        }
        if (intent?.action == CANCEL) {
            if (saving) return START_NOT_STICKY
            if (::sessionDir.isInitialized) sessionDir.deleteRecursively()
            finish(error = CANCELLED)
            return START_NOT_STICKY
        }
        if (intent?.action == PAUSE) {
            val current = state
            if (current?.running == true && !current.saving && !current.processing && !current.pausing && !current.paused) {
                pauseRequested = true
                state = current.copy(pausing = true, stage = word("Ставим на паузу…", "Pausing…"))
                main.post(stopProcess); notifySafely()
            }
            return START_NOT_STICKY
        }
        if (intent?.action == RESUME) {
            if (state?.paused == true) {
                pauseRequested = false
                state = state?.copy(paused = false, pausing = false, speed = "", etaSeconds = -1, error = "", stage = word("Продолжаем…", "Resuming…"))
                begin()
            }
            return START_NOT_STICKY
        }
        if (state?.running == true) return START_NOT_STICKY
        val previous = state?.takeIf { intent?.action == RETRY && it.savedUri.isEmpty() && it.file == null }
        val url = intent?.getStringExtra("url") ?: previous?.media?.url ?: run { stopSelf(); return START_NOT_STICKY }
        english = intent?.getBooleanExtra("english", false) ?: false
        if (previous != null) {
            state = State(System.nanoTime(), previous.media, previous.choice)
        } else {
        requireNotNull(intent)
        val choice = Choice(intent.getStringExtra("selector") ?: "", intent.getStringExtra("label") ?: "",
            intent.getBooleanExtra("audio", false), intent.getBooleanExtra("mp3", false),
            intent.getBooleanExtra("extractAudio", false), intent.getStringExtra("format").orEmpty(),
            intent.getIntExtra("bitrate", 192), intent.getBooleanExtra("metadata", false), intent.getBooleanExtra("cover", false))
        val selected = intent.getStringExtra("choiceJson")?.let { MediaTransfer.choice(org.json.JSONObject(it)) } ?: choice
        val media = intent.getStringExtra("mediaJson")?.let { MediaTransfer.decode(it) }
            ?: Media(url, intent.getStringExtra("title") ?: "YouTube", listOf(choice), intent.getStringExtra("thumbnail") ?: "")
        state = State(System.nanoTime(), media, selected)
        }
        finished = false; saving = false; pauseRequested = false; preserveSession = false
        sessionDir = File(filesDir, "transfer-${state!!.id}").apply { mkdirs() }
        jobId = state!!.id
        persistSession()
        prepareForeground()
        begin()
        return START_NOT_STICKY
    }
    private fun persistSession() {
        val current = state ?: return
        getSharedPreferences(JOURNAL, 0).edit().putBoolean("english", english).putString("session", org.json.JSONObject().apply {
            put("id", current.id); put("media", MediaTransfer.encode(current.media)); put("choice", MediaTransfer.choice(current.choice))
            put("progress", current.progress)
        }.toString()).apply()
    }
    private fun prepareForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, word("Загрузки", "Downloads"), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification())
    }
    private fun begin() {
        val current = state ?: return
        val media = current.media
        val choice = current.choice
        val dir = sessionDir
        processId = "flow-download-${current.id}-${System.nanoTime()}"
        val attempt = processId
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:download").apply { acquire(3_600_000L) }
        main.postDelayed(deadline, 3_600_000L)
        worker.execute {
            try {
                val file = MediaEngine(applicationContext).downloadResumable(media, choice, dir, attempt, { pauseRequested || finished }) { value, etaSeconds, line ->
                    main.post {
                        if (!finished && !pauseRequested && state?.id == current.id && processId == attempt) {
                            val percent = if (value < 0) -1 else value.toInt().coerceIn(0, 100)
                            val speed = Regex("\\bat\\s+([0-9.]+\\s*[KMG]?i?B/s)").find(line)?.groupValues?.get(1).orEmpty()
                            val stage = when {
                                line.contains("[Merger]", true) -> word("Объединяем видео и звук", "Combining video and audio")
                                line.contains("[ExtractAudio]", true) -> word("Готовим аудио", "Preparing audio")
                                percent >= 0 -> word("Загружаем видео и звук", "Downloading video and audio")
                                else -> word("Подключаемся к YouTube", "Connecting to YouTube")
                            }
                            state = state?.copy(progress = percent, etaSeconds = etaSeconds,
                                speed = speed.ifBlank { state?.speed.orEmpty() }, stage = stage,
                                processing = line.contains("[Merger]") || line.contains("[ExtractAudio]") || line.contains("[VideoRemuxer]") || percent >= 100)
                            notifySafely()
                        }
                    }
                }
                if (finished) { file.parentFile?.deleteRecursively(); return@execute }
                if (pauseRequested) { main.post { paused() }; return@execute }
                var savedUri = ""
                if (Build.VERSION.SDK_INT >= 29) {
                    saving = true
                    main.post {
                        main.removeCallbacks(deadline)
                        if (state?.id == current.id) state = state?.copy(saving = true, progress = -1, speed = "", etaSeconds = -1, stage = word("Сохраняем на устройство", "Saving to device"))
                        notifySafely()
                    }
                    try { savedUri = MediaStorage.save(applicationContext, file, media, choice.audio) }
                    catch (e: Exception) {
                        main.post { finish(file = file, error = e.message ?: "Save failed") }
                        return@execute
                    }
                }
                val destination = savedUri
                if (destination.isNotEmpty()) dir.deleteRecursively()
                main.post { if (state?.id == current.id) { state = state?.copy(savedUri = destination); finish(file = file) } }
            } catch (e: Exception) {
                main.post {
                    if (finished || state?.id != current.id) { if (!preserveSession) dir.deleteRecursively() }
                    else if (pauseRequested) paused()
                    else { dir.deleteRecursively(); finish(error = e.message ?: "Download failed") }
                }
            }
        }
    }
    private fun paused() {
        main.removeCallbacks(stopProcess); main.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        // If pause raced with the first postprocessing callback, discard only a
        // possibly unfinished conversion/merge output, not downloaded source parts.
        val selected = state?.choice
        if (selected != null && selected.format.isNotBlank() && (selected.audio || selected.selector.contains('+')))
            File(sessionDir, "flow.${selected.format}").takeIf { it.isFile }?.delete()
        state = state?.copy(paused = true, pausing = false, processing = false, speed = "", etaSeconds = -1, stage = word("На паузе", "Paused"))
        persistSession()
        notifySafely()
    }
    private fun notification(): Notification {
        val current = state
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(current?.media?.title ?: "Flow")
            .setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(current?.running == true).setAutoCancel(current?.running != true)
        if (current?.running == true) {
            val details = buildList {
                if (current.progress >= 0) add("${current.progress}%")
                if (current.speed.isNotBlank()) add(current.speed)
                if (current.etaSeconds >= 0 && current.progress in 0..99)
                    add(word("~${current.etaSeconds} сек", "~${current.etaSeconds}s left"))
            }.joinToString(" · ")
            builder.setContentText(if (current.paused || current.pausing) current.stage else details.ifBlank { current.stage.ifBlank { word("Готовим файл…", "Preparing file…") } })
                .setProgress(100, current.progress.coerceAtLeast(0), current.progress < 0)
            if (!current.saving) builder.addAction(Notification.Action.Builder(null, word("Отмена", "Cancel"),
                    PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)).build())
            if (!current.saving && !current.processing && !current.pausing) builder.addAction(Notification.Action.Builder(null,
                if (current.paused) word("Продолжить", "Resume") else word("Пауза", "Pause"),
                PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).setAction(if (current.paused) RESUME else PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        } else builder.setContentText(if (!current?.savedUri.isNullOrEmpty()) word("Сохранено в папку Flow", "Saved to Flow folder") else if (current?.file != null) word("Готово — нажмите, чтобы сохранить", "Ready — tap to save") else word("Загрузка остановлена. Откройте Flow.", "Download stopped. Open Flow."))
        return builder.build()
    }
    private fun notifySafely() {
        // Denying notification permission must not crash or stop a user-started service.
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }
    private fun finish(file: File? = null, error: String = "") {
        if (finished) return
        finished = true
        getSharedPreferences(JOURNAL, 0).edit().remove("session").apply()
        main.removeCallbacks(stopProcess)
        if (state?.id == jobId) state = state?.copy(running = false, file = file, error = error)
        main.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifySafely()
        stopSelf()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { finish(error = word("Android остановил долгую загрузку.", "Android stopped a long-running download.")) }
    override fun onDestroy() {
        if (!finished) {
            preserveSession = !saving
            if (state?.id == jobId) state = state?.copy(paused = true, pausing = false, stage = word("На паузе", "Paused"))
            finished = true
        }
        main.removeCallbacks(deadline)
        main.removeCallbacks(stopProcess)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        worker.shutdownNow()
        val target = processId
        killWorker.execute { runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(target) } }
        killWorker.shutdown()
        super.onDestroy()
    }
}
