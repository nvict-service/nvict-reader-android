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
import androidx.compose.material.icons.filled.Close
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
import nl.nvict.reader.data.RecentFile
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
            onAbout = { dialog = ReaderDialog.About },
        )
        dialog?.let { ReaderDialogs(it, null, vm) { dialog = null } }
        return
    }

    val state = active
    LaunchedEffect(state.doc.id) { searchOpen = state.searchQuery.isNotBlank() }

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
                            IconButton(onClick = pick) {
                                Icon(Icons.Default.FolderOpen, stringResource(R.string.open_file))
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
                                    item(R.string.goto_page) { dialog = ReaderDialog.GoToPage }
                                    item(R.string.outline) { dialog = ReaderDialog.Outline }
                                    item(R.string.fit_width) { state.scale = 1f }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.night_pages)) },
                                        trailingIcon = { Checkbox(checked = darkPages, onCheckedChange = null) },
                                        onClick = {
                                            menuOpen = false
                                            vm.setDarkPages(!darkPages)
                                        },
                                    )
                                    HorizontalDivider()
                                    item(R.string.share_document) { vm.shareDocument(state) }
                                    item(R.string.export_pages) { dialog = ReaderDialog.ExportPages }
                                    item(R.string.share_page_image) { vm.sharePageImage(state) }
                                    item(R.string.print) { vm.print(state) }
                                    HorizontalDivider()
                                    item(R.string.document_info) { dialog = ReaderDialog.Info }
                                    item(R.string.about) { dialog = ReaderDialog.About }
                                    item(R.string.close_document) { vm.close(state.doc.id) }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
                if (docs.size > 1) {
                    DocumentTabs(docs = docs, active = state, onSelect = { vm.setActive(it) }, onClose = { vm.close(it) })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PdfPagesView(state = state, darkPages = darkPages, vm = vm)

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

// ------------------------------------------------------------------ startscherm

@Composable
private fun StartScreen(
    recent: List<RecentFile>,
    onOpen: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onRemove: (RecentFile) -> Unit,
    onClear: () -> Unit,
    onAbout: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Icon(
                    painterResource(R.drawable.nvict_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.height(36.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAbout) { Icon(Icons.Default.Info, stringResource(R.string.about)) }
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp),
            )
            Button(onClick = onOpen, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_file))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp)) {
                Text(
                    stringResource(R.string.recent_files),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (recent.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear_recent)) }
                }
            }
            if (recent.isEmpty()) {
                Text(
                    stringResource(R.string.no_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    items(recent, key = { it.uri.toString() }) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecent(file) }
                                .padding(vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                null,
                                tint = NVictColors.Error,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${df.format(Date(file.openedAt))} · pagina ${file.lastPage + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemove(file) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.remove_from_recent))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
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
