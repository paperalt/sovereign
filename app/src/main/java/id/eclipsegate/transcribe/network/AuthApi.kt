package id.eclipsegate.transcribe.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("user") val user: UserDto
)

data class TokenPairResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class MessageResponse(
    @SerializedName("message") val message: String
)

data class GoogleLoginRequest(
    @SerializedName("id_token") val idToken: String
)

interface AuthApi {
    @POST("/api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("/api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<AuthResponse>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<TokenPairResponse>

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<MessageResponse>
}
