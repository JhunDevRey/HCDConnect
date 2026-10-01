package com.hcdc.hcdconnect.ui.eventdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.CampusEventRepository
import com.hcdc.hcdconnect.data.repository.RoleRepository
import com.hcdc.hcdconnect.data.repository.UserRepository
import com.hcdc.hcdconnect.data.repository.UserRoles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Success(
        val event: CampusEvent,
        // Admins can edit and delete any event; organizers only their own, in their club.
        val canManage: Boolean,
        val isGoing: Boolean,
        // Only admins can see who RSVP'd; everyone else sees just the count.
        val canSeeAttendees: Boolean
    ) : EventDetailUiState
    data class Error(val message: String) : EventDetailUiState
}

/** One-off actions and their results, separate from the live event data. */
data class EventDetailActionState(
    val isUpdatingRsvp: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoadingAttendees: Boolean = false,
    // Emails of the people going, for the admin's list; cleared with onAttendeesShown().
    val attendees: List<String>? = null,
    // One-off message (string resource ID); cleared with onMessageShown().
    val message: Int? = null
)

// @JvmOverloads gives the default ViewModel factory the (SavedStateHandle) constructor it looks for.
class EventDetailViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CampusEventRepository = CampusEventRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val roleRepository: RoleRepository = RoleRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    // Read from the launching Intent's extras, and kept across process death.
    private val eventId: String? = savedStateHandle[EventDetailActivity.EXTRA_EVENT_ID]
    private val retries = MutableStateFlow(0)
    private val roles = MutableStateFlow(UserRoles())

    private val _actionState = MutableStateFlow(EventDetailActionState())
    val actionState: StateFlow<EventDetailActionState> = _actionState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<EventDetailUiState> = retries
        .flatMapLatest {
            val id = eventId
            if (id.isNullOrBlank()) {
                flowOf(EventDetailUiState.Error("Missing event ID"))
            } else {
                val userId = authRepository.currentUser?.uid
                repository.observeEvent(id)
                    .combine(roles) { result, userRoles -> result to userRoles }
                    .map { (result, userRoles) ->
                        result.fold(
                            onSuccess = { event ->
                                EventDetailUiState.Success(
                                    event = event,
                                    canManage = userRoles.canManageEvent(
                                        userId, event.createdBy, event.organizerClub
                                    ),
                                    isGoing = event.isGoing(userId),
                                    canSeeAttendees = userRoles.isAdmin
                                )
                            },
                            onFailure = { EventDetailUiState.Error(it.message ?: "Failed to load event") }
                        )
                    }
                    .onStart { emit(EventDetailUiState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState.Loading)

    init {
        authRepository.currentUser?.uid?.let { userId ->
            viewModelScope.launch { roleRepository.getRoles(userId).onSuccess { roles.value = it } }
        }
    }

    fun retry() {
        retries.value++
    }

    fun toggleGoing() {
        val state = uiState.value as? EventDetailUiState.Success ?: return
        val userId = authRepository.currentUser?.uid ?: return
        if (_actionState.value.isUpdatingRsvp || !state.event.acceptsRsvps()) return

        _actionState.update { it.copy(isUpdatingRsvp = true) }
        viewModelScope.launch {
            val result = repository.setGoing(state.event.eventId, userId, going = !state.isGoing)
            _actionState.update {
                it.copy(
                    isUpdatingRsvp = false,
                    message = if (result.isFailure) R.string.error_rsvp else null
                )
            }
            // On success the live listener delivers the new attendee list.
        }
    }

    fun delete() {
        val state = uiState.value as? EventDetailUiState.Success ?: return
        if (_actionState.value.isDeleting) return

        _actionState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = repository.deleteEvent(state.event.eventId)
            _actionState.update {
                result.fold(
                    onSuccess = { _ -> it.copy(isDeleting = false, isDeleted = true) },
                    onFailure = { e ->
                        val denied = e is FirebaseFirestoreException &&
                            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        it.copy(
                            isDeleting = false,
                            message = if (denied) R.string.error_not_club_organizer else R.string.error_delete_event
                        )
                    }
                )
            }
        }
    }

    fun showAttendees() {
        val state = uiState.value as? EventDetailUiState.Success ?: return
        if (!state.canSeeAttendees || _actionState.value.isLoadingAttendees) return

        _actionState.update { it.copy(isLoadingAttendees = true) }
        viewModelScope.launch {
            val result = userRepository.getUsers(state.event.attendees)
            _actionState.update {
                result.fold(
                    onSuccess = { users ->
                        it.copy(
                            isLoadingAttendees = false,
                            attendees = users.map { user -> user.email.ifBlank { user.userId } }
                                .sortedBy { email -> email.lowercase() }
                        )
                    },
                    onFailure = { _ -> it.copy(isLoadingAttendees = false, message = R.string.error_load_attendees) }
                )
            }
        }
    }

    fun onAttendeesShown() = _actionState.update { it.copy(attendees = null) }

    fun onMessageShown() = _actionState.update { it.copy(message = null) }
}
