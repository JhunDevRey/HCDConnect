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
    private val removedUsers get() = firestore.collection(COLLECTION_REMOVED_USERS)

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

    /** Whether an admin has deleted [userId]. Users can check only themselves. */
    suspend fun isRemoved(userId: String): Result<Boolean> = try {
        Result.success(removedUsers.document(userId).get().await().exists())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Admin only: deletes [userId] from the app. The free Firebase plan can't delete the
     * sign-in account itself, so this blocks it instead: the user goes on the removed list,
     * which the rules treat as signed out, and their profile, roles and RSVPs are deleted.
     * Events they posted stay. All changes are made together, or none are.
     */
    suspend fun removeUser(userId: String, email: String, removedBy: String): Result<Unit> = try {
        // Queries can't run inside a batch, so find their RSVPs first.
        val rsvps = firestore.collection(COLLECTION_EVENTS)
            .whereArrayContains(FIELD_ATTENDEES, userId)
            .get()
            .await()
        val batch = firestore.batch()
        batch.set(
            removedUsers.document(userId),
            mapOf(FIELD_EMAIL to email, FIELD_REMOVED_BY to removedBy, FIELD_REMOVED_AT to FieldValue.serverTimestamp())
        )
        rsvps.documents.forEach { batch.update(it.reference, FIELD_ATTENDEES, FieldValue.arrayRemove(userId)) }
        batch.delete(users.document(userId))
        batch.delete(firestore.collection(COLLECTION_ORGANIZERS).document(userId))
        batch.delete(firestore.collection(COLLECTION_ADMINS).document(userId))
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_REMOVED_USERS = "removedUsers"
        const val COLLECTION_EVENTS = "events"
        const val COLLECTION_ORGANIZERS = "organizers"
        const val COLLECTION_ADMINS = "admins"
        const val FIELD_EMAIL = "email"
        const val FIELD_LAST_SIGN_IN = "lastSignIn"
        const val FIELD_ATTENDEES = "attendees"
        const val FIELD_REMOVED_BY = "removedBy"
        const val FIELD_REMOVED_AT = "removedAt"
    }
}
