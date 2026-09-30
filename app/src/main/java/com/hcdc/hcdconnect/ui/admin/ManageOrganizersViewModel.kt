package com.hcdc.hcdconnect.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.repository.AdminRepository
import com.hcdc.hcdconnect.data.repository.AppUser
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.ClubRepository
import com.hcdc.hcdconnect.data.repository.Organizer
import com.hcdc.hcdconnect.data.repository.OrganizerRepository
import com.hcdc.hcdconnect.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A user and their roles, as shown in the admin list. */
data class UserRow(
    val user: AppUser,
    val isAdmin: Boolean,
    val isOrganizer: Boolean,
    // The organizer's club; null if they aren't an organizer or haven't joined one yet.
    val club: String?,
    // The signed-in admin can't remove their own admin access (the rules block it too).
    val isSelf: Boolean
)

data class ManageUsersUiState(
    val rows: List<UserRow> = emptyList(),
    // The official club list, for assigning clubs and for the Clubs dialog.
    val clubs: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class ManageUsersActionState(
    val isSaving: Boolean = false,
    // One-off message (string resource ID); cleared with onMessageShown().
    val message: Int? = null
)

class ManageOrganizersViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val organizerRepository: OrganizerRepository = OrganizerRepository(),
    private val adminRepository: AdminRepository = AdminRepository(),
    private val clubRepository: ClubRepository = ClubRepository(),
    authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val currentUserId = authRepository.currentUser?.uid
    private val query = MutableStateFlow("")

    private val _actionState = MutableStateFlow(ManageUsersActionState())
    val actionState: StateFlow<ManageUsersActionState> = _actionState.asStateFlow()

    val uiState: StateFlow<ManageUsersUiState> = combine(
        userRepository.observeUsers().onStart<Result<List<AppUser>>?> { emit(null) },
        organizerRepository.observeAllOrganizers().onStart<Result<List<Organizer>>?> { emit(null) },
        adminRepository.observeAdminIds().onStart<Result<Set<String>>?> { emit(null) },
        clubRepository.observeClubs().onStart<Result<List<String>>?> { emit(null) },
        query
    ) { usersResult, organizersResult, adminsResult, clubsResult, search ->
        if (usersResult == null || organizersResult == null || adminsResult == null || clubsResult == null) {
            return@combine ManageUsersUiState(isLoading = true)
        }
        val error = listOf(usersResult, organizersResult, adminsResult, clubsResult)
            .firstNotNullOfOrNull { it.exceptionOrNull() }
        if (error != null) {
            return@combine ManageUsersUiState(isLoading = false, errorMessage = error.message ?: "Failed to load")
        }

        val organizers = organizersResult.getOrThrow().associateBy { it.userId }
        val adminIds = adminsResult.getOrThrow()
        val users = usersResult.getOrThrow()
        // Organizers added before profiles existed may have no users document yet, so list
        // them too, using the email saved with their organizer entry.
        val knownIds = users.map { it.userId }.toSet()
        val organizersWithoutProfile = organizers.values
            .filter { it.userId !in knownIds }
            .map { AppUser(userId = it.userId, email = it.email.ifBlank { it.userId }) }

        val rows = (users + organizersWithoutProfile)
            .filter { search.isBlank() || it.email.contains(search.trim(), ignoreCase = true) }
            .map { user ->
                val organizer = organizers[user.userId]
                UserRow(
                    user = user,
                    isAdmin = user.userId in adminIds,
                    isOrganizer = organizer != null,
                    club = organizer?.club,
                    isSelf = user.userId == currentUserId
                )
            }
            // Admins, then organizers, then everyone else, each by email.
            .sortedWith(compareBy<UserRow>({ !it.isAdmin }, { !it.isOrganizer }, { it.user.email }))

        ManageUsersUiState(rows = rows, clubs = clubsResult.getOrThrow(), isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUsersUiState())

    fun search(text: String) {
        query.value = text
    }

    /** Saves [row]'s roles. A non-organizer has no club; an organizer may have none yet. */
    fun saveRoles(row: UserRow, isAdmin: Boolean, isOrganizer: Boolean, club: String?) {
        runAction(R.string.roles_saved, R.string.error_saving_roles) {
            val user = row.user
            if (isAdmin != row.isAdmin) {
                adminRepository.setAdmin(user.userId, user.email, isAdmin).getOrThrow()
            }
            when {
                isOrganizer && (!row.isOrganizer || club != row.club) ->
                    organizerRepository.setOrganizer(user.userId, user.email, club).getOrThrow()
                !isOrganizer && row.isOrganizer ->
                    organizerRepository.removeOrganizer(user.userId).getOrThrow()
            }
        }
    }

    fun addClub(name: String) {
        if (!ClubRepository.isValidName(name.trim())) {
            _actionState.update { it.copy(message = R.string.error_club_name) }
            return
        }
        runAction(R.string.club_added, R.string.error_saving_club) {
            clubRepository.addClub(name).getOrThrow()
        }
    }

    fun deleteClub(name: String) {
        runAction(R.string.club_deleted, R.string.error_saving_club) {
            clubRepository.deleteClub(name).getOrThrow()
        }
    }

    fun onMessageShown() = _actionState.update { it.copy(message = null) }

    private fun runAction(successMessage: Int, failureMessage: Int, block: suspend () -> Unit) {
        if (_actionState.value.isSaving) return
        _actionState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val ok = try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _actionState.update {
                it.copy(isSaving = false, message = if (ok) successMessage else failureMessage)
            }
        }
    }
}
