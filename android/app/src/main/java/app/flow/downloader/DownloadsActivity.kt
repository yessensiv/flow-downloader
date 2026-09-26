package app.flow.downloader

import android.app.Activity
import android.app.AlertDialog
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import android.util.LruCache
import java.util.concurrent.Executors

class DownloadsActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var selectAll: CheckBox
    private lateinit var selectionButton: Button
    private lateinit var search: EditText
    private lateinit var history: DownloadHistory
    private var english = false
    private var music = false
    private var query = ""
    private var deleting = false
    private var pendingDeletion: SavedDownload? = null
    private var pendingBulkDeletion: List<SavedDownload> = emptyList()
    private var selecting = false
    private val selectedUris = linkedSetOf<String>()
    private val worker = Executors.newSingleThreadExecutor()
    private val imageWorker = Executors.newSingleThreadExecutor()
    private val thumbnailCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private fun text(ru: String, en: String) = if (english) en else ru
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val lime = Color.rgb(194, 255, 112)
    private fun animateRows() {
        if (selecting || !ValueAnimator.areAnimatorsEnabled()) return
        (0 until results.childCount).forEach { index ->
            val child = results.getChildAt(index)
            child.alpha = 0f; child.translationY = dp(6).toFloat()
            child.animate().alpha(1f).translationY(0f).setStartDelay(index * 28L).setDuration(200L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        english = intent.getBooleanExtra("english", false)
        history = DownloadHistory(this)
        music = savedInstanceState?.getBoolean("music") ?: false
        selecting = savedInstanceState?.getBoolean("selecting") ?: false
        selectedUris.addAll(savedInstanceState?.getStringArrayList("selectedUris").orEmpty())
        pendingDeletion = savedInstanceState?.getString("pendingDeletion")?.let { uri -> history.list().find { it.uri == uri } }
        pendingBulkDeletion = savedInstanceState?.getStringArrayList("pendingBulkDeletion")
            ?.let { uris -> history.list().filter { it.uri in uris } }.orEmpty()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 20, 16)); isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        scroll.addView(list); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        scroll.requestApplyInsets()
        buildHeader()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                renderItems()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render()
    }
    private fun label(value: String, size: Int, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (muted) Color.rgb(175, 190, 174) else Color.WHITE)
        setPadding(0, dp(8), 0, dp(8)); setLineSpacing(dp(3).toFloat(), 1f)
        if (size >= 20) setTypeface(null, Typeface.BOLD)
    }
    private fun action(value: String, click: () -> Unit) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false; setTextColor(lime)
        background = GradientDrawable().apply { setColor(Color.rgb(28, 36, 29)); cornerRadius = dp(12).toFloat() }
        minHeight = dp(48)
        setOnTouchListener { view, event ->
            if (ValueAnimator.areAnimatorsEnabled()) when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(.98f).scaleY(.98f).setDuration(75L).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            false
        }
        setOnClickListener { click() }
    }
    private fun cardAction(value: String, description: String = value, click: () -> Unit) = TextView(this).apply {
        text = value; textSize = 14f; gravity = android.view.Gravity.CENTER
        setTextColor(lime); minHeight = dp(48); isClickable = true; isFocusable = true
        contentDescription = description
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(42, 194, 255, 112)), null, mask)
        setOnTouchListener { view, event ->
            if (ValueAnimator.areAnimatorsEnabled()) when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(.97f).scaleY(.97f).setDuration(75L).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            false
        }
        setOnClickListener { click() }
    }
    private fun buildHeader() {
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        navigation.addView(action(text("‹  Назад", "‹  Back")) { finish() }, LinearLayout.LayoutParams(dp(110), dp(48)))
        selectionButton = action(text("Выбрать", "Select")) { toggleSelectionMode() }.apply { gravity = android.view.Gravity.CENTER }
        navigation.addView(selectionButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        list.addView(navigation, LinearLayout.LayoutParams(-1, dp(48)))
        list.addView(label(text("Мои загрузки", "My downloads"), 28))
        list.addView(label(text("Видео и музыка — каждый файл на своём месте.",
            "Your saved videos and music, neatly separated."), 15, true))
        search = EditText(this).apply {
            textSize = 16f; setSingleLine(); setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(144, 159, 144)); hint = text("Поиск по названию", "Search by title")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            background = GradientDrawable().apply { setColor(Color.rgb(20, 27, 21)); cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(54, 67, 54)) }
            setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(54)
        }
        list.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(4) })
        tabs = LinearLayout(this)
        list.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(Color.rgb(23, 31, 24)); cornerRadius = dp(16).toFloat() }
            visibility = View.GONE
        }
        val summary = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        selectAll = CheckBox(this).apply {
            textSize = 13f; setTextColor(Color.WHITE); buttonTintList = ColorStateList.valueOf(lime)
            setOnCheckedChangeListener { _, checked ->
                val visible = visibleItems().map { it.uri }.toSet()
                if (checked) selectedUris.addAll(visible) else selectedUris.removeAll(visible)
                updateSelectionUi(visibleItems())
            }
        }
        summary.addView(selectAll, LinearLayout.LayoutParams(0, dp(42), 1f))
        selectionCount = TextView(this).apply { textSize = 13f; setTextColor(Color.rgb(175, 190, 174)); gravity = android.view.Gravity.CENTER_VERTICAL }
        summary.addView(selectionCount)
        selectionBar.addView(summary)
        val bulkActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bulkActions.addView(action("") { shareSelected() }.apply { tag = "bulk-share" }, LinearLayout.LayoutParams(0, dp(44), 1f))
        bulkActions.addView(action("") { confirmDeleteSelected() }.apply { tag = "bulk-delete" },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        selectionBar.addView(bulkActions)
        list.addView(selectionBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(results)
    }
    private fun render() {
        val all = history.list()
        tabs.removeAllViews()
        listOf(false, true).forEach { audio ->
            val count = all.count { it.isAudio == audio }
            val name = if (audio) text("Музыка", "Music") else text("Видео", "Video")
            tabs.addView(action("$name · $count") {
                if (music != audio) selectedUris.clear()
                music = audio; render()
            }.apply {
                if (music == audio) {
                    setTextColor(Color.rgb(15, 20, 16))
                    background = GradientDrawable().apply { setColor(lime); cornerRadius = dp(12).toFloat() }
                }
                isEnabled = !deleting
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if (audio) leftMargin = dp(8) })
        }
        renderItems()
    }
    private fun renderItems() {
        results.removeAllViews()
        val items = history.list().filter { it.isAudio == music && it.title.contains(query.trim(), ignoreCase = true) }
        if (items.isEmpty()) results.addView(label(if (query.isNotBlank()) text("Ничего не найдено. Попробуйте другое название.", "No matches. Try another title.") else if (music) text("Музыки пока нет\nСохраните аудио из Flow — оно появится здесь.",
            "No music yet\nSave audio from Flow to see it here.") else text("Видео пока нет\nСохраните видео из Flow — оно появится здесь.",
            "No videos yet\nSave a video from Flow to see it here."), 17, true))
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(4), dp(9))
                tag = item.uri
                background = rowBackground(item.uri in selectedUris)
                isClickable = true; isFocusable = true
                setOnClickListener { if (selecting) toggleSelected(item) else access(item, false) }
            }
            if (selecting) {
                row.addView(CheckBox(this).apply {
                    buttonTintList = ColorStateList.valueOf(lime)
                    isChecked = item.uri in selectedUris
                    tag = "selection-checkbox"
                    isClickable = false; isFocusable = false
                }, LinearLayout.LayoutParams(dp(30), dp(42)).apply { rightMargin = dp(4) })
            }
            val thumbnail = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(Color.rgb(20, 27, 21)); cornerRadius = dp(10).toFloat() }
                clipToOutline = true
                contentDescription = item.title
            }
            if (item.thumbnail.isNotBlank()) {
                val cached = thumbnailCache.get(item.thumbnail)
                if (cached != null) thumbnail.setImageBitmap(cached) else loadThumbnail(item.thumbnail, thumbnail)
            }
            else thumbnail.setImageResource(if (item.isAudio) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_slideshow)
            row.addView(thumbnail, LinearLayout.LayoutParams(dp(104), dp(60)).apply { rightMargin = dp(11) })

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_VERTICAL
            }
            details.addView(label(item.title, 15).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dp(3)); setLineSpacing(dp(1).toFloat(), 1f)
            })
            val metadata = "${android.text.format.Formatter.formatShortFileSize(this, item.bytes)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.savedAt))}"
            details.addView(label(metadata, 12, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
            })
            row.addView(details, LinearLayout.LayoutParams(0, dp(60), 1f))
            if (!selecting) row.addView(cardAction("⋮", text("Действия с файлом", "File actions")) {
                AlertDialog.Builder(this).setTitle(item.title)
                    .setItems(arrayOf(
                        text("Открыть", "Open"),
                        text("Поделиться", "Share"),
                        text("Удалить файл с устройства…", "Delete file from device…"),
                        text("Убрать только запись (файл останется)", "Remove history only (keep file)")
                    )) { _, which ->
                        when (which) {
                            0 -> access(item, false)
                            1 -> access(item, true)
                            2 -> confirmDelete(item)
                            3 -> removeEntry(item)
                        }
                    }.show()
            }.apply { isEnabled = !deleting; alpha = if (deleting) .45f else 1f },
                LinearLayout.LayoutParams(dp(44), dp(48)))
            results.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        updateSelectionUi(items)
        animateRows()
    }
    private fun visibleItems(): List<SavedDownload> = history.list()
        .filter { it.isAudio == music && it.title.contains(query.trim(), ignoreCase = true) }

    private fun rowBackground(selected: Boolean): RippleDrawable {
        val shape = GradientDrawable().apply {
            setColor(if (selected) Color.rgb(35, 48, 34) else Color.rgb(28, 36, 29))
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), if (selected) Color.rgb(105, 151, 65) else Color.rgb(28, 36, 29))
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(36, 194, 255, 112)), shape, null)
    }
    private fun selectedItems(): List<SavedDownload> = history.list()
        .filter { it.isAudio == music && it.uri in selectedUris }

    private fun toggleSelectionMode() {
        if (deleting) return
        selecting = !selecting
        if (!selecting) selectedUris.clear()
        renderItems()
    }

    private fun toggleSelected(item: SavedDownload) {
        if (item.uri in selectedUris) selectedUris.remove(item.uri) else selectedUris.add(item.uri)
        updateSelectionUi(visibleItems())
    }

    private fun updateSelectionUi(visible: List<SavedDownload>) {
        selectionButton.text = if (selecting) text("Отмена", "Cancel") else text("Выбрать", "Select")
        selectionButton.isEnabled = !deleting
        selectionBar.visibility = if (selecting) View.VISIBLE else View.GONE
        if (!selecting) return
        selectAll.setOnCheckedChangeListener(null)
        val visibleUris = visible.map { it.uri }
        selectAll.text = text("Выбрать все (${visible.size})", "Select all (${visible.size})")
        selectAll.isChecked = visibleUris.isNotEmpty() && visibleUris.all { it in selectedUris }
        selectAll.isEnabled = visible.isNotEmpty() && !deleting
        selectAll.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedUris.addAll(visibleUris) else selectedUris.removeAll(visibleUris.toSet())
            updateSelectionUi(visibleItems())
        }
        (0 until results.childCount).forEach { index ->
            val row = results.getChildAt(index) as? LinearLayout ?: return@forEach
            val uri = row.tag as? String ?: return@forEach
            val selected = uri in selectedUris
            row.background = rowBackground(selected)
            row.findViewWithTag<CheckBox>("selection-checkbox")?.isChecked = selected
        }
        val count = selectedItems().size
        selectionCount.text = text("Выбрано: $count", "Selected: $count")
        val share = selectionBar.findViewWithTag<Button>("bulk-share")
        val delete = selectionBar.findViewWithTag<Button>("bulk-delete")
        share.text = text("↗  Поделиться", "↗  Share")
        delete.text = text("Удалить", "Delete")
        delete.compoundDrawablePadding = dp(8)
        delete.setCompoundDrawablesRelativeWithIntrinsicBounds(
            getDrawable(android.R.drawable.ic_menu_delete)?.mutate()?.apply { setTint(lime) }, null, null, null
        )
        listOf(share, delete).forEach { button ->
            button.isEnabled = count > 0 && !deleting
            button.alpha = if (button.isEnabled) 1f else .45f
        }
    }

    private fun shareSelected() {
        if (deleting) return
        val items = selectedItems()
        if (items.isEmpty()) return
        worker.execute {
            val available = items.all { item -> runCatching {
                contentResolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")?.use { true } ?: false
            }.getOrDefault(false) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!available) {
                    unavailable()
                    return@runOnUiThread
                }
                try {
                    val uris = ArrayList(items.map { Uri.parse(it.uri) })
                    val title = if (uris.size == 1) items.first().title else text("${uris.size} файла Flow", "${uris.size} Flow files")
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = if (music) "audio/*" else "video/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri(title, uris.first()).apply {
                            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                        }
                    }
                    startActivity(Intent.createChooser(send, text("Поделиться файлами", "Share files")))
                } catch (_: Exception) {
                    Toast.makeText(this, text("Не удалось открыть меню отправки.", "Could not open the share menu."), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDeleteSelected() {
        if (deleting) return
        val items = selectedItems()
        if (items.isEmpty()) return
        AlertDialog.Builder(this).setTitle(text("Удалить ${items.size} файлов?", "Delete ${items.size} files?"))
            .setMessage(text("Файлы будут удалены с устройства и из истории Flow. Это действие нельзя отменить.",
                "The files will be deleted from your device and Flow history. This cannot be undone."))
            .setNegativeButton(text("Отмена", "Cancel"), null)
            .setPositiveButton(text("Удалить", "Delete")) { _, _ -> deleteSelected(items) }.show()
    }

    private fun deleteSelected(items: List<SavedDownload>) {
        if (deleting) return
        if (android.os.Build.VERSION.SDK_INT >= 30 && items.all { Uri.parse(it.uri).authority == android.provider.MediaStore.AUTHORITY }) {
            pendingBulkDeletion = items
            try {
                startIntentSenderForResult(
                    android.provider.MediaStore.createDeleteRequest(contentResolver, items.map { Uri.parse(it.uri) }).intentSender,
                    32, null, 0, 0, 0
                )
            } catch (_: Exception) {
                pendingBulkDeletion = emptyList()
                AlertDialog.Builder(this).setMessage(text("Android не смог открыть подтверждение удаления. Файлы не изменены.",
                    "Android could not show delete confirmation. No files were changed."))
                    .setPositiveButton("OK", null).show()
            }
            return
        }
        deleting = true
        render()
        worker.execute {
            val deleted = mutableListOf<SavedDownload>()
            items.forEach { item ->
                val result = runCatching {
                    val uri = Uri.parse(item.uri)
                    val removed = if (android.provider.DocumentsContract.isDocumentUri(this, uri))
                        android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                    else contentResolver.delete(uri, null, null) > 0
                    if (removed) history.remove(item.uri)
                    removed
                }.getOrDefault(false)
                if (result) deleted += item
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                deleting = false
                deleted.forEach { selectedUris.remove(it.uri) }
                if (deleted.isEmpty()) selecting = false
                render()
                if (deleted.size == items.size) Toast.makeText(this,
                    text("Удалено файлов: ${deleted.size}.", "Deleted ${deleted.size} files."), Toast.LENGTH_LONG).show()
                else AlertDialog.Builder(this).setMessage(text(
                    "Удалено ${deleted.size} из ${items.size}. Для остальных отмените выбор и подтвердите удаление по одному через меню ⋮.",
                    "Deleted ${deleted.size} of ${items.size}. Cancel selection, then confirm deletion individually from each ⋮ menu."))
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun loadThumbnail(rawUrl: String, target: ImageView) {
        imageWorker.execute {
            val bitmap = runCatching {
                val url = URL(rawUrl)
                require(url.protocol == "https" && (url.host == "i.ytimg.com" || url.host.endsWith(".ytimg.com")))
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                connection.connect()
                try {
                    require(connection.responseCode in 200..299)
                    val data = connection.inputStream.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                        while (true) { val count = stream.read(buffer); if (count < 0) break; total += count; require(total <= 2 * 1024 * 1024); out.write(buffer, 0, count) }
                        out.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    val sample = generateSequence(1) { it * 2 }.takeWhile { bounds.outWidth / it > 960 || bounds.outHeight / it > 540 }.lastOrNull() ?: 1
                    BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
                } finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) {
                thumbnailCache.put(rawUrl, bitmap)
                runOnUiThread { if (!isDestroyed && target.parent != null) target.setImageBitmap(bitmap) }
            }
        }
    }
    private fun removeEntry(item: SavedDownload) {
        AlertDialog.Builder(this).setTitle(text("Убрать из истории?", "Remove from history?"))
                    .setMessage(text("Файл останется в выбранной папке.", "The file stays in its folder."))
                    .setPositiveButton(text("Убрать", "Remove")) { _, _ ->
                        worker.execute {
                            val result = runCatching { history.remove(item.uri) }
                            runOnUiThread { if (!isDestroyed) { if (result.isSuccess) render() else unavailable() } }
                        }
                    }.setNegativeButton(text("Отмена", "Cancel"), null).show()
    }
    private fun confirmDelete(item: SavedDownload) {
        if (deleting) return
        AlertDialog.Builder(this).setTitle(text("Удалить файл?", "Delete file?"))
            .setMessage(item.title + "\n\n" + text("Файл будет удалён из выбранной папки и из Flow. Отменить удаление в приложении нельзя.",
                "The file will be deleted from its folder and from Flow. This cannot be undone in the app."))
            .setNegativeButton(text("Отмена", "Cancel"), null)
            .setPositiveButton(text("Удалить файл", "Delete file")) { _, _ -> deleteFile(item) }.show()
    }
    private fun deleteFile(item: SavedDownload) {
        if (deleting) return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val savedUri = Uri.parse(item.uri)
            val mediaUri = if (savedUri.authority == android.provider.MediaStore.AUTHORITY) savedUri
                else runCatching { android.provider.MediaStore.getMediaUri(this, savedUri) }.getOrNull()
            if (mediaUri != null) {
                pendingDeletion = item
                try {
                    startIntentSenderForResult(
                        android.provider.MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri)).intentSender,
                        31, null, 0, 0, 0
                    )
                } catch (_: Exception) {
                    pendingDeletion = null
                    AlertDialog.Builder(this).setMessage(text("Android не смог открыть подтверждение удаления. Файл и запись оставлены.",
                        "Android could not show its delete confirmation. The file and entry were kept."))
                        .setPositiveButton("OK", null).show()
                }
                return
            }
        }
        deleting = true; render()
        worker.execute {
            var fileDeleted = false
            val result = runCatching {
                history.deleteFile(item) { value ->
                    val uri = Uri.parse(value)
                    require(android.provider.DocumentsContract.isDocumentUri(this, uri))
                    val supports = contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                        it.moveToFirst() && it.getInt(0) and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
                    } ?: false
                    if (!supports) false else android.provider.DocumentsContract.deleteDocument(contentResolver, uri).also { fileDeleted = it }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                deleting = false; render()
                when {
                    result.getOrDefault(false) -> Toast.makeText(this, text("Файл и запись удалены.", "File and entry deleted."), Toast.LENGTH_LONG).show()
                    fileDeleted -> AlertDialog.Builder(this).setMessage(text("Файл удалён, но запись не удалось обновить. Уберите её из истории вручную.", "File deleted, but history could not be updated. Remove the entry manually.")).setPositiveButton("OK", null).show()
                    result.exceptionOrNull() is SecurityException -> requestDeleteAccess(item)
                    else -> AlertDialog.Builder(this).setMessage(text("Не удалось удалить файл. Возможно, он уже удалён или хранилище не поддерживает удаление. Запись оставлена в истории.",
                        "Could not delete the file. It may be missing or the provider does not support deletion. The history entry was kept.")).setPositiveButton("OK", null).show()
                }
            }
        }
    }
    private fun requestDeleteAccess(item: SavedDownload) {
        AlertDialog.Builder(this).setTitle(text("Нужен доступ к файлу", "File access needed"))
            .setMessage(text("Выберите этот же файл в системном окне, чтобы разрешить удаление. После выбора снова появится подтверждение.",
                "Select this same file in the system picker to allow deletion. You will be asked to confirm again."))
            .setNegativeButton(text("Отмена", "Cancel"), null)
            .setPositiveButton(text("Выбрать файл", "Select file")) { _, _ ->
                pendingDeletion = item
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(item.uri))
                }, 30)
            }.show()
    }
    @Deprecated("Uses system document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 32) {
            val items = pendingBulkDeletion
            pendingBulkDeletion = emptyList()
            if (resultCode == RESULT_OK && items.isNotEmpty()) {
                worker.execute {
                    val removed = mutableListOf<SavedDownload>()
                    items.forEach { item ->
                        if (runCatching { history.remove(item.uri) }.isSuccess) removed += item
                    }
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        removed.forEach { selectedUris.remove(it.uri) }
                        render()
                        if (removed.size == items.size) Toast.makeText(this,
                            text("Файлов удалено: ${removed.size}.", "Deleted ${removed.size} files."), Toast.LENGTH_LONG).show()
                        else AlertDialog.Builder(this).setMessage(text(
                            "Android удалил файлы, но не все записи истории удалось обновить.",
                            "Android deleted the files, but some history entries could not be updated."))
                            .setPositiveButton("OK", null).show()
                    }
                }
            } else renderItems()
            return
        }
        if (requestCode == 31) {
            val item = pendingDeletion ?: return
            pendingDeletion = null
            if (resultCode == RESULT_OK) {
                worker.execute {
                    val result = runCatching { history.remove(item.uri) }
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        render()
                        if (result.isFailure) AlertDialog.Builder(this)
                            .setMessage(text("Android удалил файл, но историю обновить не удалось.", "Android deleted the file, but the history could not be updated."))
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
            return
        }
        if (requestCode != 30) return
        val item = pendingDeletion ?: return
        pendingDeletion = null
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri != Uri.parse(item.uri)) {
            Toast.makeText(this, text("Выбран другой файл. Ничего не удалено.", "A different file was selected. Nothing deleted."), Toast.LENGTH_LONG).show()
            return
        }
        if (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
            Toast.makeText(this, text("Хранилище не предоставило доступ на удаление.", "The provider did not grant write access."), Toast.LENGTH_LONG).show()
            return
        }
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        confirmDelete(item)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("music", music); outState.putString("pendingDeletion", pendingDeletion?.uri)
        outState.putBoolean("selecting", selecting); outState.putStringArrayList("selectedUris", ArrayList(selectedUris))
        outState.putStringArrayList("pendingBulkDeletion", ArrayList(pendingBulkDeletion.map { it.uri }))
        super.onSaveInstanceState(outState)
    }
    private fun access(item: SavedDownload, share: Boolean) {
        worker.execute {
            val available = runCatching { contentResolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")?.use { true } ?: false }.getOrDefault(false)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!available) { unavailable(); return@runOnUiThread }
                try {
                    val uri = Uri.parse(item.uri)
                    val request = if (share) Intent(Intent.ACTION_SEND).apply {
                        type = item.mime; putExtra(Intent.EXTRA_STREAM, uri)
                    } else Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime)
                    request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    request.clipData = ClipData.newRawUri(item.title, uri)
                    startActivity(Intent.createChooser(request, text("Выберите приложение", "Choose an app")))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, text("Нет приложения для этого формата.", "No app supports this format."), Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) { unavailable() }
            }
        }
    }
    private fun unavailable() {
        Toast.makeText(this, text("Файл недоступен: он мог быть удалён, перемещён или доступ отозван.",
            "File unavailable: it may have been moved, deleted, or access revoked."), Toast.LENGTH_LONG).show()
    }
    override fun onDestroy() { worker.shutdownNow(); imageWorker.shutdownNow(); super.onDestroy() }
}
