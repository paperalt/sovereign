package id.eclipsegate.transcribe.auth

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip authorization header for public auth routes
        val path = originalRequest.url.encodedPath
        if (path.contains("/api/v1/auth/login") || 
            path.contains("/api/v1/auth/register") || 
            path.contains("/api/v1/auth/google") || 
            path.contains("/api/v1/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()
        val accessToken = tokenStorage.getAccessToken()
        if (!accessToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        val llmProvider = tokenStorage.getLLMProvider()
        val llmKey = tokenStorage.getProviderApiKey(llmProvider)
        if (!llmKey.isNullOrBlank()) {
            requestBuilder.header("X-LLM-Provider", llmProvider)
            requestBuilder.header("X-LLM-Key", llmKey)
        }

        val sttProvider = tokenStorage.getSTTProvider()
        val sttKey = tokenStorage.getProviderApiKey(sttProvider)
        if (!sttKey.isNullOrBlank()) {
            requestBuilder.header("X-STT-Provider", sttProvider)
            requestBuilder.header("X-STT-Key", sttKey)
        }

        return chain.proceed(requestBuilder.build())
    }
}
