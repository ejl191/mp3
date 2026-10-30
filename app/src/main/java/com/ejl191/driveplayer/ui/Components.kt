package com.ejl191.driveplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ejl191.driveplayer.data.BookWithTracks
import com.ejl191.driveplayer.playback.Downloads
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(ui: PlayerUi, player: Player?, onOpen: () -> Unit) {
    if (ui.bookId == null || player == null) return
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            if (ui.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        ui.bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { if (ui.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ui.isPlaying) "Pause" else "Play",
                    )
                }
            }
        }
    }
}

/** Download / progress / downloaded toggle for one book. */
@Composable
fun DownloadButton(book: BookWithTracks) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookId = book.book.id
    val work by remember(bookId) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(Downloads.workName(bookId))
    }.observeAsState()
    val running = work?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true
    val tracks = book.tracks
    val done = tracks.count { it.downloaded }
    var confirmRemove by remember { mutableStateOf(false) }

    when {
        book.allDownloaded -> IconButton(onClick = { confirmRemove = true }) {
            Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded. Tap to remove the download")
        }
        running -> IconButton(onClick = { Downloads.cancel(context, bookId) }) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (tracks.isEmpty()) 0f else done.toFloat() / tracks.size },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
        else -> IconButton(onClick = { Downloads.start(context, bookId) }) {
            Icon(Icons.Default.Download, contentDescription = "Download for offline listening")
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove download?") },
            text = { Text("\"${book.book.title}\" will stream from Drive again. Your place is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch { Downloads.remove(context, bookId) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
