package app.flow.downloader

import android.app.*
import android.content.Intent
import android.os.*
import java.io.File
import java.util.concurrent.Executors

/** Owns the download independently of the screen. One explicit user job at a time. */
class DownloadService : Service() {
    data class State(val id: Long, val media: Media, val choice: Choice,
        val running: Boolean = true, val progress: Int = -1, val file: File? = null, val error: String = "")
    companion object {
        @Volatile var state: State? = null
            private set
        const val CANCEL = "app.flow.downloader.CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION = 10
        fun forgetCompleted() { if (state?.running != true) state = null }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var english = false
    private var finished = false
    private val deadline = Runnable { finish(error = word("Превышено время загрузки. Повторите попытку.", "Download timed out. Please retry.")) }
    private fun word(ru: String, en: String) = if (english) en else ru
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            finish(error = word("Загрузка отменена.", "Download cancelled."))
            return START_NOT_STICKY
        }
        if (state?.running == true) return START_NOT_STICKY
        val url = intent?.getStringExtra("url") ?: run { stopSelf(); return START_NOT_STICKY }
        english = intent.getBooleanExtra("english", false)
        val choice = Choice(intent.getStringExtra("selector") ?: "", intent.getStringExtra("label") ?: "",
            intent.getBooleanExtra("audio", false), intent.getBooleanExtra("mp3", false))
        val media = Media(url, intent.getStringExtra("title") ?: "YouTube", listOf(choice), intent.getStringExtra("thumbnail") ?: "")
        state = State(System.nanoTime(), media, choice)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, word("Загрузки", "Downloads"), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:download").apply { acquire(3_600_000L) }
        main.postDelayed(deadline, 3_600_000L)
        worker.execute {
            try {
                val file = MediaEngine(applicationContext).download(media, choice) { value ->
                    main.post {
                        if (!finished) {
                            val percent = if (value < 0) -1 else value.toInt().coerceIn(0, 100)
                            if (state?.progress != percent) {
                                state = state?.copy(progress = percent)
                                notifySafely()
                            }
                        }
                    }
                }
                main.post { if (finished) file.parentFile?.deleteRecursively() else finish(file = file) }
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
            builder.setContentText(if (current.progress < 0) word("Готовим файл…", "Preparing file…") else "${current.progress}%")
                .setProgress(100, current.progress.coerceAtLeast(0), current.progress < 0)
                .addAction(Notification.Action.Builder(null, word("Отмена", "Cancel"),
                    PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)).build())
        } else builder.setContentText(if (current?.file != null) word("Готово — нажмите, чтобы сохранить", "Ready — tap to save") else word("Загрузка остановлена. Откройте Flow.", "Download stopped. Open Flow."))
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
