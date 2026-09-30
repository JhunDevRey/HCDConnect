package com.hcdc.hcdconnect.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Admins are listed in `admins/{uid}`. Admins can add other admins in the app; the rules
 * stop an admin from removing themselves, so there's always at least one.
 */
class AdminRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val admins get() = firestore.collection(COLLECTION_ADMINS)

    suspend fun isAdmin(userId: String): Result<Boolean> = runCatchingFirestore {
        admins.document(userId).get().await().exists()
    }

    /** Live set of admin UIDs. Only admins can read it. */
    fun observeAdminIds(): Flow<Result<Set<String>>> = callbackFlow {
        val registration = admins.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.documents.map { it.id }.toSet()))
            }
        }
        awaitClose { registration.remove() }
    }

    /** Admin only: grants or removes admin access for [userId]. */
    suspend fun setAdmin(userId: String, email: String, admin: Boolean): Result<Unit> =
        runCatchingFirestore {
            val doc = admins.document(userId)
            if (admin) doc.set(mapOf(FIELD_EMAIL to email)).await() else doc.delete().await()
        }

    private suspend fun <T> runCatchingFirestore(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val COLLECTION_ADMINS = "admins"
        const val FIELD_EMAIL = "email"
    }
}
