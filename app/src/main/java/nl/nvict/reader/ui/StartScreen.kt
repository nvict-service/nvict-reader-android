package nl.nvict.reader.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.nvict.reader.R
import nl.nvict.reader.data.FileThumb
import nl.nvict.reader.data.RecentFile
import nl.nvict.reader.data.Thumbnails

private const val PREVIEW_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    recent: List<RecentFile>,
    onOpen: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onRemove: (RecentFile) -> Unit,
    onClear: () -> Unit,
    onSignatures: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = showAll) { showAll = false }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showAll) {
                        IconButton(onClick = { showAll = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    } else {
                        Icon(
                            painterResource(R.drawable.reader_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp).size(36.dp),
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(if (showAll) R.string.recent_files else R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onSignatures) {
                        Icon(Icons.Default.Draw, stringResource(R.string.signatures_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (recent.isNotEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.clear_recent)) }, onClick = {
                                    menuOpen = false
                                    onClear()
                                    showAll = false
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                                menuOpen = false
                                onSettings()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = {
                                menuOpen = false
                                onAbout()
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (showAll) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(recent, key = { it.uri.toString() }) { file ->
                    RecentTile(file, onOpen = { onOpenRecent(file) }, onRemove = { onRemove(file) })
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // Grote "PDF openen"-knop, zoals in Fill & Sign
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(140.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.open_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(56.dp))

            // Recente bestanden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(stringResource(R.string.recent_files), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (recent.size > PREVIEW_COUNT) {
                    TextButton(onClick = { showAll = true }) {
                        Text(stringResource(R.string.show_all), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (recent.isEmpty()) {
                Text(
                    stringResource(R.string.no_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    recent.take(PREVIEW_COUNT).forEach { file ->
                        Box(Modifier.weight(1f)) {
                            RecentTile(file, onOpen = { onOpenRecent(file) }, onRemove = { onRemove(file) })
                        }
                    }
                    // Lege plekken opvullen zodat tegels altijd even breed zijn
                    repeat(PREVIEW_COUNT - recent.size.coerceAtMost(PREVIEW_COUNT)) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Tegel met miniatuur van de eerste pagina, bestandsnaam, tijdstip en grootte. Lang indrukken = menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTile(file: RecentFile, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<FileThumb?>(null, file.uri) { value = Thumbnails.load(context, file.uri) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            val bmp = thumb?.bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            } else {
                Icon(Icons.Default.PictureAsPdf, null, tint = NVictColors.Error, modifier = Modifier.size(40.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_recent)) }, onClick = {
                    menuOpen = false
                    onRemove()
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            file.name.removeSuffix(".pdf").removeSuffix(".PDF"),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val ago = DateUtils.getRelativeTimeSpanString(
            file.openedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
        val size = thumb?.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
        Text(
            listOfNotNull(ago, size).joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
