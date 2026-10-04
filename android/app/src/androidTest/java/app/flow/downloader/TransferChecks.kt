package app.flow.downloader

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Exercises the real foreground service against a throttled loopback stream. */
object TransferChecks {
    fun run(context: Context): String {
        check(DownloadService.state?.running != true) { "A user download is already active" }
        val running = AtomicBoolean(true)
        val resumeOffset = AtomicLong(0)
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 250 }
        val contentLength = 100L * 1024 * 1024
        val serverThread = Thread {
            val chunk = ByteArray(8192) { 0x35 }
            while (running.get()) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val request = reader.readLine() ?: return@use
                        var range = 0L
                        while (true) {
                            val line = reader.readLine().orEmpty()
                            if (line.isEmpty()) break
                            Regex("(?i)^Range: bytes=(\\d+)-").find(line)?.let { range = it.groupValues[1].toLong() }
                        }
                        if (range > 0) resumeOffset.set(range)
                        val response = buildString {
                            append(if (range > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                            if (range > 0) append("Content-Range: bytes $range-${contentLength - 1}/$contentLength\r\n")
                            append("Content-Type: audio/mp4\r\nContent-Length: ${contentLength - range}\r\n")
                            append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n")
                        }
                        val output = socket.getOutputStream()
                        output.write(response.toByteArray(Charsets.US_ASCII))
                        if (request.startsWith("HEAD ")) return@use
                        var cursor = range
                        while (cursor < contentLength && running.get()) {
                            val count = minOf(chunk.size.toLong(), contentLength - cursor).toInt()
                            output.write(chunk, 0, count)
                            cursor += count
                            Thread.sleep(8)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Check whether the test has completed.
                } catch (_: Exception) {
                    // Pausing a download closes the active socket.
                }
            }
        }.apply { isDaemon = true; start() }
        val enginePreferences = context.getSharedPreferences("engine", 0)
        val originalCheck = enginePreferences.getLong("checked", 0L)
        val hadCheck = enginePreferences.contains("checked")
        enginePreferences.edit().putLong("checked", System.currentTimeMillis()).commit()
        var testFolder: File? = null
        fun waitFor(label: String, seconds: Int, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                Thread.sleep(100)
            }
            error("Timed out waiting for $label; state=${DownloadService.state}")
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/sample.m4a"
            val choice = Choice("best", "M4A · original", true)
            val media = Media(url, "Flow local transfer check", listOf(choice))
            context.startForegroundService(Intent(context, DownloadService::class.java).apply {
                putExtra("url", url); putExtra("mediaJson", MediaTransfer.encode(media))
                putExtra("choiceJson", MediaTransfer.choice(choice).toString())
            })
            waitFor("service start", 15) { DownloadService.state?.running == true }
            val id = DownloadService.state!!.id
            val folder = File(context.filesDir, "transfer-$id")
            testFolder = folder
            fun partSize(): Long = folder.listFiles()?.filter { it.name.endsWith(".part") }?.sumOf { it.length() } ?: 0
            waitFor("partial file", 35) { partSize() >= 32768L || DownloadService.state?.error?.isNotEmpty() == true }
            check(partSize() >= 32768L) { "Download failed before pause: ${DownloadService.state?.error}" }
            context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.PAUSE))
            waitFor("paused state", 20) { DownloadService.state?.paused == true || DownloadService.state?.error?.isNotEmpty() == true }
            check(DownloadService.state?.paused == true) { "Pause failed: ${DownloadService.state?.error}" }
            val savedBytes = partSize()
            check(savedBytes >= 32768L)
            context.startForegroundService(Intent(context, DownloadService::class.java).setAction(DownloadService.RESUME))
            waitFor("HTTP Range resume", 35) { resumeOffset.get() > 0 || DownloadService.state?.error?.isNotEmpty() == true }
            check(resumeOffset.get() > 0) { "Resume failed: ${DownloadService.state?.error}" }
            waitFor("additional bytes", 20) { partSize() > savedBytes || DownloadService.state?.error?.isNotEmpty() == true }
            check(partSize() > savedBytes) { "No progress after resume: ${DownloadService.state?.error}" }
            return "PASS: paused at $savedBytes bytes; resumed with HTTP Range ${resumeOffset.get()}; continued to ${partSize()} bytes"
        } finally {
            context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.CANCEL))
            if (testFolder != null) {
                waitFor("cancel cleanup", 10) { testFolder?.exists() == false }
            }
            running.set(false)
            server.close()
            serverThread.join(2000)
            enginePreferences.edit().let { if (hadCheck) it.putLong("checked", originalCheck) else it.remove("checked") }.commit()
        }
    }
}
