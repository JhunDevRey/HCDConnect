package com.hcdc.hcdconnect.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A signed-up user, as listed for admins. */
data class AppUser(val userId: String, val email: String)

/**
 * User profiles in `users/{uid}`. Firebase Auth can't list accounts from the app, so each
 * user writes their own profile on sign-in, and admins read the collection to manage organizers.
 */
class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val users get() = firestore.collection(COLLECTION_USERS)

    /** Creates or refreshes the signed-in user's profile. The rules only allow writing your own. */
    suspend fun recordSignIn(userId: String, email: String): Result<Unit> = try {
        users.document(userId)
            .set(mapOf(FIELD_EMAIL to email, FIELD_LAST_SIGN_IN to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Live list of all users, by email. Only admins are allowed to read it. */
    fun observeUsers(): Flow<Result<List<AppUser>>> = callbackFlow {
        val registration = users.orderBy(FIELD_EMAIL, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(Result.failure(error))
                    snapshot != null -> trySend(Result.success(snapshot.documents.map {
                        AppUser(userId = it.id, email = it.getString(FIELD_EMAIL).orEmpty())
                    }))
                }
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_EMAIL = "email"
        const val FIELD_LAST_SIGN_IN = "lastSignIn"
    }
}
