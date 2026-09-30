package com.hcdc.hcdconnect.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The official club list in `clubs/{name}`. The document ID is the club's name, so the
 * security rules can check that an event or organizer uses a listed club.
 * Everyone signed in can read it; only admins can change it.
 */
class ClubRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val clubs get() = firestore.collection(COLLECTION_CLUBS)

    /** Live list of club names, alphabetical. */
    fun observeClubs(): Flow<Result<List<String>>> = callbackFlow {
        val registration = clubs.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.documents.map { it.id }.sorted()))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun getClubs(): Result<List<String>> = runCatchingFirestore {
        clubs.get().await().documents.map { it.id }.sorted()
    }

    /** Admin only. Names can't contain "/" because they're used as document IDs. */
    suspend fun addClub(name: String): Result<Unit> = runCatchingFirestore {
        val trimmed = name.trim()
        require(isValidName(trimmed)) { "Club names can't be empty or contain \"/\"" }
        clubs.document(trimmed).set(mapOf(FIELD_NAME to trimmed)).await()
    }

    /** Admin only. Existing events and organizers keep the name, but no new ones can use it. */
    suspend fun deleteClub(name: String): Result<Unit> = runCatchingFirestore {
        clubs.document(name).delete().await()
    }

    private suspend fun <T> runCatchingFirestore(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private const val COLLECTION_CLUBS = "clubs"
        private const val FIELD_NAME = "name"

        // Firestore document IDs can't contain "/" or be "." or "..".
        fun isValidName(name: String): Boolean =
            name.isNotBlank() && '/' !in name && name != "." && name != ".."
    }
}
