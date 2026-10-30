package com.ejl191.driveplayer.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ejl191.driveplayer.app
import com.ejl191.driveplayer.drive.DriveApi
import com.ejl191.driveplayer.drive.NeedsSignInException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Offline copies live at filesDir/audio/<fileId>; playback prefers them over streaming. */
object Downloads {
    fun file(context: Context, fileId: String) = File(context.filesDir, "audio/$fileId")

    fun workName(bookId: String) = "download-$bookId"

    fun start(context: Context, bookId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK to bookId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(bookId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, bookId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(bookId))
    }

    suspend fun remove(context: Context, bookId: String) {
        cancel(context, bookId)
        val dao = context.app.db.dao()
        withContext(Dispatchers.IO) {
            for (track in dao.getTracks(bookId)) {
                val f = file(context, track.fileId)
                f.delete()
                File(f.path + ".part").delete()
            }
        }
        dao.clearDownloads(bookId)
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val dao = app.db.dao()
        val bookId = inputData.getString(KEY_BOOK) ?: return Result.failure()
        val book = dao.getBook(bookId) ?: return Result.success()
        val pending = dao.getTracks(bookId).filter {
            !it.downloaded || !Downloads.file(applicationContext, it.fileId).exists()
        }
        pending.forEachIndexed { i, track ->
            runCatching { setForeground(foregroundInfo(book.title, i, pending.size)) }
            try {
                download(track.fileId)
            } catch (e: NeedsSignInException) {
                return Result.failure()
            } catch (e: IOException) {
                return if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
            dao.setDownloaded(bookId, track.fileId, true)
        }
        return Result.success()
    }

    /** Downloads into a .part file, resuming a previous partial download when Drive allows it. */
    private suspend fun download(fileId: String) = withContext(Dispatchers.IO) {
        val app = applicationContext.app
        val dest = Downloads.file(applicationContext, fileId)
        if (dest.exists()) return@withContext
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        repeat(2) { attempt ->
            val token = app.auth.token()
            val have = if (part.exists()) part.length() else 0L
            val request = Request.Builder()
                .url(DriveApi.mediaUrl(fileId))
                .header("Authorization", "Bearer $token")
                .apply { if (have > 0) header("Range", "bytes=$have-") }
                .build()
            app.http.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    app.auth.invalidate(token)
                    return@repeat
                }
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val append = response.code == 206
                FileOutputStream(part, append).use { out ->
                    val input = response.body!!.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
                if (!part.renameTo(dest)) throw IOException("Couldn't save download")
                return@withContext
            }
        }
        throw IOException("Google Drive rejected the sign-in")
    }

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText("${done + 1} of $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val id = id.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_BOOK = "bookId"
        private const val CHANNEL = "downloads"
    }
}
