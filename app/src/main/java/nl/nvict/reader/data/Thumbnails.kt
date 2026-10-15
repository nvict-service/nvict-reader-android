package nl.nvict.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.nvict.reader.ReaderApp
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Miniatuur van de eerste pagina plus bestandsgrootte, voor het beginscherm. */
data class FileThumb(val bitmap: Bitmap?, val sizeBytes: Long?)

/**
 * Maakt en bewaart miniaturen van recente bestanden. De sleutel bevat de bestandsgrootte, zodat een
 * gewijzigd (bijvoorbeeld bewerkt en opgeslagen) bestand automatisch een nieuwe miniatuur krijgt.
 */
object Thumbnails {
    private const val WIDTH = 360
    private val memory = LruCache<String, FileThumb>(40)
    private val limiter = Semaphore(2)

    suspend fun load(context: Context, uri: Uri): FileThumb? = withContext(Dispatchers.IO) {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        val key = hash("$uri|$size")
        memory.get(key)?.let { return@withContext it }

        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val file = File(dir, "$key.png")
        val bitmap = if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            limiter.withPermit { render(context, uri) }?.also { bmp ->
                runCatching { FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
            }
        }
        // Onbereikbaar bestand (verwijderd, geen rechten meer): geen miniatuur, wel een regel in de lijst
        if (bitmap == null && size == null) return@withContext null
        FileThumb(bitmap, size).also { memory.put(key, it) }
    }

    private fun render(context: Context, uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            ReaderApp.instance.pdfium.newDocument(fd).use { doc ->
                doc.openPage(0)?.use { page ->
                    val w = page.getPageWidthPoint().coerceAtLeast(1)
                    val h = page.getPageHeightPoint().coerceAtLeast(1)
                    val height = (WIDTH.toLong() * h / w).toInt().coerceIn(1, WIDTH * 3)
                    val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
                    page.renderPageBitmap(bmp, 0, 0, WIDTH, height, renderAnnot = true, canvasColor = 0)
                    bmp
                }
            }
        }
    }.getOrNull()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
