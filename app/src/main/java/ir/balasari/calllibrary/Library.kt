package ir.balasari.calllibrary

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class Rec(val docId: String, val name: String, val date: Long, val size: Long, val uri: Uri)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("library", Context.MODE_PRIVATE)
    fun tree(c: Context): String? = p(c).getString("tree", null)
    fun setTree(c: Context, v: String) = p(c).edit().putString("tree", v).apply()
    fun note(c: Context, id: String): String = p(c).getString("note:$id", "") ?: ""
    fun setNote(c: Context, id: String, v: String) {
        val e = p(c).edit()
        if (v.isBlank()) e.remove("note:$id") else e.putString("note:$id", v.trim())
        e.apply()
    }
    fun sort(c: Context) = p(c).getInt("sort", 0)
    fun setSort(c: Context, v: Int) = p(c).edit().putInt("sort", v).apply()
}

object Library {
    private val exts = listOf(".opus", ".m4a", ".aac", ".mp3", ".ogg", ".wav", ".amr", ".3gp", ".flac")

    fun scan(ctx: Context, tree: Uri): List<Rec> {
        val out = ArrayList<Rec>()
        walk(ctx, tree, DocumentsContract.getTreeDocumentId(tree), out, 0)
        return out
    }

    private fun walk(ctx: Context, tree: Uri, parentId: String, out: MutableList<Rec>, depth: Int) {
        if (depth > 4) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val cursor = (try { ctx.contentResolver.query(children, cols, null, null, null) } catch (e: Exception) { null }) ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(ctx, tree, id, out, depth + 1)
                } else {
                    val low = name.lowercase()
                    if (mime.startsWith("audio/") || exts.any { ext -> low.endsWith(ext) }) {
                        out.add(Rec(id, name, c.getLong(3), c.getLong(4), DocumentsContract.buildDocumentUriUsingTree(tree, id)))
                    }
                }
            }
        }
    }
}
