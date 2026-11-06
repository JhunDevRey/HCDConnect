package com.hcdc.hcdconnect.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /** The signed-in user, or null. Firebase keeps the session across app restarts. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<Unit> = runAuth {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    /**
     * Creates the account, signs the new user in and saves [fullName] as their display name.
     * The dashboard copies it into the user's Firestore profile.
     */
    suspend fun signUp(email: String, password: String, fullName: String): Result<Unit> = runAuth {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Sign-up returned no user")
        user.updateProfile(userProfileChangeRequest { displayName = fullName }).await()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runAuth {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() = auth.signOut()

    private suspend fun runAuth(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
