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
        val preferences = instrumentation.targetContext.getSharedPreferences("appearance", 0)
        val previous = preferences.getString("theme", null)
        val updates = instrumentation.targetContext.getSharedPreferences("updates", 0)
        val previousCheck = updates.getLong("checked", 0)
        val hadCheck = updates.contains("checked")
        updates.edit().putLong("checked", System.currentTimeMillis()).commit()
        try {
            for (mode in listOf("dark", "light", "system")) {
                preferences.edit().putString("theme", mode).commit()
                runOnce(instrumentation)
            }
        } finally {
            preferences.edit().let { if (previous == null) it.remove("theme") else it.putString("theme", previous) }.commit()
            updates.edit().let { if (hadCheck) it.putLong("checked", previousCheck) else it.remove("checked") }.commit()
        }
    }
    private fun runOnce(instrumentation: Instrumentation) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun invoke(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
        try {
            instrumentation.runOnMainSync {
                field("media").set(activity, Media("https://www.youtube.com/watch?v=test", "Design preview", listOf(
                    Choice("video", "1080p", false), Choice("audio", "MP3", true, format = "mp3"))))
                invoke("refreshChoices"); invoke("refresh")
                (field("editInfo").get(activity) as Button).performClick()
                check(field("infoDialog").get(activity) != null)
                val editor = field("infoDialog").get(activity) as android.app.Dialog
                fun views(view: View): List<View> = listOf(view) + if (view is android.view.ViewGroup)
                    (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
                val controls = views(editor.window!!.decorView)
                val inputs = controls.filterIsInstance<android.widget.EditText>()
                check(inputs.size == 3)
                inputs[0].setText("Edited title"); inputs[1].setText("Edited artist"); inputs[2].setText("My file")
                controls.filterIsInstance<Button>().last().performClick()
                val edited = field("media").get(activity) as Media
                check(edited.title == "Edited title" && edited.artist == "Edited artist" && edited.fileName == "My file")
                (field("editInfo").get(activity) as Button).performClick()
                val cancelled = field("infoDialog").get(activity) as android.app.Dialog
                views(cancelled.window!!.decorView).filterIsInstance<android.widget.EditText>().first().setText("Discard me")
                cancelled.dismiss()
                check(field("media").get(activity) == edited)
                (field("exportSummary").get(activity) as Button).performClick()
                check((field("exportFields").get(activity) as LinearLayout).parent != null)
                check((field("advancedFields").get(activity) as View).visibility == View.GONE)
                (field("advancedToggle").get(activity) as Button).performClick()
                check((field("advancedFields").get(activity) as View).visibility == View.VISIBLE)
                val advanced = field("advancedFields").get(activity) as LinearLayout
                check(advanced.paddingTop > 0)
                check((advanced.getChildAt(1).layoutParams as LinearLayout.LayoutParams).topMargin > 0)
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
