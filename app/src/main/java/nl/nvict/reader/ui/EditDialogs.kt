package nl.nvict.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import nl.nvict.reader.R
import nl.nvict.reader.data.SavedSignature
import nl.nvict.reader.data.Signatures
import nl.nvict.reader.pdf.Annotation
import nl.nvict.reader.pdf.TextColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ tekst

@Composable
fun TextAnnotationDialog(
    initial: Annotation.Text?,
    onDismiss: () -> Unit,
    onConfirm: (text: String, fontSizePt: Float, color: Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var size by remember { mutableFloatStateOf(initial?.fontSizePt ?: 12f) }
    var color by remember { mutableIntStateOf(initial?.color ?: TextColors.all.first().first) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_text else R.string.edit_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(stringResource(R.string.font_size, size.roundToInt()), style = MaterialTheme.typography.labelLarge)
                Slider(value = size, onValueChange = { size = it.roundToInt().toFloat() }, valueRange = 6f..36f, steps = 29)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextColors.all.forEach { (c, _) -> ColorDot(c, selected = c == color) { color = c } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, size, color) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ handtekening kiezen

@Composable
fun SignaturePickerDialog(onDismiss: () -> Unit, onPick: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val store = remember { Signatures(context) }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<List<SavedSignature>?>(null) }
    var drawing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val list = store.list()
        saved = list
        if (list.isEmpty()) drawing = true // nog niets opgeslagen: direct tekenen
    }

    if (drawing) {
        SignaturePadDialog(
            onDismiss = { if (saved.isNullOrEmpty()) onDismiss() else drawing = false },
            onDone = { bmp ->
                scope.launch {
                    store.save(bmp)
                    onPick(bmp)
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_signature)) },
        text = {
            val list = saved
            when {
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> Text(stringResource(R.string.no_signatures))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.file.name }) { sig ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable { onPick(sig.bitmap) }
                                    .padding(8.dp),
                            ) {
                                Image(
                                    bitmap = sig.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    store.delete(sig)
                                    saved = store.list()
                                }
                            }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { drawing = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.new_signature))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ handtekening tekenen

private val PEN_COLORS = listOf(0xFF111111.toInt(), 0xFF0D47A1.toInt())

@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var penColor by remember { mutableIntStateOf(PEN_COLORS.first()) }
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 3.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.sign_here), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    current = listOf(down.position)
                                    drag(down.id) { ch ->
                                        ch.consume()
                                        current = current + ch.position
                                    }
                                    if (current.size == 1) current = listOf(current[0], current[0] + Offset(0.5f, 0.5f))
                                    strokes.add(current)
                                    current = emptyList()
                                }
                            },
                    ) {
                        // Hulplijn onderaan, zoals op papier
                        val y = size.height * 0.75f
                        drawLine(Color(0xFFBDBDBD), Offset(24f, y), Offset(size.width - 24f, y), strokeWidth = 2f)
                        (strokes + listOf(current)).forEach { pts ->
                            if (pts.size < 2) return@forEach
                            drawPath(
                                smoothPath(pts),
                                color = Color(penColor),
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PEN_COLORS.forEach { c -> ColorDot(c, selected = c == penColor) { penColor = c } }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { strokes.clear() }) { Text(stringResource(R.string.clear)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { renderSignature(strokes, penColor, strokeWidthPx)?.let(onDone) },
                        enabled = strokes.isNotEmpty(),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/** Vloeiende lijn door de punten (kwadratische Bézier via de middens). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val prev = pts[i - 1]
        val mid = Offset((prev.x + pts[i].x) / 2f, (prev.y + pts[i].y) / 2f)
        quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    lineTo(pts.last().x, pts.last().y)
}

/** Zet de getekende lijnen om naar een bijgesneden PNG-waardige bitmap met transparante achtergrond. */
private fun renderSignature(strokes: List<List<Offset>>, penColor: Int, lineWidthPx: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = lineWidthPx * 2
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad
    val maxY = all.maxOf { it.y } + pad
    val w = max(1f, maxX - minX)
    val h = max(1f, maxY - minY)
    // Hoge resolutie voor een scherpe afdruk, maar begrensd in geheugen
    val scale = min(2f, 1600f / max(w, h)).coerceAtLeast(0.5f)
    val bmp = Bitmap.createBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = penColor
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = lineWidthPx * scale
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        if (pts.size < 2) return@forEach
        val path = android.graphics.Path()
        fun tx(o: Offset) = (o.x - minX) * scale
        fun ty(o: Offset) = (o.y - minY) * scale
        path.moveTo(tx(pts[0]), ty(pts[0]))
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val mid = Offset((prev.x + pts[i].x) / 2f, (prev.y + pts[i].y) / 2f)
            path.quadTo(tx(prev), ty(prev), tx(mid), ty(mid))
        }
        path.lineTo(tx(pts.last()), ty(pts.last()))
        canvas.drawPath(path, paint)
    }
    return bmp
}
