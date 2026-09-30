package com.hcdc.hcdconnect.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * A campus event stored in the Firestore `events` collection.
 *
 * Every property has a default value so Firestore can build the object with
 * `toObject()`, which needs a no-argument constructor.
 */
data class CampusEvent(
    // Filled from the document's ID, not stored as a field.
    @DocumentId
    val eventId: String = "",
    val title: String = "",
    val organizerClub: String = "",
    // Stored as a Firestore Timestamp so it can be sorted and filtered by date.
    val date: Timestamp? = null,
    val location: String = "",
    val description: String = "",
    // Stored as a string, e.g. "UPCOMING". Firestore maps enums by name.
    val status: EventStatus = EventStatus.UPCOMING,
    // Firebase Auth UID of the user who created the event. Empty for events added in the console.
    val createdBy: String = "",
    // UIDs of users who RSVP'd "going".
    val attendees: List<String> = emptyList()
) {
    // Functions, unlike properties, aren't written to Firestore.

    /** The status to show. Events whose start time has passed show as Completed unless cancelled. */
    fun displayStatus(now: Date = Date()): EventStatus = when {
        status == EventStatus.CANCELLED -> EventStatus.CANCELLED
        date != null && date.toDate().before(now) -> EventStatus.COMPLETED
        else -> status
    }

    fun isGoing(userId: String?): Boolean = userId != null && userId in attendees

    /** Whether people can still RSVP: not cancelled and not already over. */
    fun acceptsRsvps(now: Date = Date()): Boolean =
        displayStatus(now).let { it == EventStatus.UPCOMING || it == EventStatus.ONGOING }
}

enum class EventStatus(val label: String) {
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}
