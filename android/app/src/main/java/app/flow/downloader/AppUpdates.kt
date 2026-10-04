package app.flow.downloader

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppUpdates {
    data class Release(val version: String, val url: String)
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").takeIf { Regex("\\d+\\.\\d+\\.\\d+").matches(it) }
            ?.split('.')?.map { it.toInt() } ?: error("Invalid version")
        val left = parts(candidate); val right = parts(installed)
        for (i in 0..2) if (left[i] != right[i]) return left[i] > right[i]
        return false
    }
    fun latest(): Release {
        val connection = URL("https://api.github.com/repos/yessensiv/flow-downloader/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Flow-Android")
            check(connection.responseCode == 200) { "GitHub ${connection.responseCode}" }
            val raw = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 262144)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            val json = JSONObject(raw)
            require(!json.optBoolean("prerelease") && !json.optBoolean("draft"))
            val version = json.getString("tag_name").removePrefix("v")
            isNewer(version, "0.0.0")
            val assets = json.getJSONArray("assets")
            require((0 until assets.length()).any { assets.getJSONObject(it).optString("name") == "Flow.apk" })
            return Release(version, "https://github.com/yessensiv/flow-downloader/releases/download/v$version/Flow.apk")
        } finally { connection.disconnect() }
    }
}
