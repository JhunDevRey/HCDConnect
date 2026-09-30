package com.hcdc.hcdconnect.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the signed-in user may do. The security rules enforce the same model; this only
 * decides what the UI offers.
 */
data class UserRoles(
    val isAdmin: Boolean = false,
    val isOrganizer: Boolean = false,
    // The organizer's club, or null if they haven't joined one yet.
    val organizerClub: String? = null
) {
    /** Admins can post for any club; organizers need a club first. */
    val canPostEvents: Boolean get() = isAdmin || (isOrganizer && organizerClub != null)

    /** Shows the "New event" button: admins, and organizers (who pick a club first if needed). */
    val showsNewEventButton: Boolean get() = isAdmin || isOrganizer

    /** Whether this user can edit or delete [createdBy]'s event for [club]. */
    fun canManageEvent(userId: String?, createdBy: String, club: String): Boolean =
        isAdmin || (userId != null && createdBy == userId && organizerClub == club)
}

class RoleRepository(
    private val adminRepository: AdminRepository = AdminRepository(),
    private val organizerRepository: OrganizerRepository = OrganizerRepository()
) {

    suspend fun getRoles(userId: String): Result<UserRoles> = coroutineScope {
        val admin = async { adminRepository.isAdmin(userId) }
        val organizer = async { organizerRepository.getOrganizer(userId) }
        val adminResult = admin.await()
        val organizerResult = organizer.await()
        val error = adminResult.exceptionOrNull() ?: organizerResult.exceptionOrNull()
        if (error != null) {
            Result.failure(error)
        } else {
            val entry = organizerResult.getOrNull()
            Result.success(
                UserRoles(
                    isAdmin = adminResult.getOrDefault(false),
                    isOrganizer = entry != null,
                    organizerClub = entry?.club
                )
            )
        }
    }
}
