package com.hcdc.hcdconnect.ui.auth

import android.util.Log
import android.util.Patterns
import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.hcdc.hcdconnect.R

// Validation and error messages shared by the login and register screens.

// Firebase's own minimum.
internal const val MIN_PASSWORD_LENGTH = 6

/** New accounts must use a school address. Signing in doesn't check it, so older accounts still work. */
internal const val SCHOOL_EMAIL_DOMAIN = "hcdc.edu.ph"

private const val TAG = "Auth"

@StringRes
internal fun emailError(email: String): Int? = when {
    email.isEmpty() -> R.string.error_required
    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.error_invalid_email
    else -> null
}

@StringRes
internal fun schoolEmailError(email: String): Int? =
    emailError(email) ?: if (email.endsWith("@$SCHOOL_EMAIL_DOMAIN", ignoreCase = true)) {
        null
    } else {
        R.string.error_not_school_email
    }

@StringRes
internal fun authMessageFor(e: Throwable?): Int {
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
