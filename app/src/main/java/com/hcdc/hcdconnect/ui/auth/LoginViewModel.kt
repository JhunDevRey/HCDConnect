package com.hcdc.hcdconnect.ui.auth

import android.util.Log
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP }

data class LoginUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val emailError: Int? = null,
    val passwordError: Int? = null,
    val confirmPasswordError: Int? = null,
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

    fun toggleMode() = _uiState.update {
        val newMode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
        LoginUiState(mode = newMode)
    }

    fun onEmailEdited() = _uiState.update { it.copy(emailError = null) }
    fun onPasswordEdited() = _uiState.update { it.copy(passwordError = null) }
    fun onConfirmPasswordEdited() = _uiState.update { it.copy(confirmPasswordError = null) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun submit(email: String, password: String, confirmPassword: String) {
        val state = _uiState.value
        if (state.isLoading) return
        val trimmedEmail = email.trim()

        val emailError = emailError(trimmedEmail)
        val passwordError = when {
            password.isEmpty() -> R.string.error_required
            state.mode == AuthMode.SIGN_UP && password.length < MIN_PASSWORD_LENGTH ->
                R.string.error_password_too_short
            else -> null
        }
        val confirmError = if (state.mode == AuthMode.SIGN_UP && confirmPassword != password) {
            R.string.error_passwords_dont_match
        } else {
            null
        }

        if (emailError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.SIGN_IN -> authRepository.signIn(trimmedEmail, password)
                AuthMode.SIGN_UP -> authRepository.signUp(trimmedEmail, password)
            }
            result.fold(
                onSuccess = {
                    // Deleted users can still sign in to Firebase, so turn them away here.
                    // If the check fails (e.g. offline), the dashboard checks again.
                    val userId = authRepository.currentUser?.uid
                    val removed = state.mode == AuthMode.SIGN_IN && userId != null &&
                        userRepository.isRemoved(userId).getOrNull() == true
                    if (removed) {
                        authRepository.signOut()
                        _uiState.update { it.copy(isLoading = false, message = R.string.account_removed) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, message = messageFor(e)) }
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
                    message = if (result.isSuccess) R.string.password_reset_sent else messageFor(result.exceptionOrNull())
                )
            }
        }
    }

    private fun emailError(email: String): Int? = when {
        email.isEmpty() -> R.string.error_required
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.error_invalid_email
        else -> null
    }

    @StringRes
    private fun messageFor(e: Throwable?): Int {
        Log.w(TAG, "Auth request failed", e)
        return when (e) {
            is FirebaseAuthWeakPasswordException -> R.string.auth_error_weak_password
            // Also covers "no such user": Firebase reports both the same way so
            // attackers can't probe which emails have accounts.
            is FirebaseAuthInvalidCredentialsException -> R.string.auth_error_invalid_credentials
            is FirebaseAuthInvalidUserException -> R.string.auth_error_account_disabled
            is FirebaseAuthUserCollisionException -> R.string.auth_error_email_in_use
            is FirebaseNetworkException -> R.string.auth_error_network
            is FirebaseTooManyRequestsException -> R.string.auth_error_too_many_requests
            else -> R.string.auth_error_generic
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
        // Firebase's own minimum.
        const val MIN_PASSWORD_LENGTH = 6
    }
}
