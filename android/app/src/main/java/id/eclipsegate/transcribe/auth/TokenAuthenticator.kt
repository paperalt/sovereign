package id.eclipsegate.transcribe.auth

import com.google.gson.Gson
import id.eclipsegate.transcribe.network.RefreshRequest
import id.eclipsegate.transcribe.network.TokenPairResponse
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
    private val onSessionExpired: () -> Unit
) : Authenticator {

    private val lock = Any()
    private val gson = Gson()
    private val rawClient = OkHttpClient.Builder().build()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops if refresh endpoint itself returns 401
        if (response.request.url.encodedPath.contains("/api/v1/auth/refresh")) {
            tokenStorage.clear()
            onSessionExpired()
            return null
        }

        // Only retry once per request
        if (responseCount(response) >= 2) {
            tokenStorage.clear()
            onSessionExpired()
            return null
        }

        synchronized(lock) {
            val currentAccessToken = tokenStorage.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // If another thread has already refreshed the token, retry immediately with the new token
            if (currentAccessToken != null && currentAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val refreshToken = tokenStorage.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                tokenStorage.clear()
                onSessionExpired()
                return null
            }

            // Perform synchronous refresh call
            val refreshPayload = gson.toJson(RefreshRequest(refreshToken))
            val refreshRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/v1/auth/refresh")
                .post(refreshPayload.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val refreshResponse = rawClient.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful && refreshResponse.body != null) {
                    val bodyString = refreshResponse.body!!.string()
                    val tokenPair = gson.fromJson(bodyString, TokenPairResponse::class.java)

                    tokenStorage.saveTokens(tokenPair.token, tokenPair.refreshToken)

                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokenPair.token}")
                        .build()
                } else {
                    // Refresh token is revoked or expired in database
                    tokenStorage.clear()
                    onSessionExpired()
                    return null
                }
            } catch (e: Exception) {
                return null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
