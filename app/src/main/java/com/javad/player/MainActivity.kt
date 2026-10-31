package com.javad.player

import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private var equalizer: Equalizer? = null
    private val tracks = mutableListOf<Pair<Uri, String>>()
    private val favorites = mutableSetOf<String>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var sortMode = 0
    private var darkMode = false

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = displayName(uri)
            if (tracks.none { it.first == uri }) tracks.add(uri to name)
        }
        renderTracks()
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            status.text = "پوشه انتخاب شد. برای افزودن فایل‌ها از «افزودن آهنگ» استفاده کنید."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
        ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 16)
            setBackgroundColor(if (darkMode) 0xFF151923.toInt() else 0xFFF6F7F8.toInt())
        }
        val title = TextView(this).apply {
            text = "♫  جواد پلیر"
            textSize = 27f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        outer.addView(title)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(btn("افزودن آهنگ") { pickAudio.launch(arrayOf("audio/*", "video/mp4")) }, weight())
        actions.addView(btn("انتخاب پوشه") { pickFolder.launch(null) }, weight())
        outer.addView(actions)
        val sort = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sort.addView(btn("مرتب‌سازی") {
            sortMode = (sortMode + 1) % 3
            renderTracks()
        }, weight())
        sort.addView(btn("اکولایزر") { showEqualizer() }, weight())
        sort.addView(btn("تم روشن/تیره") {
            darkMode = !darkMode
            buildUi()
        }, weight())
        outer.addView(sort)
        status = TextView(this).apply {
            text = "برای شروع، آهنگ‌ها را از حافظه گوشی انتخاب کنید."
            textSize = 14f
            setPadding(4, 14, 4, 10)
        }
        outer.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(btn("قبلی") { controller?.seekToPreviousMediaItem() })
        controls.addView(btn("پخش / مکث") {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        })
        controls.addView(btn("بعدی") { controller?.seekToNextMediaItem() })
        outer.addView(controls)
        outer.addView(btn("تنظیم زیر و بمی صدا") { showPitchDialog() })
        outer.addView(btn("جداسازی صدای خواننده (آفلاین)") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("جداسازی هوشمند خواننده")
                .setMessage("این بخش در نسخه پایه آماده‌سازی شده است؛ برای فعال‌سازی جداسازی واقعی باید مدل هوش مصنوعی آفلاین (مانند مدل جداسازی دو/چهار منبع) به پروژه افزوده و روی گوشی‌های هدف از نظر RAM و سرعت آزمایش شود.")
                .setPositiveButton("متوجه شدم", null).show()
        })
        setContentView(outer)
    }

    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun btn(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun displayName(uri: Uri): String {
        var result = uri.lastPathSegment ?: "آهنگ"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) result = c.getString(i)
            }
        }
        return result
    }

    private fun renderTracks() {
        list.removeAllViews()
        val sorted = when (sortMode) {
            1 -> tracks.sortedBy { it.second.lowercase() }
            2 -> tracks.sortedBy { it.second.length } // placeholder until media metadata/duration & file-size indexing
            else -> tracks.toList()
        }
        sorted.forEach { (uri, name) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(btn(name) { play(uri, name) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(btn(if (favorites.contains(uri.toString())) "♥" else "♡") {
                if (!favorites.add(uri.toString())) favorites.remove(uri.toString())
                renderTracks()
            })
            list.addView(row)
        }
        status.text = "${tracks.size} آهنگ | مرتب‌سازی: " + listOf("افزودن", "نام", "حالت آزمایشی")[sortMode]
    }

    private fun play(uri: Uri, name: String) {
        controller?.let {
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
            status.text = "در حال پخش: $name"
            try {
                equalizer?.release()
                val sessionId = MusicService.currentAudioSessionId
                if (sessionId > 0) equalizer = Equalizer(0, sessionId).apply { enabled = true }
            } catch (_: Exception) { }
        } ?: run { status.text = "در حال اتصال به سرویس پخش..." }
    }

    private fun showEqualizer() {
        val eq = equalizer
        if (eq == null) {
            Toast.makeText(this, "ابتدا یک آهنگ پخش کنید.", Toast.LENGTH_SHORT).show()
            return
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val range = eq.bandLevelRange
        for (band in 0 until eq.numberOfBands) {
            val hz = eq.getCenterFreq(band.toShort()) / 1000
            layout.addView(TextView(this).apply { text = "${hz} kHz" })
            val seek = SeekBar(this).apply {
                max = (range[1] - range[0]).toInt()
                progress = (eq.getBandLevel(band.toShort()) - range[0]).toInt().coerceIn(0, max)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                        if (user) eq.setBandLevel(band.toShort(), (p + range[0]).toShort())
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            layout.addView(seek)
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("اکولایزر").setView(layout)
            .setPositiveButton("بستن", null).show()
    }

    private fun showPitchDialog() {
        val seek = SeekBar(this).apply { max = 200; progress = 100 }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 4) }
        box.addView(TextView(this).apply { text = "زیر و بمی: از ۰٫۵ تا ۱٫۵ برابر" })
        box.addView(seek)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("زیر و بمی صدا").setView(box)
            .setPositiveButton("اعمال") { _, _ ->
                val pitch = 0.5f + seek.progress / 200f
                controller?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(1f, pitch))
            }.setNegativeButton("لغو", null).show()
    }

    override fun onDestroy() {
        equalizer?.release()
        controller?.release()
        super.onDestroy()
    }
}
