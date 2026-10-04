package app.flow.downloader

/** Pure selection rules shared by UI and regression tests. Never invent source resolutions. */
object DownloadOptions {
    fun select(available: List<Choice>, audio: Boolean, codec: String, language: String?): List<Choice> =
        available.filter { it.audio == audio && it.language == language && (audio || codec == "Auto" || it.codec == codec) }
            .sortedWith(compareBy<Choice> { if (it.deviceCompatible) 0 else 1 }
                .thenBy { if (codec == "Auto" && it.codec == "H.264") 0 else 1 }).distinctBy { it.label }
            .sortedWith(compareByDescending<Choice> { if (it.audio) 0 else it.label.substringBefore('p').toIntOrNull() ?: 0 }
                .thenByDescending { if (it.audio) 0 else it.label.substringAfter(" · ").substringBefore(' ').toIntOrNull() ?: 0 })

    fun estimatedSize(choice: Choice?, bitrate: Int): Long =
        if (choice?.audio == true && choice.format in listOf("mp3", "m4a", "opus") && choice.duration > 0)
            (choice.duration * bitrate * 1000 / 8).toLong()
        else choice?.estimatedBytes ?: 0
}
