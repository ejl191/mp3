package com.ejl191.driveplayer.ui

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.data.BookWithTracks
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = { TopAppBar(title = { Text("Library") }) },
        bottomBar = { MiniPlayer(ui, player, onOpenPlayer) },
        floatingActionButton = {
            if (signedIn == true) {
                ExtendedFloatingActionButton(
                    onClick = onBrowse,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add from Drive") },
                )
            }
        },
    ) { padding ->
        val list = books
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (signedIn == false) SignInCard(onSignIn, hasBooks = !list.isNullOrEmpty())
            when {
                list == null -> Unit
                list.isEmpty() && signedIn != false -> EmptyLibrary()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.book.id }) { book ->
                        BookRow(book, isCurrent = book.book.id == ui.bookId, onPlay = { onPlay(book.book.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun SignInCard(onSignIn: () -> Unit, hasBooks: Boolean) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect Google Drive", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasBooks) "Sign in to stream from Drive. Downloaded books still play offline."
                else "Sign in to browse the audio files in your Drive.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSignIn) { Text("Sign in with Google") }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your library is empty. Tap \"Add from Drive\" and pick a folder (played as one book) or a single audio file.",
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BookRow(item: BookWithTracks, isCurrent: Boolean, onPlay: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val book = item.book
    val tracks = item.tracks
    val index = item.currentIndex

    val status = buildString {
        if (book.currentFileId == null) {
            append("Not started")
        } else {
            if (tracks.size > 1) append("Chapter ${index + 1} of ${tracks.size} · ")
            append(formatTime(book.positionMs))
            tracks.getOrNull(index)?.durationMs?.takeIf { it > 0 }?.let { append(" / ${formatTime(it)}") }
        }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        leadingContent = {
            Icon(
                when {
                    isCurrent -> Icons.Default.GraphicEq
                    book.isFolder -> Icons.Default.Folder
                    else -> Icons.Default.AudioFile
                },
                contentDescription = null,
            )
        },
        headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(status)
                item.overallProgress?.let {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadButton(item)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
        },
    )
}
