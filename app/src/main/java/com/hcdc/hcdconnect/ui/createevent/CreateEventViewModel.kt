package com.hcdc.hcdconnect.ui.createevent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.model.EventStatus
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.CampusEventRepository
import com.hcdc.hcdconnect.data.repository.ClubRepository
import com.hcdc.hcdconnect.data.repository.RoleRepository
import com.hcdc.hcdconnect.ui.common.CampusTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Date

enum class FormField { TITLE, ORGANIZER, LOCATION, DATE, TIME }

/** Text field values for the screen to fill in once, when editing an existing event. */
data class FormPrefill(val title: String, val location: String, val description: String)

data class CreateEventUiState(
    val isEditMode: Boolean = false,
    // Clubs this organizer can post for, loaded from the organizers list.
    val clubs: List<String> = emptyList(),
    val club: String? = null,
    // Only editable in edit mode; new events are always Upcoming.
    val status: EventStatus = EventStatus.UPCOMING,
    // True until clubs (and, when editing, the event) have loaded.
    val isLoading: Boolean = true,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    // Maps each invalid field to a string resource ID for its error message.
    val fieldErrors: Map<FormField, Int> = emptyMap(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    // One-off; cleared with onPrefillApplied() once the text fields are filled in.
    val prefill: FormPrefill? = null,
    // One-off message; cleared with onErrorShown() once displayed.
    val errorMessage: String? = null,
    // One-off message from a string resource; also cleared with onErrorShown().
    val errorMessageRes: Int? = null
)

// @JvmOverloads gives the default ViewModel factory the (SavedStateHandle) constructor it looks for.
class CreateEventViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CampusEventRepository = CampusEventRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val roleRepository: RoleRepository = RoleRepository(),
    private val clubRepository: ClubRepository = ClubRepository()
) : ViewModel() {

    // Set when editing; null when creating a new event.
    private val editEventId: String? = savedStateHandle[CreateEventActivity.EXTRA_EVENT_ID]

    // The event's start time when editing began, so an unchanged past time isn't rejected.
    private var originalStart: Instant? = null

    private val _uiState = MutableStateFlow(CreateEventUiState(isEditMode = editEventId != null))
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, errorMessageRes = R.string.error_signed_out) }
            return
        }
        viewModelScope.launch {
            // Admins can post for any listed club; organizers only for their own.
            val clubs = roleRepository.getRoles(userId).mapCatching { roles ->
                when {
                    roles.isAdmin -> clubRepository.getClubs().getOrThrow()
                    else -> listOfNotNull(roles.organizerClub)
                }
            }.getOrElse { e ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load your clubs")
                }
                return@launch
            }

            val id = editEventId
            if (id == null) {
                _uiState.update {
                    it.copy(
                        clubs = clubs,
                        // Pre-select when there's only one choice.
                        club = clubs.singleOrNull(),
                        isLoading = false,
                        errorMessageRes = if (clubs.isEmpty()) R.string.error_not_organizer else null
                    )
                }
                return@launch
            }

            repository.getEvent(id).fold(
                onSuccess = { event -> applyEvent(event, clubs) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load the event")
                    }
                }
            )
        }
    }

    private fun applyEvent(event: CampusEvent, clubs: List<String>) {
        val zone = CampusTime.ZONE
        val start = event.date?.toDate()?.toInstant()?.truncatedTo(ChronoUnit.MINUTES)
        originalStart = start
        _uiState.update {
            it.copy(
                // Keep the event's club listed even if the organizer lost it, so the form shows
                // what's saved; the rules will refuse the save and the error explains why.
                clubs = (clubs + event.organizerClub).filter { c -> c.isNotBlank() }.distinct(),
                club = event.organizerClub.ifBlank { null },
                status = event.status,
                date = start?.atZone(zone)?.toLocalDate(),
                time = start?.atZone(zone)?.toLocalTime(),
                prefill = FormPrefill(event.title, event.location, event.description),
                isLoading = false
            )
        }
    }

    fun onPrefillApplied() = _uiState.update { it.copy(prefill = null) }

    fun onClubSelected(club: String) = _uiState.update {
        it.copy(club = club, fieldErrors = it.fieldErrors - FormField.ORGANIZER)
    }

    fun onStatusSelected(status: EventStatus) = _uiState.update { it.copy(status = status) }

    fun onDateSelected(date: LocalDate) = _uiState.update {
        it.copy(date = date, fieldErrors = it.fieldErrors - FormField.DATE)
    }

    fun onTimeSelected(time: LocalTime) = _uiState.update {
        // A new time can fix a "date in the past" error, so clear DATE too.
        it.copy(time = time, fieldErrors = it.fieldErrors - FormField.TIME - FormField.DATE)
    }

    fun onFieldEdited(field: FormField) = _uiState.update {
        if (field in it.fieldErrors) it.copy(fieldErrors = it.fieldErrors - field) else it
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null, errorMessageRes = null) }

    fun save(title: String, location: String, description: String) {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return

        val errors = mutableMapOf<FormField, Int>()
        if (title.isBlank()) errors[FormField.TITLE] = R.string.error_required
        val club = state.club
        if (club == null) errors[FormField.ORGANIZER] = R.string.error_required
        if (location.isBlank()) errors[FormField.LOCATION] = R.string.error_required
        if (state.date == null) errors[FormField.DATE] = R.string.error_required
        if (state.time == null) errors[FormField.TIME] = R.string.error_required

        val startsAt = startInstant(state.date, state.time)
        // A new or changed start time must be in the future. An unchanged one may have passed,
        // so organizers can still fix typos or cancel an event that already started.
        if (startsAt != null && startsAt != originalStart && !startsAt.isAfter(Instant.now())) {
            errors[FormField.DATE] = R.string.error_date_in_past
        }

        if (errors.isNotEmpty() || startsAt == null || club == null) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(errorMessageRes = R.string.error_signed_out) }
            return
        }

        _uiState.update { it.copy(isSaving = true, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val timestamp = Timestamp(Date.from(startsAt))
            val id = editEventId
            val result = if (id == null) {
                repository.createEvent(
                    CampusEvent(
                        title = title.trim(),
                        organizerClub = club,
                        date = timestamp,
                        location = location.trim(),
                        description = description.trim(),
                        status = EventStatus.UPCOMING,
                        createdBy = userId
                    )
                ).map { }
            } else {
                repository.updateEvent(
                    eventId = id,
                    title = title.trim(),
                    organizerClub = club,
                    date = timestamp,
                    location = location.trim(),
                    description = description.trim(),
                    status = state.status
                )
            }
            result.fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                onFailure = { e ->
                    _uiState.update {
                        if (e is FirebaseFirestoreException &&
                            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            // The rules refused: no longer an organizer, their club changed, or the
                            // club was removed from the list.
                            it.copy(isSaving = false, errorMessageRes = R.string.error_not_club_organizer)
                        } else {
                            it.copy(isSaving = false, errorMessage = e.message ?: "Unknown error")
                        }
                    }
                }
            )
        }
    }

    // Interprets the picked date and time as campus time, whatever the phone's time zone.
    private fun startInstant(date: LocalDate?, time: LocalTime?): Instant? =
        if (date != null && time != null) {
            date.atTime(time).atZone(CampusTime.ZONE).toInstant()
        } else {
            null
        }
}
