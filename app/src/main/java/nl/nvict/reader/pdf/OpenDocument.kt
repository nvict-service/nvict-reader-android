package nl.nvict.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.api.Meta
import io.legere.pdfiumandroid.api.PdfPasswordException
import io.legere.pdfiumandroid.api.PdfWriteCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.nvict.reader.ReaderApp
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/** Paginaformaat in PostScript-punten (1/72 inch). */
data class PageSize(val widthPt: Float, val heightPt: Float) {
    val ratio: Float get() = heightPt / widthPt
}

/** Een link op een pagina, met de positie in pixels van de weergegeven pagina. */
data class PageLink(val bounds: Rect, val uri: String?, val destPage: Int?)

/** Een gevonden zoekresultaat: tekenbereik op een pagina. */
data class SearchHit(val page: Int, val start: Int, val length: Int)

/**
 * Een geopend PDF-document. Alle bewerkingen lopen via één mutex en op de IO-dispatcher,
 * zodat Pdfium nooit tegelijk vanuit meerdere threads wordt aangesproken en een gesloten
 * document niet meer gebruikt wordt.
 */
class OpenDocument private constructor(
    val id: Long,
    val uri: Uri,
    val name: String,
    private val fd: ParcelFileDescriptor,
    private val doc: PdfDocument,
    val pageCount: Int,
    val pageSizes: List<PageSize>,
    val password: String?,
) : Closeable {

    private val mutex = Mutex()
    @Volatile private var closed = false

    private val pages = object : LinkedHashMap<Int, PdfPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfPage>): Boolean {
            if (size > MAX_OPEN_PAGES) {
                textPages.remove(eldest.key)?.close()
                eldest.value.close()
                return true
            }
            return false
        }
    }
    private val textPages = HashMap<Int, PdfTextPage>()

    private fun page(index: Int): PdfPage =
        pages.getOrPut(index) { doc.openPage(index) ?: throw IOException("Pagina $index kan niet worden geladen") }

    private fun textPage(index: Int): PdfTextPage {
        val p = page(index)
        return textPages.getOrPut(index) { p.openTextPage() }
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Document is gesloten" }
            block()
        }
    }

    /** Rendert een pagina op de opgegeven pixelgrootte. */
    suspend fun render(pageIndex: Int, widthPx: Int, heightPx: Int, darkPages: Boolean): Bitmap = locked {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        page(pageIndex).renderPageBitmap(
            bitmap, 0, 0, widthPx, heightPx,
            renderAnnot = true,
            canvasColor = 0,
            pageBackgroundColor = 0xFFFFFFFF.toInt(),
        )
        if (darkPages) invert(bitmap) else bitmap
    }

    private fun invert(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (c and 0xFF000000.toInt()) or (c.inv() and 0x00FFFFFF)
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    suspend fun links(pageIndex: Int, widthPx: Int, heightPx: Int): List<PageLink> = locked {
        val p = page(pageIndex)
        p.getPageLinks().map { link ->
            PageLink(
                bounds = p.mapRectToDevice(0, 0, widthPx, heightPx, 0, link.bounds),
                uri = link.uri?.takeIf { it.isNotBlank() },
                destPage = link.destPageIdx,
            )
        }
    }

    suspend fun charCount(pageIndex: Int): Int = locked { textPage(pageIndex).textPageCountChars() }

    suspend fun text(pageIndex: Int, start: Int, count: Int): String = locked {
        if (count <= 0) "" else textPage(pageIndex).textPageGetText(start, count) ?: ""
    }

    private val textCache = HashMap<Int, String>()

    suspend fun pageText(pageIndex: Int): String = locked {
        textCache.getOrPut(pageIndex) {
            val tp = textPage(pageIndex)
            val n = tp.textPageCountChars()
            if (n <= 0) "" else tp.textPageGetText(0, n) ?: ""
        }
    }

    /** Het woord rond tekenindex [index] als (start, eindIndex inclusief), of null. */
    suspend fun wordAt(pageIndex: Int, index: Int): Pair<Int, Int>? {
        val text = pageText(pageIndex)
        if (index !in text.indices || text[index].isWhitespace()) return null
        var a = index
        var b = index
        while (a > 0 && !text[a - 1].isWhitespace()) a--
        while (b < text.length - 1 && !text[b + 1].isWhitespace()) b++
        return a to b
    }

    /** Index van het teken op een punt in pixels van de weergegeven pagina, of -1. */
    suspend fun charIndexAt(pageIndex: Int, widthPx: Int, heightPx: Int, x: Int, y: Int, tolerancePx: Float = 24f): Int =
        locked {
            val p = page(pageIndex)
            val pt = p.mapDeviceCoordsToPage(0, 0, widthPx, heightPx, 0, x, y)
            val tol = tolerancePx.toDouble() * pageSizes[pageIndex].widthPt / widthPx
            textPage(pageIndex).textPageGetCharIndexAtPos(pt.x.toDouble(), pt.y.toDouble(), tol, tol)
        }

    /** Rechthoeken (in pixels) die het tekenbereik omsluiten. */
    suspend fun rangeRects(pageIndex: Int, start: Int, count: Int, widthPx: Int, heightPx: Int): List<Rect> = locked {
        if (count <= 0) return@locked emptyList()
        val p = page(pageIndex)
        val tp = textPage(pageIndex)
        val n = tp.textPageCountRects(start, count)
        (0 until n).mapNotNull { i ->
            tp.textPageGetRect(i)?.let { r: RectF -> p.mapRectToDevice(0, 0, widthPx, heightPx, 0, r) }
        }
    }

    /**
     * Zet een genormaliseerd punt (0..1 van de weergegeven pagina) om naar PDF-paginacoördinaten (punten,
     * oorsprong linksonder), rekening houdend met rotatie en cropbox.
     */
    suspend fun toPagePoints(pageIndex: Int, points: List<Pair<Float, Float>>): List<android.graphics.PointF> = locked {
        val p = page(pageIndex)
        val w = 4000
        val h = (w * pageSizes[pageIndex].ratio).toInt().coerceAtLeast(1)
        points.map { (nx, ny) ->
            p.mapDeviceCoordsToPage(0, 0, w, h, 0, (nx * w).toInt(), (ny * h).toInt())
        }
    }

    /**
     * Zet rechthoeken in PDF-coördinaten (left, top=bovenrand, right, bottom=onderrand) om naar genormaliseerde
     * schermcoördinaten. Alle vier hoeken worden omgerekend, zodat ook gedraaide pagina's kloppen.
     */
    suspend fun pdfRectsToNormalized(pageIndex: Int, rects: List<RectF>): List<NRect> = locked {
        val p = page(pageIndex)
        val w = 4000
        val h = (w * pageSizes[pageIndex].ratio).toInt().coerceAtLeast(1)
        rects.map { r ->
            val pts = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)
                .map { (x, y) -> p.mapPageCoordsToDevice(0, 0, w, h, 0, x.toDouble(), y.toDouble()) }
            NRect(
                pts.minOf { it.x } / w.toFloat(),
                pts.minOf { it.y } / h.toFloat(),
                pts.maxOf { it.x } / w.toFloat(),
                pts.maxOf { it.y } / h.toFloat(),
            )
        }
    }

    suspend fun outline(): List<Bookmark> = locked { doc.getTableOfContents() }

    suspend fun meta(): Meta = locked { doc.getDocumentMeta() }

    /** Zoekt in alle pagina's (hoofdletterongevoelig). */
    suspend fun search(query: String, onProgress: suspend (Int) -> Unit = {}): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        val hits = ArrayList<SearchHit>()
        for (pi in 0 until pageCount) {
            val text = pageText(pi).lowercase()
            var i = text.indexOf(q)
            while (i >= 0) {
                hits += SearchHit(pi, i, q.length)
                i = text.indexOf(q, i + q.length)
            }
            onProgress(pi + 1)
        }
        return hits
    }

    /**
     * Schrijft een kopie met alleen de opgegeven pagina's (0-gebaseerd) naar [out].
     * Werkt op een verse kopie van het document, zodat het geopende document onaangeroerd blijft.
     */
    suspend fun exportPages(context: Context, keep: Set<Int>, out: OutputStream) = withContext(Dispatchers.IO) {
        val copyFd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Bestand niet gevonden")
        val copy = ReaderApp.instance.pdfium.newDocument(copyFd, password)
        try {
            for (i in (pageCount - 1) downTo 0) {
                if (i !in keep) copy.deletePage(i)
            }
            val ok = copy.saveAsCopy(object : PdfWriteCallback {
                override fun WriteBlock(data: ByteArray?): Int {
                    if (data != null) out.write(data)
                    return data?.size ?: 0
                }
            }, PdfDocument.FPDF_NO_INCREMENTAL)
            out.flush()
            if (!ok) throw IOException("Exporteren mislukt")
        } finally {
            copy.close()
            copyFd.close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        textPages.values.forEach { runCatching { it.close() } }
        textPages.clear()
        pages.values.forEach { runCatching { it.close() } }
        pages.clear()
        runCatching { doc.close() }
        runCatching { fd.close() }
    }

    companion object {
        private const val MAX_OPEN_PAGES = 6
        private val nextId = AtomicLong(1)

        /**
         * Opent een document. Gooit [PdfPasswordException] als een (juist) wachtwoord nodig is.
         */
        suspend fun open(context: Context, uri: Uri, name: String, password: String? = null): OpenDocument =
            withContext(Dispatchers.IO) {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Bestand niet gevonden")
                val doc = try {
                    ReaderApp.instance.pdfium.newDocument(fd, password)
                } catch (e: Throwable) {
                    fd.close()
                    throw e
                }
                try {
                    val count = doc.getPageCount()
                    if (count <= 0) throw IOException("Document bevat geen pagina's")
                    val sizes = doc.getPageSizes(72).map { s ->
                        PageSize(s.width.coerceAtLeast(1).toFloat(), s.height.coerceAtLeast(1).toFloat())
                    }
                    OpenDocument(nextId.getAndIncrement(), uri, name, fd, doc, count, sizes, password)
                } catch (e: Throwable) {
                    doc.close()
                    fd.close()
                    throw e
                }
            }
    }
}
