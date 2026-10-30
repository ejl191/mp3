package com.ejl191.driveplayer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {
    @Transaction
    @Query("SELECT * FROM books ORDER BY lastPlayedAt DESC, addedAt DESC")
    abstract fun library(): Flow<List<BookWithTracks>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    abstract fun bookFlow(id: String): Flow<BookWithTracks?>

    @Query("SELECT * FROM books WHERE id = :id")
    abstract suspend fun getBook(id: String): Book?

    @Query("SELECT * FROM books WHERE currentFileId IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    abstract suspend fun lastPlayed(): Book?

    @Query("SELECT * FROM tracks WHERE bookId = :bookId ORDER BY position")
    abstract suspend fun getTracks(bookId: String): List<Track>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBook(book: Book)

    @Query("UPDATE books SET title = :title WHERE id = :id")
    protected abstract suspend fun renameBook(id: String, title: String)

    @Query("DELETE FROM tracks WHERE bookId = :bookId")
    protected abstract suspend fun deleteTracks(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTracks(tracks: List<Track>)

    /** Adds the book (keeping saved progress if it already exists) and replaces its track list. */
    @Transaction
    open suspend fun replaceBook(book: Book, tracks: List<Track>) {
        insertBook(book)
        renameBook(book.id, book.title)
        deleteTracks(book.id)
        insertTracks(tracks)
    }

    @Query("UPDATE books SET currentFileId = :fileId, positionMs = :positionMs, lastPlayedAt = :time WHERE id = :bookId")
    abstract suspend fun saveProgress(bookId: String, fileId: String, positionMs: Long, time: Long)

    @Query("UPDATE books SET speed = :speed WHERE id = :bookId")
    abstract suspend fun saveSpeed(bookId: String, speed: Float)

    @Query("UPDATE tracks SET durationMs = :durationMs WHERE bookId = :bookId AND fileId = :fileId")
    abstract suspend fun saveDuration(bookId: String, fileId: String, durationMs: Long)

    @Query("UPDATE tracks SET downloaded = :downloaded WHERE bookId = :bookId AND fileId = :fileId")
    abstract suspend fun setDownloaded(bookId: String, fileId: String, downloaded: Boolean)

    @Query("UPDATE tracks SET downloaded = 0 WHERE bookId = :bookId")
    abstract suspend fun clearDownloads(bookId: String)

    @Query("DELETE FROM books WHERE id = :id")
    protected abstract suspend fun deleteBookRow(id: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    protected abstract suspend fun deleteBookmarks(bookId: String)

    @Transaction
    open suspend fun deleteBook(id: String) {
        deleteTracks(id)
        deleteBookmarks(id)
        deleteBookRow(id)
    }

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    abstract fun bookmarks(bookId: String): Flow<List<Bookmark>>

    @Insert
    abstract suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    abstract suspend fun deleteBookmark(bookmark: Bookmark)
}

@Database(entities = [Book::class, Track::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "library.db").build()
    }
}
