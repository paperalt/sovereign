package org.sovereign.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

sealed interface AuthIntent {
    data class SetEmail(val email: String) : AuthIntent
    data class SetPassword(val pass: String) : AuthIntent
    data class SetFullName(val name: String) : AuthIntent
    data class SignInWithGoogle(val idToken: String) : AuthIntent
    data class SetError(val message: String) : AuthIntent
    object ToggleMode : AuthIntent
    object Submit : AuthIntent
    object DismissError : AuthIntent
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    _uiState.update { it.copy(isAuthenticated = true) }
                }
            }
        }
    }

    fun processIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.SetEmail -> {
                _uiState.update { it.copy(email = intent.email, errorMessage = null) }
            }
            is AuthIntent.SetPassword -> {
                _uiState.update { it.copy(password = intent.pass, errorMessage = null) }
            }
            is AuthIntent.SetFullName -> {
                _uiState.update { it.copy(fullName = intent.name, errorMessage = null) }
            }
            is AuthIntent.ToggleMode -> {
                _uiState.update { 
                    it.copy(
                        isRegisterMode = !it.isRegisterMode, 
                        errorMessage = null,
                        password = ""
                    ) 
                }
            }
            is AuthIntent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is AuthIntent.SetError -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = intent.message) }
            }
            is AuthIntent.Submit -> {
                submitCredentials()
            }
            is AuthIntent.SignInWithGoogle -> {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                viewModelScope.launch {
                    val result = authRepository.loginWithGoogle(intent.idToken)
                    result.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Google sign-in failed") }
                        }
                    )
                }
            }
        }
    }

    private fun submitCredentials() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password

        // Client-side Input Validation
        if (email.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email is required") }
            return
        }
        if (!emailPattern.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid email format") }
            return
        }
        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
            return
        }
        if (state.isRegisterMode && state.fullName.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Full name is required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            if (state.isRegisterMode) {
                val result = authRepository.register(email, password, state.fullName.trim())
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Registration failed") }
                    }
                )
            } else {
                val result = authRepository.login(email, password)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Login failed") }
                    }
                )
            }
        }
    }
}
