package com.ejl191.driveplayer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A Drive folder (whose audio files are its chapters) or a single audio file. Holds the saved position. */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val isFolder: Boolean,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val currentFileId: String? = null,
    val positionMs: Long = 0,
    val speed: Float = 1f,
)

@Entity(tableName = "tracks", primaryKeys = ["bookId", "fileId"])
data class Track(
    val bookId: String,
    val fileId: String,
    val title: String,
    val position: Int,
    val sizeBytes: Long,
    val durationMs: Long = 0,
    val downloaded: Boolean = false,
)

@Entity(tableName = "bookmarks", indices = [Index("bookId")])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val fileId: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BookWithTracks(
    @Embedded val book: Book,
    @Relation(parentColumn = "id", entityColumn = "bookId") val unorderedTracks: List<Track>,
) {
    val tracks: List<Track> get() = unorderedTracks.sortedBy { it.position }
    val currentIndex: Int get() = tracks.indexOfFirst { it.fileId == book.currentFileId }.coerceAtLeast(0)
    val allDownloaded: Boolean get() = tracks.isNotEmpty() && tracks.all { it.downloaded }

    /** Fraction of the whole book listened to, or null until every chapter's length is known. */
    val overallProgress: Float?
        get() {
            val t = tracks
            if (t.isEmpty() || t.any { it.durationMs <= 0 }) return null
            val total = t.sumOf { it.durationMs }
            val done = t.take(currentIndex).sumOf { it.durationMs } + book.positionMs
            return (done.toFloat() / total).coerceIn(0f, 1f)
        }
}
