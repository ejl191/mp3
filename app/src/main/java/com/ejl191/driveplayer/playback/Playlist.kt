package com.ejl191.driveplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ejl191.driveplayer.data.Book
import com.ejl191.driveplayer.data.Track

/** Media ids are "bookId|fileId" so the service knows which book's progress to save. */
object MediaIds {
    fun of(bookId: String, fileId: String) = "$bookId|$fileId"

    fun parse(mediaId: String?): Pair<String, String>? {
        val parts = mediaId?.split('|', limit = 2) ?: return null
        return if (parts.size == 2) parts[0] to parts[1] else null
    }
}

object Playlist {
    class Queue(val items: List<MediaItem>, val startIndex: Int, val startPositionMs: Long)

    /** Items carry no URI; PlaybackService resolves each to a downloaded file or a Drive stream. */
    fun build(book: Book, tracks: List<Track>): Queue {
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(MediaIds.of(book.id, track.fileId))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(book.title)
                        .setAlbumTitle(book.title)
                        .build()
                )
                .build()
        }
        val index = tracks.indexOfFirst { it.fileId == book.currentFileId }
        return if (index >= 0) Queue(items, index, book.positionMs) else Queue(items, 0, 0)
    }
}
