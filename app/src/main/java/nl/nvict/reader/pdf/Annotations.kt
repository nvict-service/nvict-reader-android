package nl.nvict.reader.pdf

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicLong

/** Rechthoek in genormaliseerde paginacoördinaten (0..1 van de weergegeven breedte/hoogte). */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/**
 * Nog niet opgeslagen bewerkingen op een document. Posities zijn genormaliseerd (0..1) ten opzichte van de
 * weergegeven pagina, zodat ze onafhankelijk van zoom zijn; pas bij opslaan worden ze naar PDF-punten omgezet.
 */
sealed class Annotation {
    abstract val id: Long
    abstract val page: Int

    /** Tekstmarkering (echte PDF-highlight-annotatie). */
    data class Highlight(
        override val id: Long,
        override val page: Int,
        val rects: List<NRect>,
        val color: Int,
    ) : Annotation()

    /** Toegevoegde tekst (wordt in de pagina-inhoud gezet). [fontSizePt] in PDF-punten. */
    data class Text(
        override val id: Long,
        override val page: Int,
        val x: Float,
        val y: Float,
        val text: String,
        val fontSizePt: Float,
        val color: Int,
    ) : Annotation()

    /** Handtekening of andere afbeelding; [rect] in genormaliseerde coördinaten. */
    data class Image(
        override val id: Long,
        override val page: Int,
        val rect: NRect,
        val bitmap: Bitmap,
    ) : Annotation()

    companion object {
        private val ids = AtomicLong(1)
        fun nextId(): Long = ids.getAndIncrement()
    }
}

object HighlightColors {
    val all = listOf(
        0xFFFFEB3B.toInt() to "Geel",
        0xFF8BC34A.toInt() to "Groen",
        0xFF4FC3F7.toInt() to "Blauw",
        0xFFF48FB1.toInt() to "Roze",
        0xFFFFA726.toInt() to "Oranje",
    )
}

object TextColors {
    val all = listOf(
        0xFF000000.toInt() to "Zwart",
        0xFFD32F2F.toInt() to "Rood",
        0xFF1565C0.toInt() to "Blauw",
        0xFF2E7D32.toInt() to "Groen",
    )
}
