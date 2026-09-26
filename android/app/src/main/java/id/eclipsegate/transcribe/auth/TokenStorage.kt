package id.eclipsegate.transcribe.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
    fun hasValidSession(): Boolean
    fun getServerUrl(): String
    fun setServerUrl(url: String)
    fun getGoogleClientId(): String
    fun setGoogleClientId(clientId: String)

    // AI Provider & Multi-Storage Key Vault
    fun getAIProvider(): String
    fun setAIProvider(provider: String)
    fun getSTTProvider(): String
    fun setSTTProvider(provider: String)
    fun getLLMProvider(): String
    fun setLLMProvider(provider: String)
    fun getProviderApiKey(provider: String): String?
    fun setProviderApiKey(provider: String, key: String?)
    fun getCustomApiKey(): String?
    fun setCustomApiKey(key: String?)

    // Beta Features
    fun isAdaptiveStreamingBetaEnabled(): Boolean
    fun setAdaptiveStreamingBetaEnabled(enabled: Boolean)
}

class EncryptedTokenStorage(context: Context) : TokenStorage {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun getServerUrl(): String {
        return id.eclipsegate.transcribe.BuildConfig.SERVER_URL
    }

    override fun setServerUrl(url: String) {
        // Immutable production configuration
    }

    override fun getGoogleClientId(): String {
        return id.eclipsegate.transcribe.BuildConfig.GOOGLE_CLIENT_ID
    }

    override fun setGoogleClientId(clientId: String) {
        // Immutable production configuration
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    override fun getAIProvider(): String {
        return prefs.getString(KEY_AI_PROVIDER, "DEFAULT") ?: "DEFAULT"
    }

    override fun setAIProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider.trim().uppercase()).apply()
    }

    override fun getSTTProvider(): String {
        return prefs.getString(KEY_STT_PROVIDER, null) ?: getAIProvider()
    }

    override fun setSTTProvider(provider: String) {
        val prov = provider.trim().uppercase()
        prefs.edit().putString(KEY_STT_PROVIDER, prov).apply()
        setAIProvider(prov)
    }

    override fun getLLMProvider(): String {
        return prefs.getString(KEY_LLM_PROVIDER, "DEFAULT") ?: "DEFAULT"
    }

    override fun setLLMProvider(provider: String) {
        prefs.edit().putString(KEY_LLM_PROVIDER, provider.trim().uppercase()).apply()
    }

    override fun getProviderApiKey(provider: String): String? {
        val normalized = provider.trim().lowercase()
        val keyName = "custom_key_$normalized"
        val key = prefs.getString(keyName, null)
        if (!key.isNullOrBlank()) {
            return key
        }
        // Fallback for legacy generic key
        return prefs.getString(KEY_CUSTOM_API_KEY, null)
    }

    override fun setProviderApiKey(provider: String, key: String?) {
        val normalized = provider.trim().lowercase()
        val keyName = "custom_key_$normalized"
        if (key.isNullOrBlank()) {
            prefs.edit().remove(keyName).apply()
        } else {
            prefs.edit().putString(keyName, key.trim()).apply()
        }
    }

    override fun getCustomApiKey(): String? {
        return getProviderApiKey(getAIProvider())
    }

    override fun setCustomApiKey(key: String?) {
        setProviderApiKey(getAIProvider(), key)
    }

    override fun isAdaptiveStreamingBetaEnabled(): Boolean {
        return prefs.getBoolean(KEY_ADAPTIVE_STREAMING_BETA, false)
    }

    override fun setAdaptiveStreamingBetaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADAPTIVE_STREAMING_BETA, enabled).apply()
    }

    override fun hasValidSession(): Boolean {
        return !getAccessToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank()
    }

    companion object {
        private const val PREFS_FILENAME = "secure_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_STT_PROVIDER = "stt_provider"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_ADAPTIVE_STREAMING_BETA = "adaptive_streaming_beta"
    }
}
