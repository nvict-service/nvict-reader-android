package nl.nvict.reader.pdf

import android.content.Context
import android.graphics.PointF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Schrijft de bewerkingen (markeringen, tekst, handtekeningen) met PdfBox in een kopie van het document.
 * Pdfium levert de omrekening van schermcoördinaten naar PDF-punten; PdfBox schrijft.
 */
object PdfEditor {

    /** Lettertype dat de Windows-versie ook gebruikt voor toegevoegde tekst. */
    private val font = PDType1Font.HELVETICA
    private const val LINE_HEIGHT = 1.25f

    suspend fun apply(context: Context, doc: OpenDocument, annotations: List<Annotation>, out: OutputStream) {
        // 1) Alle coördinaten vooraf omrekenen via Pdfium (suspend), per annotatie
        val resolved = annotations.map { a -> a to resolve(doc, a) }

        // 2) Schrijven met PdfBox op de IO-dispatcher
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(doc.uri) ?: error("Bestand niet gevonden")
            val pdf = input.use { PDDocument.load(it, doc.password ?: "") }
            try {
                pdf.isAllSecurityToBeRemoved = true
                resolved.forEach { (a, pts) ->
                    val page = pdf.getPage(a.page)
                    when (a) {
                        is Annotation.Highlight -> writeHighlight(pdf, page, a, pts)
                        is Annotation.Text -> writeText(pdf, page, a, pts)
                        is Annotation.Image -> writeImage(pdf, page, a, pts)
                    }
                }
                pdf.save(out)
                out.flush()
            } finally {
                pdf.close()
            }
        }
    }

    /** Hoekpunten in PDF-punten: per annotatie de punten die de schrijver nodig heeft. */
    private suspend fun resolve(doc: OpenDocument, a: Annotation): List<PointF> = when (a) {
        is Annotation.Highlight -> doc.toPagePoints(
            a.page,
            a.rects.flatMap { r -> listOf(r.left to r.top, r.right to r.bottom) },
        )
        is Annotation.Text -> doc.toPagePoints(a.page, listOf(a.x to a.y))
        is Annotation.Image -> doc.toPagePoints(
            a.page,
            listOf(a.rect.left to a.rect.top, a.rect.right to a.rect.bottom),
        )
    }

    private fun writeHighlight(pdf: PDDocument, page: com.tom_roush.pdfbox.pdmodel.PDPage, a: Annotation.Highlight, pts: List<PointF>) {
        if (pts.isEmpty()) return
        val quads = FloatArray(a.rects.size * 8)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in a.rects.indices) {
            val p1 = pts[i * 2]
            val p2 = pts[i * 2 + 1]
            val left = min(p1.x, p2.x)
            val right = max(p1.x, p2.x)
            val bottom = min(p1.y, p2.y)
            val top = max(p1.y, p2.y)
            // Volgorde volgens Acrobat: linksboven, rechtsboven, linksonder, rechtsonder
            quads[i * 8] = left; quads[i * 8 + 1] = top
            quads[i * 8 + 2] = right; quads[i * 8 + 3] = top
            quads[i * 8 + 4] = left; quads[i * 8 + 5] = bottom
            quads[i * 8 + 6] = right; quads[i * 8 + 7] = bottom
            minX = min(minX, left); maxX = max(maxX, right)
            minY = min(minY, bottom); maxY = max(maxY, top)
        }
        val annot = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
        annot.rectangle = PDRectangle(minX, minY, maxX - minX, maxY - minY)
        annot.quadPoints = quads
        annot.color = rgb(a.color)
        annot.setPrinted(true)
        annot.contents = ""
        runCatching { annot.constructAppearances(pdf) } // weergave voor viewers die zelf geen uiterlijk maken
        // getAnnotations() is niet altijd gekoppeld aan het document; daarom de lijst expliciet terugzetten
        val list = page.annotations.toMutableList()
        list.add(annot)
        page.annotations = list
    }

    private fun writeText(pdf: PDDocument, page: com.tom_roush.pdfbox.pdmodel.PDPage, a: Annotation.Text, pts: List<PointF>) {
        val origin = pts.firstOrNull() ?: return
        val lines = a.text.split('\n').map { sanitize(it) }
        val size = a.fontSizePt
        val ascent = size * 0.78f
        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setFont(font, size)
            cs.setNonStrokingColor(red(a.color), green(a.color), blue(a.color))
            cs.newLineAtOffset(origin.x, origin.y - ascent)
            lines.forEachIndexed { i, line ->
                if (i > 0) cs.newLineAtOffset(0f, -size * LINE_HEIGHT)
                if (line.isNotEmpty()) cs.showText(line)
            }
            cs.endText()
        }
    }

    private fun writeImage(pdf: PDDocument, page: com.tom_roush.pdfbox.pdmodel.PDPage, a: Annotation.Image, pts: List<PointF>) {
        if (pts.size < 2) return
        val x = min(pts[0].x, pts[1].x)
        val y = min(pts[0].y, pts[1].y)
        val w = abs(pts[1].x - pts[0].x)
        val h = abs(pts[1].y - pts[0].y)
        val image = LosslessFactory.createFromImage(pdf, a.bitmap)
        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(image, x, y, w, h)
        }
    }

    /** Helvetica (WinAnsi) kent geen emoji's en dergelijke; vervang wat niet kan. */
    private fun sanitize(text: String): String = buildString {
        text.forEach { c ->
            val ok = runCatching { font.encode(c.toString()); true }.getOrDefault(false)
            append(if (ok) c else '?')
        }
    }

    private fun rgb(color: Int) = PDColor(floatArrayOf(red(color) / 255f, green(color) / 255f, blue(color) / 255f), PDDeviceRGB.INSTANCE)
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
}
