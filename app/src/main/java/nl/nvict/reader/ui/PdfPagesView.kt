package nl.nvict.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nl.nvict.reader.pdf.PageBitmapCache
import nl.nvict.reader.pdf.PageLink
import android.graphics.Rect as ARect
import kotlin.math.roundToInt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val MAX_RENDER_WIDTH = 2400
private val PAGE_MARGIN = 8.dp
private val PAGE_SPACING = 8.dp

/**
 * Verticaal scrollende paginaweergave met knijpzoom, horizontaal scrollen bij inzoomen,
 * dubbeltikken om te zoomen, links, tekstselectie (lang indrukken en slepen) en zoekmarkeringen.
 */
@Composable
fun PdfPagesView(
    state: DocState,
    darkPages: Boolean,
    vm: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val doc = state.doc
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { PAGE_MARGIN.toPx() }
        val spacingPx = with(density) { PAGE_SPACING.toPx() }
        val fitWidthPx = (containerWidthPx - 2 * marginPx).coerceAtLeast(100f)
        val pageWidthPx = (fitWidthPx * state.scale).roundToInt()
        val contentWidthDp: Dp = with(density) { (pageWidthPx + 2 * marginPx).toDp() }

        fun pageHeightPx(i: Int): Float = pageWidthPx * doc.pageSizes[i].ratio

        /** Geschatte verticale scrollpositie in pixels (nodig om rond de vingers te zoomen). */
        fun scrollYPx(): Float {
            val first = listState.firstVisibleItemIndex
            var y = 0f
            for (i in 0 until first) y += pageHeightPx(i) + spacingPx
            return y + listState.firstVisibleItemScrollOffset
        }

        // Scrollverzoeken (ga naar pagina, zoektreffer, link)
        LaunchedEffect(state.scrollRequest) {
            val req = state.scrollRequest ?: return@LaunchedEffect
            listState.scrollToItem(req.page)
        }

        // Bijhouden welke pagina het grootste deel van het scherm vult
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val viewportEnd = info.viewportEndOffset
                info.visibleItemsInfo.maxByOrNull { item ->
                    val top = item.offset.coerceAtLeast(0)
                    val bottom = (item.offset + item.size).coerceAtMost(viewportEnd)
                    bottom - top
                }?.index ?: -1
            }.distinctUntilChanged().collect { page -> if (page >= 0) vm.pageShown(state, page) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Knijpzoom in de Initial-pass, zodat de lijst niet tegelijk scrolt
                .pointerInput(doc.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pinching = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                pinching = true
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid()
                                val old = state.scale
                                val new = (old * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                if (new != old) {
                                    val factor = new / old
                                    state.scale = new
                                    // Houd het punt tussen de vingers op zijn plaats
                                    val dx = (hScroll.value + centroid.x) * (factor - 1)
                                    val dy = (scrollYPx() + centroid.y) * (factor - 1)
                                    hScroll.dispatchRawDelta(dx)
                                    listState.dispatchRawDelta(dy)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pinching) {
                                // Na een knijpgebaar: resterende vinger niet als scroll/tik laten doorgaan
                                event.changes.forEach { it.consume() }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .horizontalScroll(hScroll),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(contentWidthDp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = PAGE_MARGIN),
            ) {
                items(count = doc.pageCount, key = { it }) { index ->
                    val heightDp = with(density) { pageHeightPx(index).toDp() }
                    PageItem(
                        state = state,
                        vm = vm,
                        pageIndex = index,
                        widthPx = pageWidthPx,
                        heightPx = pageHeightPx(index).roundToInt(),
                        darkPages = darkPages,
                        onDoubleTap = { tap ->
                            val target = if (state.scale > 1.01f) MIN_SCALE else 2.5f
                            val factor = target / state.scale
                            // Positie van de tik in de totale inhoud (vóór het zoomen)
                            val xContent = hScroll.value + marginPx + tap.x
                            var yContent = marginPx + tap.y
                            for (i in 0 until index) yContent += pageHeightPx(i) + spacingPx
                            val viewportX = xContent - hScroll.value
                            state.scale = target
                            scope.launch {
                                hScroll.scrollTo((xContent * factor - viewportX).roundToInt().coerceAtLeast(0))
                                listState.dispatchRawDelta(yContent * (factor - 1))
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = PAGE_MARGIN)
                            .padding(bottom = PAGE_SPACING)
                            .width(with(density) { pageWidthPx.toDp() })
                            .height(heightDp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageItem(
    state: DocState,
    vm: ReaderViewModel,
    pageIndex: Int,
    widthPx: Int,
    heightPx: Int,
    darkPages: Boolean,
    onDoubleTap: (Offset) -> Unit,
    modifier: Modifier,
) {
    val doc = state.doc
    val renderWidth = widthPx.coerceAtMost(MAX_RENDER_WIDTH)
    val renderHeight = (renderWidth * doc.pageSizes[pageIndex].ratio).roundToInt().coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Rendering: cache eerst, anders renderen (met korte vertraging zodat knijpzoom niet elke frame rendert)
    val bitmap: State<Bitmap?> = produceState<Bitmap?>(
        initialValue = PageBitmapCache[PageBitmapCache.Key(doc.id, pageIndex, renderWidth, darkPages)]
            ?: PageBitmapCache.anyFor(doc.id, pageIndex, darkPages),
        doc.id, pageIndex, renderWidth, darkPages,
    ) {
        val key = PageBitmapCache.Key(doc.id, pageIndex, renderWidth, darkPages)
        val cached = PageBitmapCache[key]
        if (cached != null) {
            value = cached
            return@produceState
        }
        delay(120)
        val bmp = runCatching { doc.render(pageIndex, renderWidth, renderHeight, darkPages) }.getOrNull()
        if (bmp != null) {
            PageBitmapCache.put(key, bmp)
            value = bmp
        }
    }

    // Links op deze pagina, in pixels van de huidige weergavegrootte
    val links by produceState<List<PageLink>>(emptyList(), doc.id, pageIndex, widthPx, heightPx) {
        value = runCatching { doc.links(pageIndex, widthPx, heightPx) }.getOrDefault(emptyList())
    }

    // Selectie-rechthoeken
    val selection = state.selection?.takeIf { it.page == pageIndex }
    val selectionRects by produceState<List<ARect>>(emptyList(), selection, widthPx, heightPx) {
        value = selection?.let {
            runCatching { doc.rangeRects(pageIndex, it.start, it.count, widthPx, heightPx) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    // Zoektreffers op deze pagina
    val hitsOnPage = remember(state.searchHits, pageIndex) { state.searchHits.filter { it.page == pageIndex } }
    val currentHit = state.currentHit
    val hitRects by produceState<List<Pair<List<ARect>, Boolean>>>(emptyList(), hitsOnPage, currentHit, widthPx, heightPx) {
        value = hitsOnPage.map { hit ->
            val rects = runCatching { doc.rangeRects(pageIndex, hit.start, hit.length, widthPx, heightPx) }
                .getOrDefault(emptyList())
            rects to (hit == currentHit)
        }
    }

    val pageBackground = if (darkPages) Color(0xFF121212) else Color.White
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = modifier
            .shadow(2.dp)
            .background(pageBackground)
            .pointerInput(doc.id, pageIndex, widthPx, heightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (up == null) {
                        // Geen 'up' binnen de tijd: lang ingedrukt (selectie), of geannuleerd door scrollen
                        val stillPressed = currentEvent.changes.any { it.id == down.id && it.pressed && !it.isConsumed }
                        if (!stillPressed) return@awaitEachGesture
                        val startIdx = runCatchingSuspend {
                            doc.charIndexAt(pageIndex, widthPx, heightPx, down.position.x.roundToInt(), down.position.y.roundToInt())
                        } ?: -1
                        if (startIdx < 0) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.selection = Selection(pageIndex, startIdx, startIdx)
                        down.consume()
                        // Positie opzoeken gebeurt buiten de pointer-lus (die mag niet lang blokkeren)
                        var latest: Offset? = null
                        var lookup: Job? = null
                        drag(down.id) { change ->
                            change.consume()
                            latest = change.position
                            if (lookup?.isActive != true) {
                                lookup = scope.launch {
                                    while (true) {
                                        val p = latest ?: break
                                        latest = null
                                        val x = p.x.roundToInt().coerceIn(0, widthPx)
                                        val y = p.y.roundToInt().coerceIn(0, heightPx)
                                        val idx = runCatchingSuspend { doc.charIndexAt(pageIndex, widthPx, heightPx, x, y) } ?: -1
                                        if (idx >= 0) state.selection = Selection(pageIndex, startIdx, idx)
                                    }
                                }
                            }
                        }
                        return@awaitEachGesture
                    }
                    // Tik: dubbeltik?
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                    if (second != null) {
                        second.consume()
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                        onDoubleTap(up.position)
                        return@awaitEachGesture
                    }
                    // Enkele tik
                    if (state.selection != null) {
                        state.selection = null
                        return@awaitEachGesture
                    }
                    val px = up.position.x.roundToInt()
                    val py = up.position.y.roundToInt()
                    links.firstOrNull { it.bounds.contains(px, py) }?.let { vm.openLink(state, it.uri, it.destPage) }
                }
            },
    ) {
        val bmp = bitmap.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        if (selectionRects.isNotEmpty() || hitRects.isNotEmpty()) {
            val selColor = if (isDark) NVictColors.SelectionDark else NVictColors.SelectionLight
            Canvas(modifier = Modifier.fillMaxSize()) {
                hitRects.forEach { (rects, current) ->
                    val c = if (current) NVictColors.SearchHitCurrent else NVictColors.SearchHit
                    rects.forEach { r -> drawRect(c, Offset(r.left.toFloat(), r.top.toFloat()), Size(r.width().toFloat(), r.height().toFloat())) }
                }
                selectionRects.forEach { r ->
                    drawRect(selColor, Offset(r.left.toFloat(), r.top.toFloat()), Size(r.width().toFloat(), r.height().toFloat()))
                }
            }
        }
    }
}

private suspend inline fun <T> runCatchingSuspend(block: () -> T): T? =
    try { block() } catch (e: Throwable) { if (e is kotlinx.coroutines.CancellationException) throw e; null }
