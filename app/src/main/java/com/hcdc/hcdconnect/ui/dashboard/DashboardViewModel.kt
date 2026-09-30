package com.hcdc.hcdconnect.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.CampusEventRepository
import com.hcdc.hcdconnect.data.repository.ClubRepository
import com.hcdc.hcdconnect.data.repository.OrganizerRepository
import com.hcdc.hcdconnect.data.repository.RoleRepository
import com.hcdc.hcdconnect.data.repository.UserRoles
import com.hcdc.hcdconnect.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EventsTab { UPCOMING, PAST }

sealed interface EventFilter {
    data object All : EventFilter
    data object Going : EventFilter
    data class Club(val name: String) : EventFilter
}

data class DashboardUiState(
    val tab: EventsTab = EventsTab.UPCOMING,
    val filter: EventFilter = EventFilter.All,
    // Text typed in the search box; blank means no search.
    val query: String = "",
    // Clubs to offer as filter chips, from the events in the current tab.
    val clubs: List<String> = emptyList(),
    val events: List<CampusEvent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentUserId: String? = null,
    // Every upcoming event, unfiltered, for keeping reminders in sync. Null on the Past tab.
    val reminderEvents: List<CampusEvent>? = null
)

/** One-off results of the "join a club" step, shown once and then cleared. */
data class DashboardActionState(
    val isJoining: Boolean = false,
    // Listed clubs to choose from; non-null asks the screen to show the picker.
    val joinableClubs: List<String>? = null,
    // True once the organizer has a club, asking the screen to open the event form.
    val openCreateEvent: Boolean = false,
    // One-off message (string resource ID).
    val message: Int? = null
)

class DashboardViewModel(
    private val repository: CampusEventRepository = CampusEventRepository(),
    private val organizerRepository: OrganizerRepository = OrganizerRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val roleRepository: RoleRepository = RoleRepository(),
    private val clubRepository: ClubRepository = ClubRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val tab = MutableStateFlow(EventsTab.UPCOMING)
    private val filter = MutableStateFlow<EventFilter>(EventFilter.All)
    private val query = MutableStateFlow("")
    // Bumped by refresh() to restart the listener. That moves "now", dropping events that have started.
    private val refreshes = MutableStateFlow(0)

    // Starts with no roles so admin and organizer controls never flash for other users.
    private val _roles = MutableStateFlow(UserRoles())
    val roles: StateFlow<UserRoles> = _roles.asStateFlow()

    private val _actionState = MutableStateFlow(DashboardActionState())
    val actionState: StateFlow<DashboardActionState> = _actionState.asStateFlow()

    // null while the first snapshot for the current tab is loading.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsResult: Flow<Result<List<CampusEvent>>?> =
        combine(tab, refreshes) { selectedTab, _ -> selectedTab }
            .flatMapLatest { selectedTab ->
                val source = when (selectedTab) {
                    EventsTab.UPCOMING -> repository.observeUpcomingEvents()
                    EventsTab.PAST -> repository.observePastEvents()
                }
                source.onStart<Result<List<CampusEvent>>?> { emit(null) }
            }

    val uiState: StateFlow<DashboardUiState> =
        combine(tab, filter, query, eventsResult) { selectedTab, selectedFilter, search, result ->
            val userId = authRepository.currentUser?.uid
            val allEvents = result?.getOrNull().orEmpty()
            val clubs = allEvents.map { it.organizerClub }
                .filter { it.isNotBlank() }
                .plus((selectedFilter as? EventFilter.Club)?.name)
                .filterNotNull()
                .distinct()
                .sorted()
            DashboardUiState(
                tab = selectedTab,
                filter = selectedFilter,
                clubs = clubs,
                query = search,
                events = allEvents.filter { it.matches(selectedFilter, userId) && it.matchesSearch(search) },
                isLoading = result == null,
                errorMessage = result?.exceptionOrNull()?.let { it.message ?: "Failed to load events" },
                currentUserId = userId,
                reminderEvents = if (selectedTab == EventsTab.UPCOMING && result?.isSuccess == true) allEvents else null
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        recordSignIn()
        checkRoles()
    }

    fun selectTab(newTab: EventsTab) {
        tab.value = newTab
    }

    fun selectFilter(newFilter: EventFilter) {
        filter.value = newFilter
    }

    fun search(text: String) {
        query.value = text
    }

    /** Restarts the event listener and rechecks roles, so a newly granted role shows up. */
    fun refresh() {
        refreshes.value++
        checkRoles()
    }

    // Keeps the user's profile current so admins can find them in "Manage organizers".
    // Failure only affects the admin list, so it's ignored here.
    private fun recordSignIn() {
        val user = authRepository.currentUser ?: return
        val email = user.email ?: return
        viewModelScope.launch { userRepository.recordSignIn(user.uid, email) }
    }

    private fun checkRoles() {
        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            _roles.value = UserRoles()
            return
        }
        // If the check fails (e.g. offline), keep the previous roles rather than hiding things.
        viewModelScope.launch {
            roleRepository.getRoles(userId).onSuccess { _roles.value = it }
        }
    }

    /**
     * The "New event" button. Admins, and organizers who have a club, go straight to the
     * form. Organizers without a club first pick one from the listed clubs.
     */
    fun onNewEventClicked() {
        val roles = _roles.value
        when {
            roles.canPostEvents -> _actionState.update { it.copy(openCreateEvent = true) }
            roles.isOrganizer -> viewModelScope.launch {
                clubRepository.getClubs().fold(
                    onSuccess = { clubs ->
                        _actionState.update {
                            if (clubs.isEmpty()) it.copy(message = R.string.no_clubs_to_join)
                            else it.copy(joinableClubs = clubs)
                        }
                    },
                    onFailure = { _actionState.update { it.copy(message = R.string.error_join_club) } }
                )
            }
        }
    }

    /** The organizer picked [club]. It can't be changed afterwards except by an admin. */
    fun joinClub(club: String) {
        val userId = authRepository.currentUser?.uid ?: return
        if (_actionState.value.isJoining) return
        _actionState.update { it.copy(isJoining = true, joinableClubs = null) }
        viewModelScope.launch {
            val result = organizerRepository.joinClub(userId, club)
            if (result.isSuccess) {
                _roles.update { it.copy(organizerClub = club) }
            }
            _actionState.update {
                it.copy(
                    isJoining = false,
                    openCreateEvent = result.isSuccess,
                    message = if (result.isSuccess) null else R.string.error_join_club
                )
            }
        }
    }

    fun onJoinPickerShown() = _actionState.update { it.copy(joinableClubs = null) }

    fun onCreateEventOpened() = _actionState.update { it.copy(openCreateEvent = false) }

    fun onMessageShown() = _actionState.update { it.copy(message = null) }

    /** Case-insensitive match on every search word against the title, club, location or description. */
    private fun CampusEvent.matchesSearch(search: String): Boolean {
        val words = search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = listOf(title, organizerClub, location, description).joinToString(" ")
        return words.all { haystack.contains(it, ignoreCase = true) }
    }

    private fun CampusEvent.matches(filter: EventFilter, userId: String?): Boolean = when (filter) {
        EventFilter.All -> true
        EventFilter.Going -> isGoing(userId)
        is EventFilter.Club -> organizerClub == filter.name
    }
}
