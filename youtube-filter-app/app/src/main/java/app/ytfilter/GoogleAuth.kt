package app.ytfilter

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Gets a YouTube read-only OAuth token via Google Identity Services.
 * Requires an "Android" OAuth client in Google Cloud Console with this app's package name + SHA-1.
 */
class GoogleAuth(private val activity: Activity) {
    private val client = Identity.getAuthorizationClient(activity)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/youtube.readonly")))
        .build()

    sealed interface Result {
        data class Token(val value: String) : Result
        /** First time only: the user must pick an account / grant consent. */
        data class NeedsConsent(val request: IntentSenderRequest) : Result
    }

    suspend fun authorize(): Result {
        val result = client.authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            Result.NeedsConsent(IntentSenderRequest.Builder(pending.intentSender).build())
        } else {
            Result.Token(result.accessToken ?: error("Google 沒有回傳 token"))
        }
    }

    fun tokenFromConsent(data: Intent?): String? =
        runCatching { client.getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
}
