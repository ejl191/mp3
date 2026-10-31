package com.ejl191.driveplayer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.data.BookWithTracks
import com.ejl191.driveplayer.playback.Downloads
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    signedIn: Boolean?,
    onSignIn: () -> Unit,
    ui: PlayerUi,
    player: Player?,
    onBrowse: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val app = LocalContext.current.app
    val books by remember { app.db.dao().library() }.collectAsState(initial = null)

    Scaffold(
        containerColor = Retro.Chrome,
        topBar = { RetroTitleBar("Drive Player - Library") },
        bottomBar = {
            BottomDock {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroTextButton("+ ADD", onBrowse, enabled = signedIn == true)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${books?.size ?: 0} BOOKS",
                        Modifier.padding(top = 6.dp),
                        style = Retro.small,
                        color = Retro.Label,
                    )
                }
                MiniPlayer(ui, player, onOpenPlayer)
            }
        },
    ) { padding ->
        val list = books
        Column(Modifier.padding(padding).fillMaxSize().padding(4.dp)) {
            if (signedIn == false) {
                SignInCard(onSignIn, hasBooks = !list.isNullOrEmpty())
                Spacer(Modifier.height(4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Retro.Lcd).bevel(raised = false)) {
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text(
                        "LIBRARY EMPTY.\n\nPRESS + ADD AND PICK A DRIVE FOLDER (PLAYED AS ONE BOOK) OR A SINGLE AUDIO FILE.",
                        Modifier.padding(8.dp),
                        style = Retro.small,
                        color = Retro.PlDim,
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { _, b -> b.book.id }) { i, book ->
                            BookRow(i + 1, book, isCurrent = book.book.id == ui.bookId, onPlay = { onPlay(book.book.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignInCard(onSignIn: () -> Unit, hasBooks: Boolean) {
    RetroPanel(Modifier.fillMaxWidth()) {
        StripeBar("Google Drive", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        LcdBox(Modifier.fillMaxWidth()) {
            Text(
                if (hasBooks) "NOT SIGNED IN. DOWNLOADED BOOKS STILL PLAY OFFLINE."
                else "NOT SIGNED IN. SIGN IN TO BROWSE AUDIO IN YOUR DRIVE.",
                style = Retro.small,
                color = Retro.LcdGreen,
            )
        }
        Spacer(Modifier.height(6.dp))
        RetroTextButton("SIGN IN WITH GOOGLE", onSignIn)
    }
}

@Composable
private fun BookRow(number: Int, item: BookWithTracks, isCurrent: Boolean, onPlay: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val book = item.book
    val tracks = item.tracks
    val index = item.currentIndex
    val download = rememberDownloadState(item)

    val total = tracks.takeIf { t -> t.isNotEmpty() && t.all { it.durationMs > 0 } }?.sumOf { it.durationMs }
    val status = buildString {
        if (book.currentFileId == null) {
            append("NOT STARTED")
        } else {
            if (tracks.size > 1) append("CH ${index + 1}/${tracks.size} ")
            append("@ ${formatTime(book.positionMs)}")
            item.overallProgress?.let { append("  ${(it * 100).toInt()}%") }
        }
        when (download) {
            DownloadState.DONE -> append("  [SAVED]")
            DownloadState.RUNNING -> append("  [DL ${tracks.count { it.downloaded }}/${tracks.size}]")
            DownloadState.NONE -> Unit
        }
    }

    PlaylistRow(
        text = book.title,
        number = number,
        trailing = total?.let(::formatTime),
        secondary = status,
        current = isCurrent,
        onClick = onPlay,
    ) {
        Spacer(Modifier.width(6.dp))
        Box {
            RetroTextButton("...", { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                when (download) {
                    DownloadState.NONE -> DropdownMenuItem(text = { Text("Download for offline") }, onClick = {
                        menu = false
                        Downloads.start(context, book.id)
                    })
                    DownloadState.RUNNING -> DropdownMenuItem(text = { Text("Cancel download") }, onClick = {
                        menu = false
                        Downloads.cancel(context, book.id)
                    })
                    DownloadState.DONE -> DropdownMenuItem(text = { Text("Remove download") }, onClick = {
                        menu = false
                        scope.launch { Downloads.remove(context, book.id) }
                    })
                }
                if (book.isFolder) {
                    DropdownMenuItem(text = { Text("Refresh from Drive") }, onClick = {
                        menu = false
                        scope.launch {
                            val msg = runCatching { app.library.refresh(book.id) }
                                .fold({ "Updated" }, { "Couldn't refresh: ${it.message}" })
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                DropdownMenuItem(text = { Text("Remove from library") }, onClick = {
                    menu = false
                    scope.launch { app.library.remove(book.id) }
                })
            }
        }
    }
}
