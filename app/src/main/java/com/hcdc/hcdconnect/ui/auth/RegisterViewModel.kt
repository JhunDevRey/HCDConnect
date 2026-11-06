package com.hcdc.hcdconnect.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val fullNameError: Int? = null,
    val emailError: Int? = null,
    val passwordError: Int? = null,
    val confirmPasswordError: Int? = null,
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    // String resource ID. One-off; cleared with onMessageShown() once displayed.
    val message: Int? = null
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameEdited() = _uiState.update { it.copy(fullNameError = null) }
    fun onEmailEdited() = _uiState.update { it.copy(emailError = null) }
    fun onPasswordEdited() = _uiState.update { it.copy(passwordError = null) }
    fun onConfirmPasswordEdited() = _uiState.update { it.copy(confirmPasswordError = null) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun register(fullName: String, email: String, password: String, confirmPassword: String) {
        if (_uiState.value.isLoading) return
        // Collapse runs of spaces so "Juan  Dela Cruz" is stored once, cleanly.
        val trimmedName = fullName.trim().replace(Regex("\\s+"), " ")
        val trimmedEmail = email.trim()

        val fullNameError = when {
            trimmedName.isEmpty() -> R.string.error_required
            trimmedName.length > MAX_FULL_NAME_LENGTH -> R.string.error_full_name_too_long
            else -> null
        }
        val emailError = schoolEmailError(trimmedEmail)
        val passwordError = when {
            password.isEmpty() -> R.string.error_required
            password.length < MIN_PASSWORD_LENGTH -> R.string.error_password_too_short
            else -> null
        }
        val confirmError = when {
            confirmPassword.isEmpty() -> R.string.error_required
            confirmPassword != password -> R.string.error_passwords_dont_match
            else -> null
        }

        if (fullNameError != null || emailError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.signUp(trimmedEmail, password, trimmedName).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isRegistered = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, message = authMessageFor(e)) } }
            )
        }
    }

    private companion object {
        // Matches the limit in firestore.rules.
        const val MAX_FULL_NAME_LENGTH = 100
    }
}
