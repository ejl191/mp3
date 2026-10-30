package com.ejl191.driveplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ejl191.driveplayer.MainActivity
import com.ejl191.driveplayer.R
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.drive.DriveApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Owns the player so audio keeps going in the background, and saves the listening position as it plays. */
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = MainScope()
    private var saveJob: Job? = null
    private var authRetries = 0

    private val dao get() = app.db.dao()

    override fun onCreate() {
        super.onCreate()
        // Drive streams need a fresh OAuth header on every request.
        val dataSource = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
        ) { spec ->
            if (spec.uri.host == DriveApi.HOST) {
                val token = runBlocking { app.auth.token() }
                spec.withAdditionalHeaders(mapOf("Authorization" to "Bearer $token"))
            } else {
                spec
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            .build()
        player.addListener(listener)

        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setDisplayName("Back 30 seconds")
                        .setIconResId(R.drawable.ic_replay)
                        .setSessionCommand(REWIND)
                        .build(),
                    CommandButton.Builder()
                        .setDisplayName("Forward 30 seconds")
                        .setIconResId(R.drawable.ic_forward)
                        .setSessionCommand(FORWARD)
                        .build(),
                )
            )
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveProgress()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveProgress()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveProgress()
            saveJob?.cancel()
            if (isPlaying) {
                saveJob = scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        saveProgress()
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveProgress()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) saveProgress()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            val (bookId, _) = MediaIds.parse(player.currentMediaItem?.mediaId) ?: return
            app.scope.launch { dao.saveSpeed(bookId, playbackParameters.speed) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY) return
            authRetries = 0
            val (bookId, fileId) = MediaIds.parse(player.currentMediaItem?.mediaId) ?: return
            val duration = player.duration
            if (duration > 0) app.scope.launch { dao.saveDuration(bookId, fileId, duration) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Tokens expire after an hour; get a new one and carry on from the same spot.
            val cause = error.cause
            if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401 && authRetries++ < 2) {
                scope.launch {
                    app.auth.invalidate()
                    player.prepare()
                }
            }
        }
    }

    private fun saveProgress() {
        val (bookId, fileId) = MediaIds.parse(player.currentMediaItem?.mediaId) ?: return
        val position = player.currentPosition
        app.scope.launch { dao.saveProgress(bookId, fileId, position, System.currentTimeMillis()) }
    }

    private fun resolve(item: MediaItem): MediaItem {
        val (_, fileId) = MediaIds.parse(item.mediaId) ?: return item
        val local = Downloads.file(this, fileId)
        val uri = if (local.exists()) Uri.fromFile(local) else Uri.parse(DriveApi.mediaUrl(fileId))
        return item.buildUpon().setUri(uri).build()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(REWIND)
                .add(FORWARD)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                REWIND.customAction -> player.seekBack()
                FORWARD.customAction -> player.seekForward()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())

        /** Bluetooth "play" or the lock-screen resume card after a restart: continue the last book. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val book = dao.lastPlayed() ?: throw IllegalStateException("Nothing to resume")
            val queue = Playlist.build(book, dao.getTracks(book.id))
            player.setPlaybackSpeed(book.speed)
            MediaSession.MediaItemsWithStartPosition(
                queue.items.map(::resolve), queue.startIndex, queue.startPositionMs,
            )
        }
    }

    companion object {
        const val SKIP_MS = 30_000L
        private const val SAVE_INTERVAL_MS = 5_000L
        private val REWIND = SessionCommand("com.ejl191.driveplayer.REWIND", Bundle.EMPTY)
        private val FORWARD = SessionCommand("com.ejl191.driveplayer.FORWARD", Bundle.EMPTY)
    }
}
