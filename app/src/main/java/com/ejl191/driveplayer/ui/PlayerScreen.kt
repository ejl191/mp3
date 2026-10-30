package com.ejl191.driveplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.data.Bookmark
import com.ejl191.driveplayer.data.BookWithTracks
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(ui: PlayerUi, player: Player?, onBack: () -> Unit) {
    val app = LocalContext.current.app
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val bookId = ui.bookId
    val book by remember(bookId) { bookId?.let { dao.bookFlow(it) } ?: flowOf(null) }
        .collectAsState(initial = null)
    val bookmarks by remember(bookId) { bookId?.let { dao.bookmarks(it) } ?: emptyFlow() }
        .collectAsState(initial = emptyList())
    var addingBookmark by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(ui.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { book?.let { DownloadButton(it) } },
            )
        },
    ) { padding ->
        if (player == null || bookId == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing playing. Pick a book from your library.")
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ui.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ui.count > 1) {
                    Text(
                        "Chapter ${ui.index + 1} of ${ui.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.error?.let {
                    Text("Playback error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                SeekBar(ui, player)
                Controls(ui, player)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeedButton(ui.speed) { player.setPlaybackSpeed(it) }
                    IconButton(onClick = { addingBookmark = true }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Chapters") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks (${bookmarks.size})") })
            }
            val current = book
            if (tab == 0) {
                ChapterList(current, ui) { index -> player.seekTo(index, 0); player.play() }
            } else {
                BookmarkList(
                    bookmarks,
                    current,
                    onJump = { index, position -> player.seekTo(index, position); player.play() },
                    onDelete = { scope.launch { dao.deleteBookmark(it) } },
                )
            }
        }
    }

    if (addingBookmark && player != null && bookId != null && ui.fileId != null) {
        val fileId = ui.fileId
        val position = remember { player.currentPosition }
        val default = buildString {
            if (ui.count > 1) append("Ch. ${ui.index + 1} · ")
            append(formatTime(position))
        }
        var label by remember { mutableStateOf(default) }
        AlertDialog(
            onDismissRequest = { addingBookmark = false },
            title = { Text("Add bookmark") },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    addingBookmark = false
                    val bookmark = Bookmark(bookId = bookId, fileId = fileId, positionMs = position, label = label.ifBlank { default })
                    app.scope.launch { dao.insertBookmark(bookmark) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addingBookmark = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeekBar(ui: PlayerUi, player: Player) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs
    val shown = dragging?.let { (it * duration).toLong() } ?: ui.positionMs
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Slider(
            value = dragging ?: if (duration > 0) (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown), style = MaterialTheme.typography.bodySmall)
            Text(if (duration > 0) "-" + formatTime(duration - shown) else "--:--", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Controls(ui: PlayerUi, player: Player) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.seekToPreviousMediaItem() }, enabled = ui.index > 0) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter")
        }
        IconButton(onClick = { player.seekBack() }) {
            Icon(Icons.Default.Replay30, contentDescription = "Back 30 seconds", Modifier.size(36.dp))
        }
        FilledIconButton(
            onClick = { if (ui.isPlaying) player.pause() else player.play() },
            modifier = Modifier.size(72.dp),
        ) {
            if (ui.isBuffering && !ui.isPlaying) {
                CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (ui.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { player.seekForward() }) {
            Icon(Icons.Default.Forward30, contentDescription = "Forward 30 seconds", Modifier.size(36.dp))
        }
        IconButton(onClick = { player.seekToNextMediaItem() }, enabled = ui.index < ui.count - 1) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next chapter")
        }
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("${formatSpeed(speed)}×") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEEDS.forEach { s ->
                DropdownMenuItem(text = { Text("${formatSpeed(s)}×") }, onClick = {
                    open = false
                    onSpeed(s)
                })
            }
        }
    }
}

private fun formatSpeed(s: Float) = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()

@Composable
private fun ChapterList(book: BookWithTracks?, ui: PlayerUi, onSelect: (Int) -> Unit) {
    val tracks = book?.tracks.orEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.fileId }) { index, track ->
            val current = index == ui.index
            ListItem(
                modifier = Modifier.clickable { onSelect(index) },
                leadingContent = {
                    if (current) Icon(Icons.Default.GraphicEq, contentDescription = "Now playing", tint = MaterialTheme.colorScheme.primary)
                    else Text("${index + 1}", Modifier.size(24.dp), textAlign = TextAlign.Center)
                },
                headlineContent = {
                    Text(
                        track.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                trailingContent = {
                    if (track.durationMs > 0) Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall)
                },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    book: BookWithTracks?,
    onJump: (Int, Long) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Text(
            "No bookmarks yet. Tap the bookmark button to save the current spot.",
            Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val tracks = book?.tracks.orEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(bookmarks, key = { _, b -> b.id }) { _, bookmark ->
            val index = tracks.indexOfFirst { it.fileId == bookmark.fileId }
            ListItem(
                modifier = Modifier.clickable(enabled = index >= 0) { onJump(index, bookmark.positionMs) },
                headlineContent = { Text(bookmark.label) },
                supportingContent = {
                    val chapter = tracks.getOrNull(index)?.title
                    Text(listOfNotNull(chapter, formatTime(bookmark.positionMs)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(bookmark) }) { Icon(Icons.Default.Delete, contentDescription = "Delete bookmark") }
                },
            )
        }
    }
}
