package org.sovereign.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthManager(
    private val context: Context,
    private val clientIdProvider: () -> String
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): Result<String> {
        val serverClientId = clientIdProvider().trim()
        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Google OAuth Client ID is not configured. Add the Web Client ID in settings.")
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(Exception("Unsupported Google credential type"))
            }
        } catch (e: GetCredentialException) {
            val friendlyMsg = when {
                e.message?.contains("canceled", ignoreCase = true) == true -> "Sign-in cancelled"
                e.message?.contains("No credentials available", ignoreCase = true) == true -> "No Google account available"
                else -> e.message ?: "Google authentication failed"
            }
            Result.failure(Exception(friendlyMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
