package com.ejl191.driveplayer.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.playback.MediaIds
import com.ejl191.driveplayer.playback.PlaybackService
import com.ejl191.driveplayer.playback.Playlist
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

/** The UI's handle on PlaybackService. */
class PlayerConnection(private val context: Context) {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({ controller = runCatching { f.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        }
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    /** Starts a book from its saved position, or just resumes if it's already loaded. */
    suspend fun play(bookId: String) {
        val c = controller ?: return
        val dao = context.app.db.dao()
        if (MediaIds.parse(c.currentMediaItem?.mediaId)?.first == bookId) {
            c.play()
            return
        }
        val book = dao.getBook(bookId) ?: return
        val tracks = dao.getTracks(bookId)
        if (tracks.isEmpty()) return
        c.pause() // saves the position of whatever was playing before
        val queue = Playlist.build(book, tracks)
        c.setMediaItems(queue.items, queue.startIndex, queue.startPositionMs)
        c.setPlaybackSpeed(book.speed)
        c.prepare()
        c.play()
    }
}

data class PlayerUi(
    val bookId: String? = null,
    val fileId: String? = null,
    val bookTitle: String = "",
    val title: String = "",
    val index: Int = 0,
    val count: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val error: String? = null,
) {
    companion object {
        fun from(p: Player): PlayerUi {
            val ids = MediaIds.parse(p.currentMediaItem?.mediaId)
            val meta = p.mediaMetadata
            return PlayerUi(
                bookId = ids?.first,
                fileId = ids?.second,
                bookTitle = meta.albumTitle?.toString().orEmpty(),
                title = meta.title?.toString().orEmpty(),
                index = p.currentMediaItemIndex,
                count = p.mediaItemCount,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                positionMs = p.currentPosition,
                durationMs = p.duration.coerceAtLeast(0),
                speed = p.playbackParameters.speed,
                error = p.playerError?.let { it.cause?.message ?: it.message },
            )
        }
    }
}

@Composable
fun rememberPlayerUi(player: Player?): PlayerUi {
    var ui by remember { mutableStateOf(PlayerUi()) }
    DisposableEffect(player) {
        if (player == null) {
            ui = PlayerUi()
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                ui = PlayerUi.from(player)
            }
        }
        player.addListener(listener)
        ui = PlayerUi.from(player)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, ui.isPlaying) {
        while (player != null && ui.isPlaying) {
            delay(500)
            ui = PlayerUi.from(player)
        }
    }
    return ui
}
