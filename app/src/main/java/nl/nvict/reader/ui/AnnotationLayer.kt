package nl.nvict.reader.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import nl.nvict.reader.R
import nl.nvict.reader.pdf.Annotation
import nl.nvict.reader.pdf.HighlightColors
import kotlin.math.roundToInt

/** Verhoudingen die ook PdfEditor gebruikt, zodat de voorvertoning gelijk is aan het opgeslagen resultaat. */
internal const val TEXT_ASCENT = 0.78f
internal const val TEXT_LINE = 1.25f

internal fun textPaint(sizePx: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.SANS_SERIF
    textSize = sizePx
    this.color = color
}

private fun textSizePx(a: Annotation.Text, widthPx: Int, pageWidthPt: Float) = a.fontSizePt * widthPx / pageWidthPt

/** Grenzen van een annotatie in pixels van de weergegeven pagina. */
internal fun annotationBounds(a: Annotation, widthPx: Int, heightPx: Int, pageWidthPt: Float): Rect = when (a) {
    is Annotation.Text -> {
        val size = textSizePx(a, widthPx, pageWidthPt)
        val paint = textPaint(size, a.color)
        val lines = a.text.split('\n')
        val w = lines.maxOf { paint.measureText(it) }.coerceAtLeast(size)
        val left = a.x * widthPx
        val top = a.y * heightPx
        Rect(left, top, left + w, top + lines.size * size * TEXT_LINE)
    }
    is Annotation.Image -> Rect(a.rect.left * widthPx, a.rect.top * heightPx, a.rect.right * widthPx, a.rect.bottom * heightPx)
    is Annotation.Highlight -> a.rects
        .map { Rect(it.left * widthPx, it.top * heightPx, it.right * widthPx, it.bottom * heightPx) }
        .reduce { acc, r -> Rect(minOf(acc.left, r.left), minOf(acc.top, r.top), maxOf(acc.right, r.right), maxOf(acc.bottom, r.bottom)) }
}

/** Bovenste annotatie onder [pos], of null. */
internal fun hitTest(annots: List<Annotation>, pos: Offset, widthPx: Int, heightPx: Int, pageWidthPt: Float, slop: Float): Annotation? =
    annots.asReversed().firstOrNull { a ->
        when (a) {
            is Annotation.Highlight -> a.rects.any { r ->
                Rect(r.left * widthPx, r.top * heightPx, r.right * widthPx, r.bottom * heightPx).inflate(slop / 2).contains(pos)
            }
            else -> annotationBounds(a, widthPx, heightPx, pageWidthPt).inflate(slop).contains(pos)
        }
    }

internal fun DrawScope.drawAnnotations(
    annots: List<Annotation>,
    selectedId: Long?,
    widthPx: Int,
    heightPx: Int,
    pageWidthPt: Float,
    darkPages: Boolean,
    accent: Color,
    handleRadius: Float,
) {
    annots.forEach { a ->
        when (a) {
            is Annotation.Highlight -> {
                val c = Color(a.color)
                a.rects.forEach { r ->
                    drawRect(
                        color = if (darkPages) c.copy(alpha = 0.45f) else c.copy(alpha = 0.55f),
                        topLeft = Offset(r.left * widthPx, r.top * heightPx),
                        size = Size(r.width * widthPx, r.height * heightPx),
                        blendMode = if (darkPages) BlendMode.SrcOver else BlendMode.Multiply,
                    )
                }
            }
            is Annotation.Image -> drawImage(
                image = a.bitmap.asImageBitmap(),
                dstOffset = IntOffset((a.rect.left * widthPx).roundToInt(), (a.rect.top * heightPx).roundToInt()),
                dstSize = IntSize((a.rect.width * widthPx).roundToInt().coerceAtLeast(1), (a.rect.height * heightPx).roundToInt().coerceAtLeast(1)),
            )
            is Annotation.Text -> {
                val size = textSizePx(a, widthPx, pageWidthPt)
                val paint = textPaint(size, if (darkPages) invertRgb(a.color) else a.color)
                val left = a.x * widthPx
                val top = a.y * heightPx
                drawIntoCanvas { canvas ->
                    a.text.split('\n').forEachIndexed { i, line ->
                        canvas.nativeCanvas.drawText(line, left, top + size * TEXT_ASCENT + i * size * TEXT_LINE, paint)
                    }
                }
            }
        }
        if (a.id == selectedId) {
            val b = annotationBounds(a, widthPx, heightPx, pageWidthPt).inflate(4.dp.toPx())
            drawRect(
                color = accent,
                topLeft = b.topLeft,
                size = b.size,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))),
            )
            if (a is Annotation.Image) {
                drawCircle(accent, handleRadius, b.bottomRight)
                drawCircle(Color.White, handleRadius * 0.45f, b.bottomRight)
            }
        }
    }
}

/** Bij donkere pagina's wordt de pagina geïnverteerd; tekst-voorvertoning doet daarom hetzelfde. */
private fun invertRgb(c: Int): Int = (c and 0xFF000000.toInt()) or (c.inv() and 0x00FFFFFF)

/**
 * Zwevende balk boven (of, als daar geen ruimte is, onder) een gebied op de pagina.
 * [belowGap] is extra ruimte onder het gebied (bijvoorbeeld voor selectiegrepen).
 */
@Composable
internal fun FloatingBar(
    anchor: Rect,
    pageWidthPx: Int,
    belowGap: Int = 0,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { 8.dp.roundToPx() }
    val x = anchor.left.roundToInt().coerceIn(0, (pageWidthPx - size.width).coerceAtLeast(0))
    val top = anchor.top.roundToInt()
    val y = if (top - size.height - gap >= 0) top - size.height - gap else anchor.bottom.roundToInt() + belowGap + gap
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .onSizeChanged { size = it },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            content()
        }
    }
}

/** Werkbalk voor een geselecteerde annotatie. */
@Composable
internal fun AnnotationBar(
    annotation: Annotation,
    anchor: Rect,
    pageWidthPx: Int,
    handleGap: Int,
    onColor: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    FloatingBar(anchor = anchor, pageWidthPx = pageWidthPx, belowGap = handleGap) {
        when (annotation) {
            is Annotation.Highlight -> {
                Spacer(Modifier.width(6.dp))
                HighlightColors.all.forEach { (color, _) ->
                    ColorDot(color, selected = color == annotation.color) { onColor(color) }
                }
            }
            is Annotation.Text -> TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            is Annotation.Image -> Unit
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(26.dp)
            .background(Color(color), CircleShape)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            .clickable(onClick = onClick),
    )
}
