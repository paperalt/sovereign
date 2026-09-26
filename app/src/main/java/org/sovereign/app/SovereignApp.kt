package org.sovereign.app

import android.app.Application
import org.sovereign.app.auth.*
import org.sovereign.app.data.DefaultMeetingRepository
import org.sovereign.app.data.LocalMeetingRepository
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.ApiClient

class SovereignApp : Application() {

    lateinit var tokenStorage: TokenStorage
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var meetingRepository: MeetingRepository
        private set

    lateinit var googleAuthManager: GoogleAuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenStorage = EncryptedTokenStorage(this)
        meetingRepository = LocalMeetingRepository(this, tokenStorage = tokenStorage)

        // Local dummy auth repository for sovereign offline-first operation
        val baseUrl = tokenStorage.getServerUrl()
        apiClient = ApiClient(
            baseUrl = baseUrl,
            tokenStorage = tokenStorage,
            onSessionExpired = {}
        )
        authRepository = DefaultAuthRepository(apiClient.authApi, tokenStorage)
        googleAuthManager = GoogleAuthManager(this) { tokenStorage.getGoogleClientId() }
    }

    companion object {
        lateinit var instance: SovereignApp
            private set
    }
}

// Default production configuration with Let's Encrypt SSL
object BuildConfig {
    const val SERVER_URL = "https://gate.eclipsegate.my.id"
    const val WS_URL = "wss://gate.eclipsegate.my.id/ws/transcribe"
    const val GOOGLE_CLIENT_ID = "709542679592-lprl5ob7uv2ktin74idhg4347n8urbuk.apps.googleusercontent.com"
}
