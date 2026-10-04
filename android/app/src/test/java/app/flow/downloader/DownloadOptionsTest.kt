package app.flow.downloader

import org.junit.Assert.*
import org.junit.Test

class DownloadOptionsTest {
    @Test fun versionsCompareNumerically() {
        assertTrue(AppUpdates.isNewer("0.21.0", "0.20.1"))
        assertTrue(AppUpdates.isNewer("v1.0.0", "0.99.99"))
        assertTrue(AppUpdates.isNewer("0.20.10", "0.20.9"))
        assertFalse(AppUpdates.isNewer("0.20.1", "0.20.1"))
        assertFalse(AppUpdates.isNewer("0.19.9", "0.20.1"))
        assertThrows(IllegalStateException::class.java) { AppUpdates.isNewer("0.21.0-beta", "0.20.1") }
    }
    @Test fun completeSelectionSurvivesTransfer() {
        val choice = Choice("137+140-ru", "1080p · 30 fps", false, format = "mp4", bitrate = 256,
            metadata = true, cover = true, deviceCompatible = false, codec = "H.264", language = "ru",
            languageLabel = "ru · original", estimatedBytes = 5000000, duration = 60.0)
        val media = Media("https://www.youtube.com/watch?v=0FGtKwcxOrQ", "Тест 🎵", listOf(choice, choice.copy(codec = "AV1", selector = "399+140-ru")), "https://i.ytimg.com/test.jpg")
        assertEquals(media, MediaTransfer.decode(MediaTransfer.encode(media)))
        assertEquals(choice, MediaTransfer.choice(MediaTransfer.choice(choice)))
    }
    @Test fun invalidSessionDoesNotBecomeDownload() {
        assertThrows(org.json.JSONException::class.java) { MediaTransfer.decode("broken") }
        assertThrows(IllegalArgumentException::class.java) { MediaTransfer.decode("{\"choices\":[]}") }
    }
    @Test fun autoUsesCompatibleAlternativeAtSameQuality() {
        val av1 = Choice("399+140", "1080p · 30 fps", false, codec = "AV1", language = "en", deviceCompatible = false)
        val avc = av1.copy(selector = "137+140", codec = "H.264", deviceCompatible = true)
        val list = DownloadOptions.select(listOf(av1, avc).filter { it.deviceCompatible }, false, "Auto", "en")
        assertEquals(listOf(avc), list)
    }
    @Test fun autoDoesNotPreferUnsupportedH264OverSupportedAv1() {
        val unsupported = Choice("137+140", "1080p · 30 fps", false, codec = "H.264", language = "en", deviceCompatible = false)
        val supported = unsupported.copy(selector = "399+140", codec = "AV1", deviceCompatible = true)
        assertEquals(listOf(supported), DownloadOptions.select(listOf(unsupported, supported), false, "Auto", "en"))
    }
    @Test fun codecAndLanguageNeverFallBackSilently() {
        val english = Choice("137+140-en", "1080p · 30 fps", false, codec = "H.264", language = "en")
        val russian = english.copy(selector = "137+140-ru", language = "ru")
        val av1 = russian.copy(selector = "399+140-ru", codec = "AV1")
        assertEquals(listOf(av1), DownloadOptions.select(listOf(english, russian, av1), false, "AV1", "ru"))
        assertTrue(DownloadOptions.select(listOf(english, russian), false, "VP9", "ru").isEmpty())
    }
    @Test fun estimatesConvertedAudioAndPreservesUnknownSize() {
        val mp3 = Choice("140", "MP3", true, format = "mp3", duration = 60.0)
        assertEquals(1440000L, DownloadOptions.estimatedSize(mp3, 192))
        assertEquals(2400000L, DownloadOptions.estimatedSize(mp3, 320))
        assertEquals(0L, DownloadOptions.estimatedSize(mp3.copy(format = "flac"), 192))
        assertEquals(9000000L, DownloadOptions.estimatedSize(mp3.copy(format = "", estimatedBytes = 9000000), 192))
    }
    @Test fun onlyRealQualitiesAreOfferedInDescendingOrder() {
        val fullHd = Choice("137", "1080p · 30 fps", false, codec = "H.264", language = "")
        val hd = fullHd.copy(selector = "136", label = "720p · 30 fps")
        val choices = DownloadOptions.select(listOf(hd, fullHd), false, "Auto", "")
        assertEquals(listOf(fullHd, hd), choices)
        assertFalse(choices.any { it.label.startsWith("1440") || it.label.startsWith("2160") })
    }
}
