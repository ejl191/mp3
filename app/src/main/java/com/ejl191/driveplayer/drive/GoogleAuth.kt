package com.ejl191.driveplayer.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/** IOException so that ExoPlayer and download code treat it as a normal load failure. */
class NeedsSignInException : IOException("Sign in to Google Drive first")

/** Gets read-only Drive access tokens through Google Play services. */
class GoogleAuth(private val context: Context) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_READONLY)))
        .build()
    private val mutex = Mutex()

    @Volatile private var token: String? = null
    @Volatile private var tokenTime = 0L

    private val _signedIn = MutableStateFlow<Boolean?>(null)
    /** null until the first check finishes. */
    val signedIn: StateFlow<Boolean?> = _signedIn

    /**
     * Gets a token without UI if access was granted before. Returns a PendingIntent when the
     * user still has to pick an account or grant consent.
     */
    suspend fun authorize(): PendingIntent? {
        val result = client.authorize(request).await()
        if (result.hasResolution()) {
            _signedIn.value = false
            return result.pendingIntent
        }
        store(result)
        return null
    }

    fun handleConsentResult(data: Intent?) {
        if (data == null) {
            _signedIn.value = false
            return
        }
        try {
            store(client.getAuthorizationResultFromIntent(data))
        } catch (e: Exception) {
            _signedIn.value = false
        }
    }

    suspend fun token(): String = mutex.withLock {
        token?.let { if (System.currentTimeMillis() - tokenTime < TOKEN_TTL_MS) return it }
        val needsUi = try {
            authorize() != null
        } catch (e: Exception) {
            throw IOException("Couldn't reach Google sign-in: ${e.message}", e)
        }
        if (needsUi) throw NeedsSignInException()
        token ?: throw NeedsSignInException()
    }

    /** Call when Drive rejected a token, so the next request fetches a fresh one. */
    suspend fun invalidate(rejected: String? = null) {
        val stale = mutex.withLock {
            val t = rejected ?: token
            if (token == t) token = null
            t
        } ?: return
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, stale) } }
    }

    private fun store(result: AuthorizationResult) {
        token = result.accessToken
        tokenTime = System.currentTimeMillis()
        _signedIn.value = token != null
    }

    companion object {
        const val DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        private const val TOKEN_TTL_MS = 45 * 60 * 1000L
    }
}
