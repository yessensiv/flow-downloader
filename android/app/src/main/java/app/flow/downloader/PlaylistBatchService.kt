package app.flow.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Processes a user-selected playlist in order so the device only needs memory for one item. */
class PlaylistBatchService : Service() {
    data class State(
        val running: Boolean,
        val audio: Boolean,
        val mp3: Boolean,
        val english: Boolean,
        val total: Int,
        val index: Int = 0,
        val completed: Int = 0,
        val failed: Int = 0,
        val currentTitle: String = "",
        val progress: Int = -1,
        val etaSeconds: Long = -1,
        val speed: String = "",
        val stage: String = "",
        val cancelled: Boolean = false,
        val error: String = ""
    )

    companion object {
        @Volatile var state: State? = null
            private set
        const val CANCEL = "app.flow.downloader.PLAYLIST_CANCEL"
        private const val CHANNEL = "playlist-downloads"
        private const val NOTIFICATION = 11
        private const val MAX_ITEMS = 10
        fun forgetCompleted() { if (state?.running != true) state = null }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var cancelled = false
    private var finished = false
    private var english = false
    private fun word(ru: String, en: String) = if (english) en else ru

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            cancelled = true
            Thread {
                runCatching { YoutubeDL.getInstance().destroyProcessById("flow-playlist-analyze") }
                runCatching { YoutubeDL.getInstance().destroyProcessById("flow-download") }
            }.start()
            return START_NOT_STICKY
        }
        if (state?.running == true) return START_NOT_STICKY
        if (Build.VERSION.SDK_INT < 29) {
            stopSelf()
            return START_NOT_STICKY
        }
        val entries = runCatching { parseEntries(intent?.getStringExtra("entries").orEmpty()) }.getOrDefault(emptyList())
            .take(MAX_ITEMS)
        if (entries.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        cancelled = false
        english = intent?.getBooleanExtra("english", false) ?: false
        val audio = intent?.getBooleanExtra("audio", false) ?: false
        val mp3 = intent?.getBooleanExtra("mp3", false) ?: false
        state = State(true, audio, mp3, english, entries.size, stage = word("Готовим очередь…", "Preparing the queue…"))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, word("Загрузки плейлиста", "Playlist downloads"), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:playlist").apply {
            acquire(3 * 60 * 60 * 1000L)
        }
        worker.execute { process(entries, audio, mp3) }
        return START_NOT_STICKY
    }

    private fun parseEntries(serialized: String): List<PlaylistEntry> {
        val array = JSONArray(serialized)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = runCatching { MediaEngine(applicationContext).canonicalUrl(item.optString("url")) }.getOrNull()
                ?: return@mapNotNull null
            PlaylistEntry(url, item.optString("title").ifBlank { "YouTube" }, item.optString("channel"),
                item.optString("duration"), item.optString("thumbnail"))
        }.distinctBy { it.url }
    }

    private fun process(entries: List<PlaylistEntry>, audio: Boolean, mp3: Boolean) {
        var complete = 0
        var failed = 0
        var errorSummary = ""
        entries.forEachIndexed { itemIndex, entry ->
            if (cancelled) return@forEachIndexed
            updateState { it.copy(index = itemIndex + 1, currentTitle = entry.title, progress = -1, etaSeconds = -1,
                speed = "", stage = word("Проверяем формат", "Checking formats")) }
            var file: File? = null
            try {
                val engine = MediaEngine(applicationContext)
                val media = engine.analyze(entry.url, "flow-playlist-analyze")
                if (cancelled) return@forEachIndexed
                val choice = choose(media.choices, audio, mp3)
                file = engine.download(media, choice) { value, eta, line ->
                    if (!cancelled) {
                        val percent = if (value < 0) -1 else value.toInt().coerceIn(0, 100)
                        val speed = Regex("\\bat\\s+([0-9.]+\\s*[KMG]?i?B/s)").find(line)?.groupValues?.get(1).orEmpty()
                        val stage = when {
                            line.contains("[Merger]", true) -> word("Объединяем потоки", "Combining streams")
                            line.contains("[ExtractAudio]", true) -> word("Готовим аудио", "Preparing audio")
                            percent >= 0 -> word("Загружаем файл", "Downloading file")
                            else -> word("Подключаемся к YouTube", "Connecting to YouTube")
                        }
                        updateState { it.copy(progress = percent, etaSeconds = eta, speed = speed.ifBlank { it.speed }, stage = stage) }
                    }
                }
                if (cancelled) {
                    file?.parentFile?.deleteRecursively()
                    return@forEachIndexed
                }
                val mime = MediaStoreSaver.mimeFor(file, audio)
                MediaStoreSaver.save(applicationContext, file, media.title, mime, media.thumbnail.ifBlank { entry.thumbnail })
                file.parentFile?.deleteRecursively()
                file = null
                complete++
                updateState { it.copy(completed = complete, progress = 100, stage = word("Сохранено", "Saved")) }
            } catch (e: Exception) {
                file?.parentFile?.deleteRecursively()
                if (cancelled) return@forEachIndexed
                failed++
                if (errorSummary.isBlank()) errorSummary = e.message.orEmpty()
                updateState { it.copy(failed = failed, progress = -1, stage = word("Не удалось скачать этот пункт", "Could not download this item")) }
            }
        }
        main.post {
            if (!finished) finish(complete, failed, cancelled, errorSummary)
        }
    }

    private fun choose(choices: List<Choice>, audio: Boolean, mp3: Boolean): Choice {
        val matching = choices.filter { it.audio == audio }
        if (audio) {
            val source = matching.firstOrNull { !it.mp3 } ?: error("AUDIO_UNAVAILABLE")
            return source.copy(label = if (mp3) "MP3 · 192 kbps" else "M4A", mp3 = mp3, extractAudio = true)
        }
        return matching.maxWithOrNull(compareBy<Choice> {
            Regex("(\\d+)p").find(it.label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }.thenBy {
            Regex("(\\d+)\\s*fps").find(it.label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }) ?: error("VIDEO_UNAVAILABLE")
    }

    private fun updateState(change: (State) -> State) {
        main.post {
            if (!finished) {
                state = state?.let(change)
                notifySafely()
            }
        }
    }

    private fun notification(): Notification {
        val current = state
        val open = PendingIntent.getActivity(this, 0, Intent(this, DownloadsActivity::class.java)
            .putExtra("english", english), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(word("Загрузка плейлиста", "Playlist download"))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(current?.running == true)
        if (current?.running == true) {
            val detail = buildList {
                add("${current.index.coerceAtLeast(1)}/${current.total} · ${current.currentTitle.take(45)}")
                if (current.progress >= 0) add("${current.progress}%")
                if (current.speed.isNotBlank()) add(current.speed)
            }.joinToString(" · ")
            builder.setContentText(detail).setProgress(100, current.progress.coerceAtLeast(0), current.progress < 0)
                .addAction(Notification.Action.Builder(null, word("Отмена", "Cancel"),
                    PendingIntent.getService(this, 2, Intent(this, PlaylistBatchService::class.java).setAction(CANCEL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        } else {
            builder.setContentText(word("Готово: ${current?.completed ?: 0} · ошибки: ${current?.failed ?: 0}",
                "Done: ${current?.completed ?: 0} · errors: ${current?.failed ?: 0}"))
                .setAutoCancel(true)
        }
        return builder.build()
    }

    private fun notifySafely() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun finish(completed: Int, failed: Int, wasCancelled: Boolean, error: String) {
        if (finished) return
        finished = true
        state = state?.copy(running = false, completed = completed, failed = failed, cancelled = wasCancelled, error = error,
            stage = if (wasCancelled) word("Загрузка отменена", "Download cancelled") else word("Готово", "Complete"))
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifySafely()
        stopSelf()
    }

    override fun onDestroy() {
        if (!finished) {
            cancelled = true
            state = state?.copy(running = false, cancelled = true, stage = word("Загрузка прервана", "Download interrupted"))
            Thread {
                runCatching { YoutubeDL.getInstance().destroyProcessById("flow-playlist-analyze") }
                runCatching { YoutubeDL.getInstance().destroyProcessById("flow-download") }
            }.start()
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        worker.shutdownNow()
        super.onDestroy()
    }
}
