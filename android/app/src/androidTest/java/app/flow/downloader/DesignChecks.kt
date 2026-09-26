package app.flow.downloader

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner

/** Offline UI regression check. Does not download or change saved media. */
object DesignChecks {
    fun run(instrumentation: Instrumentation) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun invoke(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
        try {
            instrumentation.runOnMainSync {
                field("media").set(activity, Media("https://www.youtube.com/watch?v=test", "Design preview", listOf(
                    Choice("video", "1080p", false), Choice("audio", "MP3", true, format = "mp3"))))
                invoke("refreshChoices"); invoke("refresh")
                (field("exportSummary").get(activity) as Button).performClick()
                check((field("exportFields").get(activity) as LinearLayout).parent != null)
                check((field("advancedFields").get(activity) as View).visibility == View.GONE)
                (field("advancedToggle").get(activity) as Button).performClick()
                check((field("advancedFields").get(activity) as View).visibility == View.VISIBLE)
                (field("settingsDialog").get(activity) as android.app.Dialog).dismiss()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                (field("audioMode").get(activity) as Button).performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                check((field("exportSummary").get(activity) as Button).text.contains("MP3"))
                (field("exportSummary").get(activity) as Button).performClick()
                check((field("bitrateSpinner").get(activity) as Spinner).visibility == View.VISIBLE)
                (field("settingsDialog").get(activity) as android.app.Dialog).dismiss()
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
