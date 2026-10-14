package nl.nvict.reader.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legere.pdfiumandroid.api.PdfPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.nvict.reader.R
import nl.nvict.reader.data.RecentFiles
import nl.nvict.reader.pdf.OpenDocument
import nl.nvict.reader.pdf.PageBitmapCache
import nl.nvict.reader.pdf.SearchHit
import java.io.File
import java.io.FileOutputStream

/** Tekstselectie: tekenbereik op één pagina (start en end inclusief, in willekeurige volgorde). */
data class Selection(val page: Int, val anchor: Int, val focus: Int) {
    val start: Int get() = minOf(anchor, focus)
    val count: Int get() = kotlin.math.abs(focus - anchor) + 1
}

/** UI-toestand per geopend document (Compose-state, dus alleen op de main thread wijzigen). */
class DocState(val doc: OpenDocument) {
    var currentPage by mutableIntStateOf(0)          // 0-gebaseerd
    var scale by mutableStateOf(1f)                  // 1 = passend in breedte
    var selection by mutableStateOf<Selection?>(null)
    var searchQuery by mutableStateOf("")
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
    var searchIndex by mutableIntStateOf(-1)
    var searchProgress by mutableIntStateOf(0)       // aantal doorzochte pagina's, 0 = niet bezig
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
    internal var searchJob: Job? = null

    val currentHit: SearchHit? get() = searchHits.getOrNull(searchIndex)
}

/** Verzoek om naar een pagina te scrollen; [token] maakt elk verzoek uniek. */
data class ScrollRequest(val page: Int, val token: Long = System.nanoTime())

sealed class UiEvent {
    data class Toast(val text: String) : UiEvent()
    data class ShareFile(val file: File, val mime: String, val title: String) : UiEvent()
    data class OpenUrl(val url: String) : UiEvent()
    data class Print(val uri: Uri, val name: String) : UiEvent()
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    val recentFiles = RecentFiles(app)

    private val _docs = MutableStateFlow<List<DocState>>(emptyList())
    val docs: StateFlow<List<DocState>> = _docs.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    val recent = recentFiles.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val darkPages = recentFiles.darkPages.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Bestand dat op een wachtwoord wacht (toont de wachtwoorddialoog). */
    var passwordRequest by mutableStateOf<PasswordRequest?>(null)
        private set

    data class PasswordRequest(val uri: Uri, val name: String, val wrong: Boolean)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun active(): DocState? = _docs.value.firstOrNull { it.doc.id == _activeId.value }

    fun setActive(id: Long) {
        _activeId.value = id
    }

    fun setDarkPages(on: Boolean) {
        viewModelScope.launch { recentFiles.setDarkPages(on) }
    }

    // ---------------------------------------------------------------- openen/sluiten

    fun open(uri: Uri, password: String? = null, fromRecent: Boolean = false) {
        viewModelScope.launch {
            // Al open? Dan alleen activeren.
            _docs.value.firstOrNull { it.doc.uri == uri }?.let {
                _activeId.value = it.doc.id
                return@launch
            }
            val name = displayName(uri)
            try {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val doc = OpenDocument.open(ctx, uri, name, password)
                val state = DocState(doc)
                val last = recentFiles.lastPage(uri).coerceIn(0, doc.pageCount - 1)
                state.currentPage = last
                if (last > 0) state.scrollRequest = ScrollRequest(last)
                _docs.value = _docs.value + state
                _activeId.value = doc.id
                passwordRequest = null
                recentFiles.touch(uri, name, last)
            } catch (e: PdfPasswordException) {
                passwordRequest = PasswordRequest(uri, name, wrong = password != null)
            } catch (e: Throwable) {
                passwordRequest = null
                if (fromRecent) recentFiles.remove(uri)
                _events.send(UiEvent.Toast(ctx.getString(R.string.open_failed)))
            }
        }
    }

    fun cancelPassword() {
        passwordRequest = null
    }

    fun close(id: Long) {
        val state = _docs.value.firstOrNull { it.doc.id == id } ?: return
        state.searchJob?.cancel()
        val remaining = _docs.value - state
        _docs.value = remaining
        if (_activeId.value == id) _activeId.value = remaining.lastOrNull()?.doc?.id
        viewModelScope.launch(Dispatchers.IO) {
            PageBitmapCache.evictDocument(id)
            state.doc.close()
        }
    }

    fun removeRecent(uri: Uri) {
        viewModelScope.launch { recentFiles.remove(uri) }
    }

    fun clearRecent() {
        viewModelScope.launch { recentFiles.clear() }
    }

    // ---------------------------------------------------------------- navigatie

    fun pageShown(state: DocState, page: Int) {
        if (state.currentPage == page) return
        state.currentPage = page
        viewModelScope.launch { recentFiles.updatePage(state.doc.uri, page) }
    }

    fun goToPage(state: DocState, page: Int) {
        val p = page.coerceIn(0, state.doc.pageCount - 1)
        state.scrollRequest = ScrollRequest(p)
    }

    fun openLink(state: DocState, uri: String?, destPage: Int?) {
        when {
            destPage != null -> goToPage(state, destPage)
            uri != null -> viewModelScope.launch { _events.send(UiEvent.OpenUrl(uri)) }
        }
    }

    // ---------------------------------------------------------------- zoeken

    fun search(state: DocState, query: String) {
        state.searchJob?.cancel()
        state.searchQuery = query
        state.searchHits = emptyList()
        state.searchIndex = -1
        if (query.isBlank()) {
            state.searchProgress = 0
            return
        }
        state.searchJob = viewModelScope.launch {
            state.searchProgress = 1
            val hits = state.doc.search(query) { done -> state.searchProgress = done }
            state.searchHits = hits
            state.searchProgress = 0
            if (hits.isEmpty()) {
                _events.send(UiEvent.Toast(ctx.getString(R.string.search_none)))
            } else {
                // Begin bij de eerste treffer op of na de huidige pagina
                val idx = hits.indexOfFirst { it.page >= state.currentPage }.takeIf { it >= 0 } ?: 0
                showHit(state, idx)
            }
        }
    }

    fun nextHit(state: DocState) {
        if (state.searchHits.isEmpty()) return
        showHit(state, (state.searchIndex + 1) % state.searchHits.size)
    }

    fun prevHit(state: DocState) {
        if (state.searchHits.isEmpty()) return
        showHit(state, (state.searchIndex - 1 + state.searchHits.size) % state.searchHits.size)
    }

    private fun showHit(state: DocState, index: Int) {
        state.searchIndex = index
        state.searchHits.getOrNull(index)?.let { goToPage(state, it.page) }
    }

    fun clearSearch(state: DocState) {
        state.searchJob?.cancel()
        state.searchQuery = ""
        state.searchHits = emptyList()
        state.searchIndex = -1
        state.searchProgress = 0
    }

    // ---------------------------------------------------------------- selectie

    fun copySelection(state: DocState) {
        val sel = state.selection ?: return
        viewModelScope.launch {
            val text = state.doc.text(sel.page, sel.start, sel.count)
            copyToClipboard(text)
            state.selection = null
        }
    }

    fun selectWholePage(state: DocState) {
        val page = state.selection?.page ?: state.currentPage
        viewModelScope.launch {
            val n = state.doc.charCount(page)
            if (n > 0) state.selection = Selection(page, 0, n - 1)
        }
    }

    private suspend fun copyToClipboard(text: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("NVict Reader", text))
        _events.send(UiEvent.Toast(ctx.getString(R.string.copied)))
    }

    // ---------------------------------------------------------------- delen/exporteren/afdrukken

    private fun exportDir(): File = File(ctx.cacheDir, "export").apply { mkdirs() }

    private fun baseName(state: DocState): String =
        state.doc.name.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "document" }

    fun shareDocument(state: DocState) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val f = File(exportDir(), state.doc.name.ifBlank { "document.pdf" })
                ctx.contentResolver.openInputStream(state.doc.uri)!!.use { input ->
                    FileOutputStream(f).use { input.copyTo(it) }
                }
                f
            }
            _events.send(UiEvent.ShareFile(file, "application/pdf", state.doc.name))
        }
    }

    /** Exporteert pagina's; [spec] is bijvoorbeeld "1-3,5" (1-gebaseerd). */
    fun exportPages(state: DocState, spec: String) {
        val pages = parsePageSpec(spec, state.doc.pageCount)
        if (pages.isEmpty()) {
            viewModelScope.launch { _events.send(UiEvent.Toast(ctx.getString(R.string.export_pages_hint))) }
            return
        }
        viewModelScope.launch {
            try {
                val file = File(exportDir(), "${baseName(state)} - pagina's ${spec.replace(" ", "")}.pdf")
                FileOutputStream(file).use { out -> state.doc.exportPages(ctx, pages, out) }
                _events.send(UiEvent.ShareFile(file, "application/pdf", file.name))
            } catch (e: Throwable) {
                _events.send(UiEvent.Toast(e.message ?: "Exporteren mislukt"))
            }
        }
    }

    fun sharePageImage(state: DocState) {
        val page = state.currentPage
        viewModelScope.launch {
            try {
                val size = state.doc.pageSizes[page]
                val w = 1654 // ~200 dpi voor A4
                val h = (w * size.ratio).toInt()
                val bmp = state.doc.render(page, w, h, darkPages = false)
                val file = withContext(Dispatchers.IO) {
                    File(exportDir(), "${baseName(state)} - pagina ${page + 1}.png").also { f ->
                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                _events.send(UiEvent.ShareFile(file, "image/png", file.name))
            } catch (e: Throwable) {
                _events.send(UiEvent.Toast(e.message ?: "Delen mislukt"))
            }
        }
    }

    fun print(state: DocState) {
        viewModelScope.launch { _events.send(UiEvent.Print(state.doc.uri, state.doc.name)) }
    }

    // ---------------------------------------------------------------- hulpfuncties

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
            }
        }
        name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    override fun onCleared() {
        _docs.value.forEach { runCatching { it.doc.close() } }
    }

    companion object {
        /** "1-3,5,8-" -> set van 0-gebaseerde pagina-indexen binnen [pageCount]. */
        fun parsePageSpec(spec: String, pageCount: Int): Set<Int> {
            val result = sortedSetOf<Int>()
            spec.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                val m = Regex("""^(\d*)\s*-\s*(\d*)$""").find(part)
                if (m != null && part.contains('-')) {
                    val a = m.groupValues[1].toIntOrNull() ?: 1
                    val b = m.groupValues[2].toIntOrNull() ?: pageCount
                    for (p in minOf(a, b)..maxOf(a, b)) if (p in 1..pageCount) result += p - 1
                } else {
                    part.toIntOrNull()?.let { if (it in 1..pageCount) result += it - 1 }
                }
            }
            return result
        }
    }
}
