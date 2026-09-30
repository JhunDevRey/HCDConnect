package com.hcdc.hcdconnect.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** An entry in the organizers list. [club] is null until they join or are given one. */
data class Organizer(val userId: String, val email: String, val club: String?)

/**
 * The `organizers` list: one document per organizer's Firebase Auth UID, holding a `clubs`
 * array with at most one club. Admins add and remove organizers and can set anyone's club.
 * An organizer with no club can join one listed club themselves, once (see firestore.rules).
 */
class OrganizerRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val organizers get() = firestore.collection(COLLECTION_ORGANIZERS)

    /** [userId]'s organizer entry, or null if they aren't an organizer. */
    suspend fun getOrganizer(userId: String): Result<Organizer?> = runCatchingFirestore {
        organizers.document(userId).get().await().takeIf { it.exists() }?.toOrganizer()
    }

    /** Live list of every organizer. Only admins can read it. */
    fun observeAllOrganizers(): Flow<Result<List<Organizer>>> = callbackFlow {
        val registration = organizers.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.documents.map { it.toOrganizer() }))
            }
        }
        awaitClose { registration.remove() }
    }

    /** Admin only: makes [userId] an organizer, optionally of [club]. */
    suspend fun setOrganizer(userId: String, email: String, club: String?): Result<Unit> =
        runCatchingFirestore {
            organizers.document(userId)
                .set(mapOf(FIELD_EMAIL to email, FIELD_CLUBS to listOfNotNull(club)))
                .await()
        }

    /** Admin only: removes [userId]'s organizer role. */
    suspend fun removeOrganizer(userId: String): Result<Unit> = runCatchingFirestore {
        organizers.document(userId).delete().await()
    }

    /** The signed-in organizer joins [club]. Only allowed while they have no club yet. */
    suspend fun joinClub(userId: String, club: String): Result<Unit> = runCatchingFirestore {
        organizers.document(userId).update(FIELD_CLUBS, listOf(club)).await()
    }

    private fun DocumentSnapshot.toOrganizer() = Organizer(
        userId = id,
        email = getString(FIELD_EMAIL).orEmpty(),
        club = (get(FIELD_CLUBS) as? List<*>)
            ?.filterIsInstance<String>()
            ?.firstOrNull { it.isNotBlank() }
    )

    private suspend fun <T> runCatchingFirestore(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val COLLECTION_ORGANIZERS = "organizers"
        const val FIELD_EMAIL = "email"
        const val FIELD_CLUBS = "clubs"
    }
}
