package nl.nvict.reader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legere.pdfiumandroid.api.Bookmark
import nl.nvict.reader.R
import nl.nvict.reader.data.SignatureKind
import nl.nvict.reader.pdf.Annotation
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel) {
    val docs by vm.docs.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val darkPages by vm.darkPages.collectAsState()
    val active = docs.firstOrNull { it.doc.id == activeId }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.open(it) }
    }
    val pick = { picker.launch(arrayOf("application/pdf")) }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ReaderDialog?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var signaturesOpen by remember { mutableStateOf(false) }
    var padKind by remember { mutableStateOf<SignatureKind?>(null) }
    val savedSignatures by vm.signatures.state.collectAsState()
    val theme by vm.theme.collectAsState()

    if (settingsOpen) {
        SettingsDialog(
            theme = theme,
            onTheme = { vm.setTheme(it) },
            darkPages = darkPages,
            onDarkPages = { vm.setDarkPages(it) },
            onSignatures = {
                settingsOpen = false
                signaturesOpen = true
            },
            onDismiss = { settingsOpen = false },
        )
    }

    // Wachtwoorddialoog
    vm.passwordRequest?.let { req ->
        PasswordDialog(
            name = req.name,
            wrong = req.wrong,
            onConfirm = { vm.open(req.uri, password = it) },
            onDismiss = { vm.cancelPassword() },
        )
    }

    if (active == null) {
        val recent by vm.recent.collectAsState()
        StartScreen(
            recent = recent,
            onOpen = pick,
            onOpenRecent = { vm.open(it.uri, fromRecent = true) },
            onRemove = { vm.removeRecent(it.uri) },
            onClear = { vm.clearRecent() },
            onSignatures = { signaturesOpen = true },
            onSettings = { settingsOpen = true },
            onAbout = { dialog = ReaderDialog.About },
        )
        if (signaturesOpen) {
            SignaturesDialog(
                saved = savedSignatures,
                onDismiss = { signaturesOpen = false },
                onSave = { kind, bmp -> vm.saveSignature(kind, bmp) },
                onDelete = { vm.deleteSignature(it) },
            )
        }
        dialog?.let { ReaderDialogs(it, null, vm) { dialog = null } }
        return
    }

    val state = active
    LaunchedEffect(state.doc.id) { searchOpen = state.searchQuery.isNotBlank() }

    var editMenuOpen by remember { mutableStateOf(false) }
    var saveMenuOpen by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf<TextDialogRequest?>(null) }
    var closeConfirm by remember { mutableStateOf<DocState?>(null) }
    val requestClose: (DocState) -> Unit = { d -> if (d.dirty) closeConfirm = d else vm.close(d.doc.id) }

    // Tik op geselecteerde tekst of "Bewerken" in de balk opent de tekstdialoog
    LaunchedEffect(state.editingText) {
        state.editingText?.let {
            textDialog = TextDialogRequest(it)
            state.editingText = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (searchOpen) {
                    SearchBar(
                        state = state,
                        vm = vm,
                        onClose = {
                            vm.clearSearch(state)
                            searchOpen = false
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                state.doc.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        navigationIcon = {
                            Icon(
                                painterResource(R.drawable.reader_logo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.padding(horizontal = 12.dp).size(32.dp),
                            )
                        },
                        actions = {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Default.Search, stringResource(R.string.search))
                            }
                            // Bewerken: tekst, handtekening (markeren gaat via tekstselectie)
                            Box {
                                IconButton(onClick = { editMenuOpen = true }) {
                                    Icon(Icons.Default.Edit, stringResource(R.string.edit))
                                }
                                DropdownMenu(expanded = editMenuOpen, onDismissRequest = { editMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.add_text)) },
                                        leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                        onClick = {
                                            editMenuOpen = false
                                            textDialog = TextDialogRequest(null)
                                        },
                                    )
                                    listOf(
                                        SignatureKind.SIGNATURE to R.string.add_signature,
                                        SignatureKind.INITIALS to R.string.add_initials,
                                    ).forEach { (kind, label) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            leadingIcon = { Icon(Icons.Default.Draw, null) },
                                            onClick = {
                                                editMenuOpen = false
                                                val bmp = savedSignatures[kind]
                                                if (bmp != null) vm.addSignature(state, bmp, kind) else padKind = kind
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.manage_signatures)) },
                                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                                        onClick = {
                                            editMenuOpen = false
                                            signaturesOpen = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.highlight_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.BorderColor, null) },
                                        enabled = false,
                                        onClick = {},
                                    )
                                }
                            }
                            if (state.canUndo) {
                                IconButton(onClick = { vm.undo(state) }) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
                                }
                            }
                            if (state.dirty) {
                                Box {
                                    IconButton(onClick = { saveMenuOpen = true }, enabled = !state.saving) {
                                        Icon(Icons.Default.Save, stringResource(R.string.save), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    DropdownMenu(expanded = saveMenuOpen, onDismissRequest = { saveMenuOpen = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.save)) }, onClick = {
                                            saveMenuOpen = false
                                            vm.save(state)
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.save_as)) }, onClick = {
                                            saveMenuOpen = false
                                            vm.requestSaveAs(state)
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.share_edited)) }, onClick = {
                                            saveMenuOpen = false
                                            vm.shareEdited(state)
                                        })
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    @Composable
                                    fun item(label: Int, action: () -> Unit) {
                                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                            menuOpen = false
                                            action()
                                        })
                                    }
                                    item(R.string.open_file) { pick() }
                                    item(R.string.goto_page) { dialog = ReaderDialog.GoToPage }
                                    item(R.string.outline) { dialog = ReaderDialog.Outline }
                                    item(R.string.fit_width) { state.scale = 1f }
                                    item(R.string.settings) { settingsOpen = true }
                                    HorizontalDivider()
                                    item(R.string.share_document) { vm.shareDocument(state) }
                                    item(R.string.export_pages) { dialog = ReaderDialog.ExportPages }
                                    item(R.string.share_page_image) { vm.sharePageImage(state) }
                                    item(R.string.print) { vm.print(state) }
                                    HorizontalDivider()
                                    item(R.string.document_info) { dialog = ReaderDialog.Info }
                                    item(R.string.about) { dialog = ReaderDialog.About }
                                    item(R.string.close_document) { requestClose(state) }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
                state.form?.let { form ->
                    if (!state.formBannerDismissed) {
                        FormBanner(
                            text = stringResource(if (form.isEmpty) R.string.form_xfa else R.string.form_detected),
                            onClose = { state.formBannerDismissed = true },
                        )
                    }
                }
                if (docs.size > 1) {
                    DocumentTabs(docs = docs, active = state, onSelect = { vm.setActive(it) }, onClose = { id ->
                        docs.firstOrNull { it.doc.id == id }?.let { requestClose(it) }
                    })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PdfPagesView(state = state, darkPages = darkPages, vm = vm)
            if (state.saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }

            // Paginateller
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clickable { dialog = ReaderDialog.GoToPage },
            ) {
                Text(
                    stringResource(R.string.page_of, state.currentPage + 1, state.doc.pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }

    dialog?.let { ReaderDialogs(it, state, vm) { dialog = null } }

    textDialog?.let { req ->
        val existing = req.id?.let { id -> state.annotations.firstOrNull { it.id == id } as? Annotation.Text }
        TextAnnotationDialog(
            initial = existing,
            onDismiss = { textDialog = null },
            onConfirm = { text, size, color ->
                if (existing != null) vm.updateText(state, existing.id, text, size, color)
                else vm.addText(state, text, size, color)
                textDialog = null
            },
        )
    }

    padKind?.let { kind ->
        SignaturePadDialog(
            kind = kind,
            onDismiss = { padKind = null },
            onDone = { bmp ->
                padKind = null
                vm.saveSignature(kind, bmp)
                vm.addSignature(state, bmp, kind)
            },
        )
    }

    if (signaturesOpen) {
        SignaturesDialog(
            saved = savedSignatures,
            onDismiss = { signaturesOpen = false },
            onSave = { kind, bmp -> vm.saveSignature(kind, bmp) },
            onDelete = { vm.deleteSignature(it) },
            onPlace = { kind, bmp ->
                signaturesOpen = false
                vm.addSignature(state, bmp, kind)
            },
        )
    }

    closeConfirm?.let { d ->
        AlertDialog(
            onDismissRequest = { closeConfirm = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text)) },
            confirmButton = {
                TextButton(onClick = {
                    closeConfirm = null
                    vm.setActive(d.doc.id)
                    vm.save(d, closeAfter = true)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { closeConfirm = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        closeConfirm = null
                        vm.close(d.doc.id)
                    }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

/** Dialoog voor nieuwe tekst ([id] == null) of het bewerken van bestaande tekst. */
data class TextDialogRequest(val id: Long?)

@Composable
private fun FormBanner(text: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun DocumentTabs(
    docs: List<DocState>,
    active: DocState,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
) {
    val index = docs.indexOf(active).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = index,
        edgePadding = 4.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        docs.forEach { d ->
            Tab(
                selected = d == active,
                onClick = { onSelect(d.doc.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.doc.name.removeSuffix(".pdf").removeSuffix(".PDF"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(140.dp),
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_document),
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(18.dp)
                                .clickable { onClose(d.doc.id) },
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(state: DocState, vm: ReaderViewModel, onClose: () -> Unit) {
    var text by rememberSaveable(state.doc.id) { mutableStateOf(state.searchQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    TopAppBar(
        title = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (text == state.searchQuery && state.searchHits.isNotEmpty()) vm.nextHit(state) else vm.search(state, text)
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
        },
        actions = {
            when {
                state.searchProgress > 0 -> Box(Modifier.padding(horizontal = 12.dp)) {
                    CircularProgressIndicator(
                        progress = { state.searchProgress / state.doc.pageCount.toFloat() },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
                state.searchHits.isNotEmpty() -> {
                    Text(
                        "${state.searchIndex + 1}/${state.searchHits.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = { vm.prevHit(state) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                    IconButton(onClick = { vm.nextHit(state) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ------------------------------------------------------------------ dialogen

sealed class ReaderDialog {
    data object GoToPage : ReaderDialog()
    data object Outline : ReaderDialog()
    data object ExportPages : ReaderDialog()
    data object Info : ReaderDialog()
    data object About : ReaderDialog()
}

@Composable
private fun ReaderDialogs(which: ReaderDialog, state: DocState?, vm: ReaderViewModel, onDismiss: () -> Unit) {
    when (which) {
        ReaderDialog.About -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
            icon = { Icon(painterResource(R.drawable.reader_logo), null, tint = Color.Unspecified, modifier = Modifier.size(64.dp)) },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.about_text)) },
        )
        ReaderDialog.GoToPage -> if (state != null) {
            InputDialog(
                title = stringResource(R.string.goto_page),
                initial = (state.currentPage + 1).toString(),
                hint = "1 - ${state.doc.pageCount}",
                keyboard = KeyboardType.Number,
                onDismiss = onDismiss,
            ) { value ->
                value.trim().toIntOrNull()?.let { vm.goToPage(state, it - 1) }
                onDismiss()
            }
        }
        ReaderDialog.ExportPages -> if (state != null) {
            InputDialog(
                title = stringResource(R.string.export_pages),
                initial = (state.currentPage + 1).toString(),
                hint = stringResource(R.string.export_pages_hint),
                keyboard = KeyboardType.Text,
                onDismiss = onDismiss,
            ) { value ->
                vm.exportPages(state, value)
                onDismiss()
            }
        }
        ReaderDialog.Outline -> if (state != null) {
            val outline by produceState<List<Bookmark>?>(null, state.doc.id) {
                value = runCatching { state.doc.outline() }.getOrDefault(emptyList())
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
                title = { Text(stringResource(R.string.outline)) },
                text = {
                    val list = outline
                    when {
                        list == null -> CircularProgressIndicator()
                        list.isEmpty() -> Text(stringResource(R.string.outline_none))
                        else -> {
                            val flat = remember(list) { flatten(list) }
                            LazyColumn {
                                items(flat) { (depth, bm) ->
                                    Text(
                                        bm.title ?: "",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vm.goToPage(state, bm.pageIdx.toInt())
                                                onDismiss()
                                            }
                                            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        ReaderDialog.Info -> if (state != null) {
            val meta by produceState<io.legere.pdfiumandroid.api.Meta?>(null, state.doc.id) {
                value = runCatching { state.doc.meta() }.getOrNull()
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.document_info)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Bestand", state.doc.name)
                        InfoRow("Pagina's", state.doc.pageCount.toString())
                        val s = state.doc.pageSizes[state.currentPage]
                        InfoRow("Formaat", "%.0f × %.0f mm".format(s.widthPt * 25.4 / 72, s.heightPt * 25.4 / 72))
                        meta?.let { m ->
                            m.title?.takeIf { it.isNotBlank() }?.let { InfoRow("Titel", it) }
                            m.author?.takeIf { it.isNotBlank() }?.let { InfoRow("Auteur", it) }
                            m.subject?.takeIf { it.isNotBlank() }?.let { InfoRow("Onderwerp", it) }
                            m.creator?.takeIf { it.isNotBlank() }?.let { InfoRow("Gemaakt met", it) }
                            m.producer?.takeIf { it.isNotBlank() }?.let { InfoRow("Producer", it) }
                            m.creationDate?.takeIf { it.isNotBlank() }?.let { InfoRow("Aangemaakt", pdfDate(it)) }
                            m.modDate?.takeIf { it.isNotBlank() }?.let { InfoRow("Gewijzigd", pdfDate(it)) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "D:20240115093000+01'00'" -> "15-01-2024 09:30" */
private fun pdfDate(raw: String): String {
    val d = raw.removePrefix("D:")
    if (d.length < 8) return raw
    val year = d.substring(0, 4)
    val month = d.substring(4, 6)
    val day = d.substring(6, 8)
    val time = if (d.length >= 12) " ${d.substring(8, 10)}:${d.substring(10, 12)}" else ""
    return "$day-$month-$year$time"
}

private fun flatten(list: List<Bookmark>, depth: Int = 0): List<Pair<Int, Bookmark>> =
    list.flatMap { listOf(depth to it) + flatten(it.children, depth + 1) }

@Composable
private fun InputDialog(
    title: String,
    initial: String,
    hint: String,
    keyboard: KeyboardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(hint) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordDialog(name: String, wrong: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    isError = wrong,
                    label = { Text(stringResource(if (wrong) R.string.password_wrong else R.string.password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                    modifier = Modifier.padding(top = 8.dp).focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
