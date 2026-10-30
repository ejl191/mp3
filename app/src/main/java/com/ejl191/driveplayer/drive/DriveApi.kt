package com.ejl191.driveplayer.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

const val FOLDER_MIME = "application/vnd.google-apps.folder"
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav")

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    /** Drive sends sizes as strings. */
    val size: String? = null,
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME
    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") ||
            name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
}

@Serializable
private data class FileList(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)

class DriveApi(private val auth: GoogleAuth, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun children(folderId: String): List<DriveFile> =
        list("'${escape(folderId)}' in parents and trashed = false")

    suspend fun sharedWithMe(): List<DriveFile> =
        list("sharedWithMe = true and trashed = false")

    suspend fun search(text: String): List<DriveFile> =
        list(
            "name contains '${escape(text)}' and trashed = false and " +
                "(mimeType = '$FOLDER_MIME' or mimeType contains 'audio/')"
        )

    private suspend fun list(q: String): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("orderBy", "folder,name")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val result = json.decodeFromString<FileList>(get(url))
            out += result.files
            page = result.nextPageToken
        } while (page != null)
        return out
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val token = auth.token()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    auth.invalidate(token)
                    return@repeat
                }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Drive error ${response.code}: ${body.take(200)}")
                return@withContext body
            }
        }
        throw IOException("Google Drive rejected the sign-in")
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        const val HOST = "www.googleapis.com"
        private const val API = "https://$HOST/drive/v3"

        fun mediaUrl(fileId: String) = "$API/files/$fileId?alt=media&supportsAllDrives=true"
    }
}
