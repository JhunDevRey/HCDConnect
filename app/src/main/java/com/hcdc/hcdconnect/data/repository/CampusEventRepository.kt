package com.hcdc.hcdconnect.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.model.EventStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CampusEventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val events get() = firestore.collection(COLLECTION_EVENTS)

    /**
     * Live list of events dated from now onward, soonest first. Emits again whenever
     * matching events change. "Now" is fixed when collection starts, so re-collect to
     * drop events that have since started.
     */
    fun observeUpcomingEvents(): Flow<Result<List<CampusEvent>>> = observeQuery(
        events.whereGreaterThanOrEqualTo(FIELD_DATE, Timestamp.now())
            .orderBy(FIELD_DATE, Query.Direction.ASCENDING)
    )

    /** Live list of the most recent past events, newest first. */
    fun observePastEvents(): Flow<Result<List<CampusEvent>>> = observeQuery(
        events.whereLessThan(FIELD_DATE, Timestamp.now())
            .orderBy(FIELD_DATE, Query.Direction.DESCENDING)
            .limit(PAST_EVENTS_LIMIT)
    )

    /** Live single event. Fails with [NoSuchElementException] if it doesn't exist or is deleted. */
    fun observeEvent(eventId: String): Flow<Result<CampusEvent>> = callbackFlow {
        val registration = events.document(eventId).addSnapshotListener { snapshot, error ->
            val result = when {
                error != null -> Result.failure(error)
                snapshot == null -> return@addSnapshotListener
                else -> snapshot.toObject(CampusEvent::class.java)
                    ?.let { Result.success(it) }
                    ?: Result.failure(NoSuchElementException("This event no longer exists"))
            }
            trySend(result)
        }
        awaitClose { registration.remove() }
    }

    /** Fetches a single event once. Fails if the document doesn't exist. */
    suspend fun getEvent(eventId: String): Result<CampusEvent> = runCatchingFirestore {
        events.document(eventId).get().await().toObject(CampusEvent::class.java)
            ?: throw NoSuchElementException("This event no longer exists")
    }

    /**
     * Adds [event] as a new document and returns its generated ID.
     * [CampusEvent.eventId] is left out of the document because of @DocumentId.
     */
    suspend fun createEvent(event: CampusEvent): Result<String> = runCatchingFirestore {
        events.add(event).await().id
    }

    /** Updates an event's editable fields. RSVPs and the creator are left unchanged. */
    suspend fun updateEvent(
        eventId: String,
        title: String,
        organizerClub: String,
        date: Timestamp,
        location: String,
        description: String,
        status: EventStatus
    ): Result<Unit> = runCatchingFirestore {
        events.document(eventId).update(
            mapOf(
                "title" to title,
                "organizerClub" to organizerClub,
                FIELD_DATE to date,
                "location" to location,
                "description" to description,
                "status" to status.name
            )
        ).await()
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> = runCatchingFirestore {
        events.document(eventId).delete().await()
    }

    /**
     * Adds or removes [userId] from the event's attendees. The security rules only let a
     * user change their own entry, and only the attendees field.
     */
    suspend fun setGoing(eventId: String, userId: String, going: Boolean): Result<Unit> =
        runCatchingFirestore {
            val change = if (going) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
            events.document(eventId).update(FIELD_ATTENDEES, change).await()
        }

    private fun observeQuery(query: Query): Flow<Result<List<CampusEvent>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.toObjects(CampusEvent::class.java)))
            }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun <T> runCatchingFirestore(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val COLLECTION_EVENTS = "events"
        const val FIELD_DATE = "date"
        const val FIELD_ATTENDEES = "attendees"
        const val PAST_EVENTS_LIMIT = 50L
    }
}
