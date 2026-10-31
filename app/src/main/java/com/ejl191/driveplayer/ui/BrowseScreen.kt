package com.ejl191.driveplayer.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
        containerColor = Retro.Chrome,
        topBar = { RetroTitleBar("Open: ${place.title}", onBack = { if (!vm.back()) onBack() }) },
        bottomBar = { BottomDock { MiniPlayer(ui, player, onOpenPlayer) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(4.dp)) {
            if (signedIn == false) {
                SignInCard(onSignIn, hasBooks = false)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RetroTextButton("MY DRIVE", vm::showMyDrive, active = vm.stack.size == 1 && place is BrowseViewModel.Folder)
                RetroTextButton(
                    "SHARED",
                    { vm.switchRoot(BrowseViewModel.Shared) },
                    active = vm.stack.size == 1 && place == BrowseViewModel.Shared,
                )
                RetroTextButton("FIND", { searching = !searching }, active = searching)
            }
            if (searching) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LcdBox(Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = Retro.body.copy(color = Retro.LcdGreen),
                            cursorBrush = SolidColor(Retro.LcdGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (query.isNotBlank()) {
                                    searching = false
                                    vm.open(BrowseViewModel.Search(query.trim()))
                                }
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            vm.adding?.let {
                Spacer(Modifier.height(4.dp))
                LcdBox(Modifier.fillMaxWidth()) {
                    MarqueeText("READING \"$it\"...  ***  ", style = Retro.small)
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().background(Retro.Lcd).bevel(raised = false)) {
                val items = vm.items
                val error = vm.error
                when {
                    error != null -> Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ERROR: $error", style = Retro.small, color = Retro.Error)
                        RetroTextButton("RETRY", vm::load)
                    }
                    items == null -> Text("LOADING...", Modifier.padding(8.dp), style = Retro.small, color = Retro.PlDim)
                    items.isEmpty() -> Text("NO FOLDERS OR AUDIO FILES HERE.", Modifier.padding(8.dp), style = Retro.small, color = Retro.PlDim)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        val folder = place as? BrowseViewModel.Folder
                        if (folder != null && folder.id != "root" && items.any { it.isAudio }) {
                            item {
                                RetroTextButton(
                                    "PLAY THIS FOLDER AS A BOOK",
                                    { vm.addFolder(DriveFile(folder.id, folder.title, FOLDER_MIME), onPlay) },
                                    Modifier.fillMaxWidth().padding(6.dp),
                                )
                            }
                        }
                        items(items, key = { it.id }) { file ->
                            if (file.isFolder) {
                                PlaylistRow(
                                    text = "[${file.name}]",
                                    onClick = { vm.open(BrowseViewModel.Folder(file.id, file.name)) },
                                ) {
                                    Spacer(Modifier.width(6.dp))
                                    RetroTextButton("PLAY", { vm.addFolder(file, onPlay) })
                                }
                            } else {
                                PlaylistRow(
                                    text = file.name,
                                    trailing = file.size?.toLongOrNull()?.let { "%.1fM".format(it / 1_048_576.0) },
                                    onClick = { vm.addFile(file, onPlay) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
