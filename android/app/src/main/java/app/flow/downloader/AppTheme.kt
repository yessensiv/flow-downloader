package app.flow.downloader

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View

object AppTheme {
    fun mode(context: Context) = context.getSharedPreferences("appearance", 0).getString("theme", "dark") ?: "dark"
    fun isLight(context: Context) = when (mode(context)) {
        "light" -> true
        "system" -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    fun apply(activity: Activity) {
        val light = isLight(activity)
        activity.setTheme(if (light) R.style.FlowLightTheme else R.style.FlowTheme)
        val colors = colors(activity)
        activity.window.statusBarColor = colors.background
        activity.window.navigationBarColor = if (light && android.os.Build.VERSION.SDK_INT < 27) Color.rgb(15, 20, 16) else colors.background
        activity.window.decorView.systemUiVisibility = if (light)
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 27) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0) else 0
    }
    fun colors(context: Context) = Palette(isLight(context))
    class Palette(val light: Boolean) {
        val background = if (light) Color.rgb(245, 248, 243) else Color.rgb(15, 20, 16)
        val text = if (light) Color.rgb(25, 36, 25) else Color.WHITE
        val accent = if (light) Color.rgb(48, 105, 20) else Color.rgb(194, 255, 112)
        val onAccent = if (light) Color.WHITE else Color.rgb(20, 28, 16)
        fun color(r: Int, g: Int, b: Int): Int {
            if (!light) return Color.rgb(r, g, b)
            return when {
                r == 15 && g == 20 -> background
                r >= 250 && g < 200 -> Color.rgb(159, 43, 27)
                r > 210 -> text
                r >= 140 -> Color.rgb(84, 102, 80)
                r >= 100 -> accent
                r >= 49 -> Color.rgb(168, 184, 161)
                r >= 35 -> Color.rgb(223, 239, 207)
                r >= 24 -> Color.WHITE
                else -> Color.rgb(237, 243, 231)
            }
        }
    }
}
