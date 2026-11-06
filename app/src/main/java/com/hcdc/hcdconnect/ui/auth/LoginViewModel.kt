package com.hcdc.hcdconnect.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val emailError: Int? = null,
    val passwordError: Int? = null,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    // String resource IDs. The message is one-off; cleared with onMessageShown() once displayed.
    val message: Int? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailEdited() = _uiState.update { it.copy(emailError = null) }
    fun onPasswordEdited() = _uiState.update { it.copy(passwordError = null) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun signIn(email: String, password: String) {
        if (_uiState.value.isLoading) return
        val trimmedEmail = email.trim()

        val emailError = emailError(trimmedEmail)
        val passwordError = if (password.isEmpty()) R.string.error_required else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.signIn(trimmedEmail, password).fold(
                onSuccess = {
                    // Deleted users can still sign in to Firebase, so turn them away here.
                    // If the check fails (e.g. offline), the dashboard checks again.
                    val userId = authRepository.currentUser?.uid
                    val removed = userId != null &&
                        userRepository.isRemoved(userId).getOrNull() == true
                    if (removed) {
                        authRepository.signOut()
                        _uiState.update { it.copy(isLoading = false, message = R.string.account_removed) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, message = authMessageFor(e)) }
                }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        if (_uiState.value.isLoading) return
        val trimmedEmail = email.trim()
        val emailError = emailError(trimmedEmail)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(trimmedEmail)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = if (result.isSuccess) R.string.password_reset_sent else authMessageFor(result.exceptionOrNull())
                )
            }
        }
    }
}
