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
        val speed: String = "", val stage: String = "", val file: File? = null, val error: String = "", val savedUri: String = "", val saving: Boolean = false)
    companion object {
        @Volatile var state: State? = null
            private set
        const val CANCEL = "app.flow.downloader.CANCEL"
        const val CANCELLED = "CANCELLED"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION = 10
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
    private val deadline = Runnable { finish(error = word("Превышено время загрузки. Повторите попытку.", "Download timed out. Please retry.")) }
    private fun word(ru: String, en: String) = if (english) en else ru
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            if (saving) return START_NOT_STICKY
            finish(error = CANCELLED)
            return START_NOT_STICKY
        }
        if (state?.running == true) return START_NOT_STICKY
        val url = intent?.getStringExtra("url") ?: run { stopSelf(); return START_NOT_STICKY }
        english = intent.getBooleanExtra("english", false)
        val choice = Choice(intent.getStringExtra("selector") ?: "", intent.getStringExtra("label") ?: "",
            intent.getBooleanExtra("audio", false), intent.getBooleanExtra("mp3", false),
            intent.getBooleanExtra("extractAudio", false), intent.getStringExtra("format").orEmpty(),
            intent.getIntExtra("bitrate", 192), intent.getBooleanExtra("metadata", false), intent.getBooleanExtra("cover", false))
        val media = Media(url, intent.getStringExtra("title") ?: "YouTube", listOf(choice), intent.getStringExtra("thumbnail") ?: "")
        state = State(System.nanoTime(), media, choice)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, word("Загрузки", "Downloads"), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:download").apply { acquire(3_600_000L) }
        main.postDelayed(deadline, 3_600_000L)
        worker.execute {
            try {
                val file = MediaEngine(applicationContext).download(media, choice) { value, etaSeconds, line ->
                    main.post {
                        if (!finished) {
                            val percent = if (value < 0) -1 else value.toInt().coerceIn(0, 100)
                            val speed = Regex("\\bat\\s+([0-9.]+\\s*[KMG]?i?B/s)").find(line)?.groupValues?.get(1).orEmpty()
                            val stage = when {
                                line.contains("[Merger]", true) -> word("Объединяем видео и звук", "Combining video and audio")
                                line.contains("[ExtractAudio]", true) -> word("Готовим аудио", "Preparing audio")
                                percent >= 0 -> word("Загружаем видео и звук", "Downloading video and audio")
                                else -> word("Подключаемся к YouTube", "Connecting to YouTube")
                            }
                            state = state?.copy(progress = percent, etaSeconds = etaSeconds,
                                speed = speed.ifBlank { state?.speed.orEmpty() }, stage = stage)
                            notifySafely()
                        }
                    }
                }
                if (finished) { file.parentFile?.deleteRecursively(); return@execute }
                var savedUri = ""
                if (Build.VERSION.SDK_INT >= 29) {
                    saving = true
                    main.post {
                        main.removeCallbacks(deadline)
                        state = state?.copy(saving = true, progress = -1, speed = "", etaSeconds = -1, stage = word("Сохраняем на устройство", "Saving to device"))
                        notifySafely()
                    }
                    try { savedUri = MediaStorage.save(applicationContext, file, media, choice.audio) }
                    catch (e: Exception) {
                        main.post { finish(file = file, error = e.message ?: "Save failed") }
                        return@execute
                    }
                }
                val destination = savedUri
                main.post { state = state?.copy(savedUri = destination); finish(file = file) }
            } catch (e: Exception) {
                main.post { finish(error = e.message ?: "Download failed") }
            }
        }
        return START_NOT_STICKY
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
            builder.setContentText(details.ifBlank { current.stage.ifBlank { word("Готовим файл…", "Preparing file…") } })
                .setProgress(100, current.progress.coerceAtLeast(0), current.progress < 0)
            if (!current.saving) builder.addAction(Notification.Action.Builder(null, word("Отмена", "Cancel"),
                    PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)).build())
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
        state = state?.copy(running = false, file = file, error = error)
        main.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifySafely()
        stopSelf()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { finish(error = word("Android остановил долгую загрузку.", "Android stopped a long-running download.")) }
    override fun onDestroy() {
        if (!finished) {
            state = state?.copy(running = false, error = word("Загрузка прервана.", "Download interrupted."))
            finished = true
        }
        main.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        worker.shutdownNow()
        Thread { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById("flow-download") }.start()
        super.onDestroy()
    }
}
