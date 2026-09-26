package org.sovereign.app.network

import org.sovereign.app.auth.AuthInterceptor
import org.sovereign.app.auth.TokenAuthenticator
import org.sovereign.app.auth.TokenStorage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    val baseUrl: String,
    private val tokenStorage: TokenStorage,
    private val onSessionExpired: () -> Unit
) {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val authInterceptor = AuthInterceptor(tokenStorage)
    private val tokenAuthenticator = TokenAuthenticator(tokenStorage, baseUrl, onSessionExpired)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val meetingApi: MeetingApi = retrofit.create(MeetingApi::class.java)
}
