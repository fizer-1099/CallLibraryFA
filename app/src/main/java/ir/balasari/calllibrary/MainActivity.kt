package ir.balasari.calllibrary

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var all: List<Rec> = emptyList()
    private var shown: List<Rec> = emptyList()
    private var query = ""
    private lateinit var count: TextView
    private lateinit var sortBtn: Button
    private lateinit var listView: ListView
    private val handler = Handler(Looper.getMainLooper())
    private val sortNames = listOf("جدیدترین", "قدیمی‌ترین", "بزرگ‌ترین")
    private val dateFmt = android.icu.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", android.icu.util.ULocale("fa_IR@calendar=persian"))

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun fmtDate(ms: Long) = if (ms <= 0) "—" else dateFmt.format(Date(ms))
    private fun fmtSize(b: Long) = if (b >= 1048576) String.format(Locale.US, "%.1f MB", b / 1048576.0) else "${b / 1024} KB"
    private fun mmss(ms: Int) = String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(12), dp(8), dp(12))
                addView(TextView(context).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(context).apply { textSize = 13f; setTextColor(0xFF666666.toInt()) })
            }
            val r = shown[position]
            val note = Store.note(this@MainActivity, r.docId)
            (row.getChildAt(0) as TextView).text = r.name
            (row.getChildAt(1) as TextView).text = fmtDate(r.date) + "  •  " + fmtSize(r.size) + (if (note.isNotBlank()) "  •  📝 $note" else "")
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(28), dp(16), dp(8))
        }
        root.addView(TextView(this).apply { text = "کتابخانه ضبط مکالمه"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD })
        val pick = Button(this).apply { text = "انتخاب پوشه‌ی ضبط‌ها"; setOnClickListener { pickFolder() } }
        root.addView(pick)
        sortBtn = Button(this).apply {
            setOnClickListener { Store.setSort(this@MainActivity, (Store.sort(this@MainActivity) + 1) % sortNames.size); refreshList() }
        }
        root.addView(sortBtn)
        val search = EditText(this).apply { hint = "جستجو در نام فایل و یادداشت"; setSingleLine(true) }
        search.doAfterTextChanged { query = it?.toString() ?: ""; refreshList() }
        root.addView(search)
        count = TextView(this).apply { textSize = 13f; setPadding(0, dp(6), 0, dp(6)) }
        root.addView(count)
        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ -> openPlayer(shown[position]) }
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        startActivityForResult(i, 77)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        Store.setTree(this, uri.toString())
        reload()
    }

    private fun reload() {
        val t = Store.tree(this)
        if (t == null) {
            count.text = "ابتدا پوشه‌ای که ضبط‌ها در آن ذخیره می‌شوند را انتخاب کنید."
            return
        }
        count.text = "در حال بارگذاری…"
        Thread {
            val result = try { Library.scan(this, Uri.parse(t)) } catch (e: Exception) { null }
            runOnUiThread {
                if (result == null) {
                    count.text = "خواندن پوشه ناموفق بود؛ دوباره پوشه را انتخاب کنید."
                } else {
                    all = result
                    refreshList()
                }
            }
        }.start()
    }

    private fun refreshList() {
        val sort = Store.sort(this)
        sortBtn.text = "مرتب‌سازی: " + sortNames[sort] + " (برای تغییر لمس کنید)"
        val q = query.trim()
        val filtered = all.filter { r ->
            q.isEmpty() || r.name.contains(q, true) || Store.note(this, r.docId).contains(q, true)
        }
        shown = when (sort) {
            0 -> filtered.sortedByDescending { it.date }
            1 -> filtered.sortedBy { it.date }
            else -> filtered.sortedByDescending { it.size }
        }
        adapter.notifyDataSetChanged()
        if (Store.tree(this) != null) count.text = "${shown.size} ضبط"
    }

    private fun openPlayer(r: Rec) {
        val mp = MediaPlayer()
        var prepared = false
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val time = TextView(this)
        val seek = SeekBar(this)
        val play = Button(this).apply { text = "▶ پخش" }
        val rename = Button(this).apply { text = "تغییر نام" }
        val noteEt = EditText(this).apply { hint = "یادداشت (مثلاً اسم طرف مکالمه)"; setText(Store.note(this@MainActivity, r.docId)) }
        box.addView(time); box.addView(seek); box.addView(play); box.addView(noteEt); box.addView(rename)

        try {
            mp.setDataSource(this, r.uri)
            mp.prepare()
            prepared = true
            seek.max = mp.duration
            time.text = "0:00 / " + mmss(mp.duration)
        } catch (e: Exception) {
            time.text = "پخش این فایل ممکن نیست: ${e.message}"
        }

        val tick = object : Runnable {
            override fun run() {
                if (prepared) {
                    try {
                        seek.progress = mp.currentPosition
                        time.text = mmss(mp.currentPosition) + " / " + mmss(mp.duration)
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 300)
            }
        }
        play.setOnClickListener {
            if (!prepared) return@setOnClickListener
            if (mp.isPlaying) { mp.pause(); play.text = "▶ پخش" } else { mp.start(); play.text = "⏸ توقف" }
        }
        mp.setOnCompletionListener { play.text = "▶ پخش"; seek.progress = 0 }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && prepared) mp.seekTo(p) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val dlg = AlertDialog.Builder(this)
            .setTitle(r.name)
            .setView(box)
            .setPositiveButton("بستن", null)
            .setNeutralButton("اشتراک") { _, _ -> share(r) }
            .setNegativeButton("حذف") { _, _ -> confirmDelete(r) }
            .create()
        rename.setOnClickListener { dlg.dismiss(); askRename(r) }
        dlg.setOnDismissListener {
            handler.removeCallbacks(tick)
            Store.setNote(this, r.docId, noteEt.text.toString())
            try { mp.release() } catch (_: Exception) {}
            refreshList()
        }
        dlg.show()
        tick.run()
    }

    private fun share(r: Rec) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, r.uri)
            clipData = ClipData.newRawUri("", r.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(i, "اشتراک‌گذاری ضبط")) } catch (e: Exception) { toast("اشتراک‌گذاری ناموفق بود") }
    }

    private fun askRename(r: Rec) {
        val et = EditText(this).apply { setText(r.name); setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("تغییر نام")
            .setView(et)
            .setPositiveButton("ذخیره") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isEmpty() || newName == r.name) return@setPositiveButton
                try {
                    val newUri = DocumentsContract.renameDocument(contentResolver, r.uri, newName)
                    if (newUri == null) toast("تغییر نام ناموفق بود") else {
                        val note = Store.note(this, r.docId)
                        if (note.isNotBlank()) Store.setNote(this, DocumentsContract.getDocumentId(newUri), note)
                        Store.setNote(this, r.docId, "")
                        reload()
                    }
                } catch (e: Exception) { toast("تغییر نام ناموفق بود: ${e.message}") }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun confirmDelete(r: Rec) {
        AlertDialog.Builder(this)
            .setTitle("حذف ضبط؟")
            .setMessage(r.name + "\n\nاین کار قابل برگشت نیست.")
            .setPositiveButton("حذف") { _, _ ->
                try {
                    if (DocumentsContract.deleteDocument(contentResolver, r.uri)) {
                        Store.setNote(this, r.docId, "")
                        reload()
                    } else toast("حذف ناموفق بود")
                } catch (e: Exception) { toast("حذف ناموفق بود: ${e.message}") }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
