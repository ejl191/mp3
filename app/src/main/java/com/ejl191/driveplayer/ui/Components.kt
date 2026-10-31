package com.ejl191.driveplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ejl191.driveplayer.data.BookWithTracks
import com.ejl191.driveplayer.playback.Downloads
import kotlinx.coroutines.launch

/** Bottom area that extends the chrome behind the navigation bar. */
@Composable
fun BottomDock(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Retro.Chrome).navigationBarsPadding(), content = content)
}

/** Collapsed one-line player, like an old player's "window shade" mode. */
@Composable
fun MiniPlayer(ui: PlayerUi, player: Player?, onOpen: () -> Unit) {
    if (ui.bookId == null || player == null) return
    Row(
        Modifier.fillMaxWidth().background(Retro.Chrome).bevel().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroIconButton(
            if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (ui.isPlaying) "Pause" else "Play",
            onClick = { if (ui.isPlaying) player.pause() else player.play() },
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(4.dp))
        LcdBox(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeText("${ui.title}  ***  ${ui.bookTitle}  ***", Modifier.weight(1f), style = Retro.small)
                Text(" ${formatTime(ui.positionMs)}", style = Retro.small, color = Retro.LcdGreen)
            }
        }
        Spacer(Modifier.width(4.dp))
        RetroIconButton(Icons.Default.ExpandLess, "Open player", onOpen, iconSize = 18.dp)
    }
}

enum class DownloadState { NONE, RUNNING, DONE }

@Composable
fun rememberDownloadState(book: BookWithTracks): DownloadState {
    val context = LocalContext.current
    val work by remember(book.book.id) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(Downloads.workName(book.book.id))
    }.observeAsState()
    val running = work?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true
    return when {
        book.allDownloaded -> DownloadState.DONE
        running -> DownloadState.RUNNING
        else -> DownloadState.NONE
    }
}

/** "DL" button: starts a download, shows progress (tap to cancel), or offers to remove a finished one. */
@Composable
fun DownloadButton(book: BookWithTracks, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookId = book.book.id
    val state = rememberDownloadState(book)
    val done = book.tracks.count { it.downloaded }
    var confirmRemove by remember { mutableStateOf(false) }

    when (state) {
        DownloadState.DONE -> RetroTextButton("SAVED", { confirmRemove = true }, modifier, active = true)
        DownloadState.RUNNING -> RetroTextButton("DL $done/${book.tracks.size}", { Downloads.cancel(context, bookId) }, modifier, active = true)
        DownloadState.NONE -> RetroTextButton("DL", { Downloads.start(context, bookId) }, modifier)
    }

    if (confirmRemove) {
        RemoveDownloadDialog(book.book.title, onDismiss = { confirmRemove = false }) {
            confirmRemove = false
            scope.launch { Downloads.remove(context, bookId) }
        }
    }
}

@Composable
fun RemoveDownloadDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = { Text("\"$title\" will stream from Drive again. Your place is kept.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("REMOVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
