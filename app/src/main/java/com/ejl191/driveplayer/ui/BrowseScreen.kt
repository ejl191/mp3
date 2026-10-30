package com.ejl191.driveplayer.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.drive.DriveFile
import com.ejl191.driveplayer.drive.FOLDER_MIME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface Place {
        val title: String
    }
    data class Folder(val id: String, override val title: String) : Place
    data object Shared : Place {
        override val title = "Shared with me"
    }
    data class Search(val query: String) : Place {
        override val title get() = "\"$query\""
    }

    private val app = application.app
    private val myDrive = Folder("root", "My Drive")

    var stack by mutableStateOf<List<Place>>(listOf(myDrive))
        private set
    var items by mutableStateOf<List<DriveFile>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Set while a folder is being read to add it as a book. */
    var adding by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    val place get() = stack.last()

    init {
        load()
    }

    fun open(place: Place) {
        stack = stack + place
        load()
    }

    fun switchRoot(root: Place) {
        stack = listOf(root)
        load()
    }

    fun showMyDrive() = switchRoot(myDrive)

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        load()
        return true
    }

    fun load() {
        job?.cancel()
        items = null
        error = null
        val target = place
        job = viewModelScope.launch {
            try {
                val files = when (target) {
                    is Folder -> app.drive.children(target.id)
                    Shared -> app.drive.sharedWithMe()
                    is Search -> app.drive.search(target.query)
                }
                items = files.filter { it.isFolder || it.isAudio }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }

    fun addFolder(folder: DriveFile, onAdded: (String) -> Unit) = add(folder.name, onAdded) { app.library.addFolder(folder) }

    fun addFile(file: DriveFile, onAdded: (String) -> Unit) = add(file.name, onAdded) { app.library.addFile(file) }

    private fun add(name: String, onAdded: (String) -> Unit, block: suspend () -> String) {
        if (adding != null) return
        adding = name
        viewModelScope.launch {
            try {
                onAdded(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                adding = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    signedIn: Boolean?,
    onSignIn: () -> Unit,
    ui: PlayerUi,
    player: Player?,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: BrowseViewModel = viewModel(),
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val place = vm.place

    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (!vm.back()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search Drive") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (query.isNotBlank()) {
                                    searching = false
                                    vm.open(BrowseViewModel.Search(query.trim()))
                                }
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(place.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                },
            )
        },
        bottomBar = { MiniPlayer(ui, player, onOpenPlayer) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (signedIn == false) {
                SignInCard(onSignIn, hasBooks = false)
                return@Column
            }
            if (vm.stack.size == 1) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = place is BrowseViewModel.Folder, onClick = vm::showMyDrive, label = { Text("My Drive") })
                    FilterChip(
                        selected = place == BrowseViewModel.Shared,
                        onClick = { vm.switchRoot(BrowseViewModel.Shared) },
                        label = { Text("Shared with me") },
                    )
                }
            }
            vm.adding?.let {
                Text("Reading \"$it\"…", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val items = vm.items
            val error = vm.error
            when {
                error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::load) { Text("Retry") }
                }
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Text("No folders or audio files here.", Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val folder = place as? BrowseViewModel.Folder
                    if (folder != null && folder.id != "root" && items.any { it.isAudio }) {
                        item {
                            Button(
                                onClick = { vm.addFolder(DriveFile(folder.id, folder.title, FOLDER_MIME), onPlay) },
                                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                            ) { Text("Play this folder as a book") }
                        }
                    }
                    items(items, key = { it.id }) { file ->
                        if (file.isFolder) {
                            ListItem(
                                modifier = Modifier.clickable { vm.open(BrowseViewModel.Folder(file.id, file.name)) },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                headlineContent = { Text(file.name) },
                                trailingContent = {
                                    IconButton(onClick = { vm.addFolder(file, onPlay) }) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = "Play folder as a book")
                                    }
                                },
                            )
                        } else {
                            ListItem(
                                modifier = Modifier.clickable { vm.addFile(file, onPlay) },
                                leadingContent = { Icon(Icons.Default.AudioFile, contentDescription = null) },
                                headlineContent = { Text(file.name) },
                                supportingContent = file.size?.toLongOrNull()?.let { size ->
                                    { Text("%.1f MB".format(size / 1_048_576.0)) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
