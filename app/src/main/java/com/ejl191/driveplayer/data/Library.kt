package com.ejl191.driveplayer.data

import android.content.Context
import com.ejl191.driveplayer.drive.DriveApi
import com.ejl191.driveplayer.drive.DriveFile
import com.ejl191.driveplayer.playback.Downloads
import java.io.IOException

class Library(
    private val context: Context,
    private val dao: LibraryDao,
    private val drive: DriveApi,
) {
    /** Adds a folder as a book; its audio files (including in subfolders) become chapters in natural order. */
    suspend fun addFolder(folder: DriveFile): String {
        val files = collectAudio(folder.id, "")
        if (files.isEmpty()) throw IOException("No audio files found in \"${folder.name}\"")
        save(folder.id, folder.name, isFolder = true, files)
        return folder.id
    }

    suspend fun addFile(file: DriveFile): String {
        save(file.id, file.name.substringBeforeLast('.'), isFolder = false, listOf(file to file.name))
        return file.id
    }

    /** Re-reads a folder book from Drive to pick up added or removed files. */
    suspend fun refresh(bookId: String) {
        val book = dao.getBook(bookId) ?: return
        if (!book.isFolder) return
        save(bookId, book.title, isFolder = true, collectAudio(bookId, ""))
    }

    suspend fun remove(bookId: String) {
        Downloads.remove(context, bookId)
        dao.deleteBook(bookId)
    }

    private suspend fun collectAudio(folderId: String, prefix: String): List<Pair<DriveFile, String>> {
        val children = drive.children(folderId)
        val files = children.filter { !it.isFolder && it.isAudio }
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .map { it to prefix + it.name }
        val nested = children.filter { it.isFolder }
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .flatMap { collectAudio(it.id, "$prefix${it.name}/") }
        return files + nested
    }

    private suspend fun save(id: String, title: String, isFolder: Boolean, files: List<Pair<DriveFile, String>>) {
        val existing = dao.getTracks(id).associateBy { it.fileId }
        val tracks = files.mapIndexed { i, (file, path) ->
            Track(
                bookId = id,
                fileId = file.id,
                title = path.substringBeforeLast('.'),
                position = i,
                sizeBytes = file.size?.toLongOrNull() ?: 0,
                durationMs = existing[file.id]?.durationMs ?: 0,
                downloaded = existing[file.id]?.downloaded ?: false,
            )
        }
        dao.replaceBook(Book(id = id, title = title, isFolder = isFolder), tracks)
    }
}

/** Orders "Chapter 2" before "Chapter 10". */
object NaturalOrder : Comparator<String> {
    private val chunks = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val x = chunks.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunks.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pn = p.trimStart('0')
                val qn = q.trimStart('0')
                if (pn.length != qn.length) pn.length - qn.length else pn.compareTo(qn)
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size - y.size
    }
}
