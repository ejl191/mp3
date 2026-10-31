package com.ejl191.driveplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.data.BookWithTracks
import com.ejl191.driveplayer.data.Bookmark
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Composable
fun PlayerScreen(ui: PlayerUi, player: Player?, onBack: () -> Unit, onEject: () -> Unit) {
    val app = LocalContext.current.app
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val bookId = ui.bookId
    val book by remember(bookId) { bookId?.let { dao.bookFlow(it) } ?: flowOf(null) }
        .collectAsState(initial = null)
    val bookmarks by remember(bookId) { bookId?.let { dao.bookmarks(it) } ?: emptyFlow() }
        .collectAsState(initial = emptyList())
    var addingBookmark by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Retro.Chrome)) {
        RetroTitleBar("Drive Player", onBack = onBack)
        if (player == null || bookId == null) {
            RetroPanel(Modifier.fillMaxWidth().padding(4.dp)) {
                LcdBox(Modifier.fillMaxWidth()) {
                    MarqueeText("NO BOOK LOADED  ***  PRESS EJECT TO OPEN ONE FROM DRIVE  ***")
                }
                Spacer(Modifier.height(6.dp))
                RetroIconButton(Icons.Default.Eject, "Open from Drive", onEject)
            }
            return@Column
        }

        RetroPanel(Modifier.fillMaxWidth().padding(4.dp)) {
            MainDisplay(ui, book)
            RetroSeekBarFor(ui, player)
            Transport(ui, player, onEject)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SpeedButton(ui.speed) { player.setPlaybackSpeed(it) }
                RetroTextButton("+BMK", { addingBookmark = true })
                book?.let { DownloadButton(it) }
                Spacer(Modifier.weight(1f))
                RetroTextButton("CH", { tab = 0 }, active = tab == 0)
                RetroTextButton("BM", { tab = 1 }, active = tab == 1)
            }
        }

        // Playlist editor
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp)
                .navigationBarsPadding()
                .background(Retro.Chrome)
                .bevel()
                .padding(4.dp),
        ) {
            StripeBar(if (tab == 0) "Chapters" else "Bookmarks (${bookmarks.size})", Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().background(Retro.Lcd).bevel(raised = false)) {
                if (tab == 0) {
                    ChapterList(book, ui) { index -> player.seekTo(index, 0); player.play() }
                } else {
                    BookmarkList(
                        bookmarks,
                        book,
                        onJump = { index, position -> player.seekTo(index, position); player.play() },
                        onDelete = { scope.launch { dao.deleteBookmark(it) } },
                    )
                }
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
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { addingBookmark = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun MainDisplay(ui: PlayerUi, book: BookWithTracks?) {
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    val time = if (showRemaining && ui.durationMs > 0) "-" + formatTime(ui.durationMs - ui.positionMs) else formatTime(ui.positionMs)
    val offline = book?.tracks?.firstOrNull { it.fileId == ui.fileId }?.downloaded == true
    val status = when {
        ui.error != null -> "ERROR: ${ui.error}"
        ui.isBuffering -> "BUFFERING..."
        ui.isPlaying -> "PLAYING"
        else -> "PAUSED"
    }

    Row(Modifier.fillMaxWidth().height(96.dp)) {
        LcdBox(Modifier.width(150.dp).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ui.isPlaying) "▶" else "❚❚",
                        style = Retro.small,
                        color = Retro.LcdGreen,
                        modifier = Modifier.width(18.dp),
                    )
                    LcdTime(time, Modifier.clickable { showRemaining = !showRemaining })
                }
                Visualizer(ui.isPlaying, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LcdBox(Modifier.fillMaxWidth()) {
                val number = if (ui.count > 1) "${ui.index + 1}. " else ""
                MarqueeText("$number${ui.bookTitle} - ${ui.title} (${formatTime(ui.durationMs)})  ***  ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoBox("${formatSpeed(ui.speed)}x")
                if (ui.count > 1) InfoBox("CH ${ui.index + 1}/${ui.count}")
                InfoBox(if (offline) "LOCAL" else "STREAM")
            }
            LcdBox(Modifier.fillMaxWidth().weight(1f)) {
                MarqueeText(status, color = if (ui.error != null) Retro.Error else Retro.LcdGreen, style = Retro.small)
            }
        }
    }
}

@Composable
private fun RetroSeekBarFor(ui: PlayerUi, player: Player) {
    var preview by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs
    val fraction = preview ?: if (duration > 0) ui.positionMs.toFloat() / duration else 0f
    Column(Modifier.padding(vertical = 6.dp)) {
        RetroSeekBar(
            fraction = fraction,
            enabled = duration > 0,
            onPreview = { preview = it },
            onSeek = { player.seekTo((it * duration).toLong()) },
        )
        preview?.let {
            Text("SEEK TO ${formatTime((it * duration).toLong())}", style = Retro.small, color = Retro.TitleText)
        }
    }
}

@Composable
private fun Transport(ui: PlayerUi, player: Player, onEject: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val m = Modifier.weight(1f)
        RetroIconButton(Icons.Default.SkipPrevious, "Previous chapter", { player.seekToPreviousMediaItem() }, m, enabled = ui.index > 0)
        RetroIconButton(Icons.Default.Replay30, "Back 30 seconds", { player.seekBack() }, m)
        RetroIconButton(Icons.Default.PlayArrow, "Play", { player.play() }, m, active = ui.isPlaying)
        RetroIconButton(Icons.Default.Pause, "Pause", { player.pause() }, m, active = !ui.isPlaying)
        RetroIconButton(Icons.Default.Forward30, "Forward 30 seconds", { player.seekForward() }, m)
        RetroIconButton(Icons.Default.SkipNext, "Next chapter", { player.seekToNextMediaItem() }, m, enabled = ui.index < ui.count - 1)
        RetroIconButton(Icons.Default.Eject, "Open from Drive", onEject, m)
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RetroTextButton("SPD ${formatSpeed(speed)}x", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEEDS.forEach { s ->
                DropdownMenuItem(text = { Text("${formatSpeed(s)}x") }, onClick = {
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
            PlaylistRow(
                text = track.title,
                number = index + 1,
                trailing = track.durationMs.takeIf { it > 0 }?.let(::formatTime),
                current = index == ui.index,
                onClick = { onSelect(index) },
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
            "NO BOOKMARKS. PRESS +BMK TO SAVE THIS SPOT.",
            Modifier.padding(8.dp),
            style = Retro.small,
            color = Retro.PlDim,
        )
        return
    }
    val tracks = book?.tracks.orEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(bookmarks, key = { _, b -> b.id }) { i, bookmark ->
            val index = tracks.indexOfFirst { it.fileId == bookmark.fileId }
            PlaylistRow(
                text = bookmark.label,
                number = i + 1,
                trailing = formatTime(bookmark.positionMs),
                secondary = tracks.getOrNull(index)?.title,
                onClick = if (index >= 0) ({ onJump(index, bookmark.positionMs) }) else null,
            ) {
                Spacer(Modifier.width(6.dp))
                RetroTextButton("DEL", { onDelete(bookmark) })
            }
        }
    }
}
