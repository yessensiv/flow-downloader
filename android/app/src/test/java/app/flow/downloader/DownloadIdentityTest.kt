package app.flow.downloader

import org.junit.Assert.*
import org.junit.Test

class DownloadIdentityTest {
    private val source = Media("https://www.youtube.com/watch?v=0FGtKwcxOrQ", "New title", emptyList())
    private val saved = SavedDownload("content://test/file", "Old title", "video/mp4", 123, 1000,
        sourceUrl = "https://youtu.be/0FGtKwcxOrQ?t=10")
    @Test fun renamedFileAndShortUrlStillMatch() {
        assertTrue(saved.matchesSource(source, false))
        assertTrue(saved.matchesSource(source.copy(url = "https://youtube.com/shorts/0FGtKwcxOrQ"), false))
        assertTrue(saved.matchesSource(source.copy(url = "https://m.youtube.com/watch?feature=share&v=0FGtKwcxOrQ"), false))
    }
    @Test fun differentVideoOrMediaTypeDoesNotMatch() {
        assertFalse(saved.matchesSource(source, true))
        assertFalse(saved.matchesSource(source.copy(url = "https://youtu.be/rKJuFfmG_Gk"), false))
        assertTrue(saved.copy(mime = "audio/mpeg").matchesSource(source, true))
    }
    @Test fun legacyThumbnailCanIdentifySource() {
        assertTrue(saved.copy(sourceUrl = "", thumbnail = "https://i.ytimg.com/vi/0FGtKwcxOrQ/hqdefault.jpg").matchesSource(source, false))
        assertFalse(saved.copy(sourceUrl = "", thumbnail = "https://example.com/vi/0FGtKwcxOrQ/hqdefault.jpg").matchesSource(source, false))
        assertFalse(saved.copy(sourceUrl = "").matchesSource(source, false))
    }
    @Test fun malformedSourcesNeverMatch() {
        for (value in listOf("", "broken", "https://youtube.com/watch?v=bad", "https://example.com/watch?v=0FGtKwcxOrQ")) {
            assertNull(DownloadIdentity.sourceId(value))
            assertFalse(saved.matchesSource(source.copy(url = value), false))
        }
    }
}
