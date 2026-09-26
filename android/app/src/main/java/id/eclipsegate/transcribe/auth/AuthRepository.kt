package id.eclipsegate.transcribe.auth

import id.eclipsegate.transcribe.network.AuthApi
import id.eclipsegate.transcribe.network.LoginRequest
import id.eclipsegate.transcribe.network.RefreshRequest
import id.eclipsegate.transcribe.network.RegisterRequest
import id.eclipsegate.transcribe.network.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthRepository {
    val isLoggedIn: StateFlow<Boolean>
    fun notifySessionExpired()
    suspend fun login(email: String, pass: String): Result<UserDto>
    suspend fun register(email: String, pass: String, fullName: String): Result<UserDto>
    suspend fun loginWithGoogle(idToken: String): Result<UserDto>
    suspend fun logout(): Result<Unit>
}

class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    private val _isLoggedIn = MutableStateFlow(tokenStorage.hasValidSession())
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    override fun notifySessionExpired() {
        tokenStorage.clear()
        _isLoggedIn.value = false
    }

    override suspend fun login(email: String, pass: String): Result<UserDto> {
        return try {
            val response = authApi.login(LoginRequest(email.trim().lowercase(), pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenStorage.saveTokens(body.token, body.refreshToken)
                _isLoggedIn.value = true
                Result.success(body.user)
            } else {
                val errMsg = parseError(response.errorBody()?.string())
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, pass: String, fullName: String): Result<UserDto> {
        return try {
            val response = authApi.register(RegisterRequest(email.trim().lowercase(), pass, fullName.trim()))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenStorage.saveTokens(body.token, body.refreshToken)
                _isLoggedIn.value = true
                Result.success(body.user)
            } else {
                val errMsg = parseError(response.errorBody()?.string())
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<UserDto> {
        return try {
            val response = authApi.googleLogin(id.eclipsegate.transcribe.network.GoogleLoginRequest(idToken))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenStorage.saveTokens(body.token, body.refreshToken)
                _isLoggedIn.value = true
                Result.success(body.user)
            } else {
                val errMsg = parseError(response.errorBody()?.string())
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = tokenStorage.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                authApi.logout(RefreshRequest(refreshToken))
            }
            tokenStorage.clear()
            _isLoggedIn.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            tokenStorage.clear()
            _isLoggedIn.value = false
            Result.success(Unit)
        }
    }

    private fun parseError(json: String?): String {
        if (json.isNullOrBlank()) return "Terjadi kesalahan jaringan"
        return try {
            val map = com.google.gson.JsonParser.parseString(json).asJsonObject
            map.get("error")?.asString ?: "Permintaan gagal diproses"
        } catch (e: Exception) {
            "Kesalahan otentikasi"
        }
    }
}
