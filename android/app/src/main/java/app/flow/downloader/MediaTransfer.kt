package app.flow.downloader

import org.json.JSONArray
import org.json.JSONObject

/** Only public metadata and format IDs; never persist signed YouTube stream URLs. */
object MediaTransfer {
    fun choice(c: Choice) = JSONObject().apply {
        put("selector", c.selector); put("label", c.label); put("audio", c.audio); put("mp3", c.mp3)
        put("extractAudio", c.extractAudio); put("format", c.format); put("bitrate", c.bitrate)
        put("metadata", c.metadata); put("cover", c.cover); put("compatible", c.deviceCompatible)
        put("codec", c.codec); put("language", c.language); put("languageLabel", c.languageLabel)
        put("bytes", c.estimatedBytes); put("duration", c.duration)
    }
    fun choice(j: JSONObject) = Choice(j.getString("selector"), j.getString("label"), j.getBoolean("audio"),
        j.optBoolean("mp3"), j.optBoolean("extractAudio"), j.optString("format"), j.optInt("bitrate", 192),
        j.optBoolean("metadata"), j.optBoolean("cover"), j.optBoolean("compatible", true),
        j.optString("codec"), j.optString("language"), j.optString("languageLabel"), j.optLong("bytes"), j.optDouble("duration", 0.0))
    fun encode(m: Media) = JSONObject().apply {
        put("url", m.url); put("title", m.title); put("thumbnail", m.thumbnail)
        put("artist", m.artist); put("fileName", m.fileName)
        put("choices", JSONArray().apply { m.choices.forEach { put(choice(it)) } })
    }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= 500000) { "Too many download variants" } }
    fun decode(value: String): Media {
        val j = JSONObject(value)
        val rows = j.getJSONArray("choices")
        require(rows.length() in 1..5000)
        return Media(j.getString("url"), j.getString("title"), (0 until rows.length()).map { choice(rows.getJSONObject(it)) }, j.optString("thumbnail"), j.optString("artist"), j.optString("fileName"))
    }
}
