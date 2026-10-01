package com.hcdc.hcdconnect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.ui.common.setUpBrandedSystemBars
import com.hcdc.hcdconnect.databinding.ActivityMainBinding
import com.hcdc.hcdconnect.reminders.EventReminderScheduler
import com.hcdc.hcdconnect.ui.admin.ManageOrganizersActivity
import com.hcdc.hcdconnect.ui.auth.LoginActivity
import com.hcdc.hcdconnect.ui.createevent.CreateEventActivity
import com.hcdc.hcdconnect.data.repository.UserRoles
import com.hcdc.hcdconnect.ui.dashboard.CampusEventAdapter
import com.hcdc.hcdconnect.ui.dashboard.DashboardActionState
import com.hcdc.hcdconnect.ui.dashboard.DashboardUiState
import com.hcdc.hcdconnect.ui.dashboard.DashboardViewModel
import com.hcdc.hcdconnect.ui.dashboard.EventFilter
import com.hcdc.hcdconnect.ui.dashboard.EventsTab
import com.hcdc.hcdconnect.ui.eventdetail.EventDetailActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DashboardViewModel by viewModels()
    private val authRepository = AuthRepository()

    private val eventAdapter = CampusEventAdapter { event ->
        startActivity(EventDetailActivity.newIntent(this, event.eventId))
    }

    // The list updates live, so the result is only used to confirm the save.
    private val createEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Snackbar.make(binding.root, R.string.event_created, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.fabNewEvent)
                    .show()
            }
        }

    // The filters the chips were last built from, so chips are only rebuilt when they change.
    private var shownChipFilters: List<EventFilter>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The dashboard is the launcher screen, so send signed-out users to login first.
        val user = authRepository.currentUser
        if (user == null) {
            startActivity(LoginActivity.newIntent(this))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpBrandedSystemBars(binding.root, binding.toolbar)
        binding.toolbar.menu.findItem(R.id.action_account).title =
            getString(R.string.signed_in_as, user.email.orEmpty())

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_manage_organizers -> {
                    startActivity(ManageOrganizersActivity.newIntent(this))
                    true
                }
                R.id.action_sign_out -> {
                    signOut()
                    true
                }
                else -> false
            }
        }

        setUpSearch()

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(if (tab.position == 0) EventsTab.UPCOMING else EventsTab.PAST)
                binding.recyclerEvents.scrollToPosition(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            viewModel.selectFilter(chip.tag as EventFilter)
        }

        binding.recyclerEvents.adapter = eventAdapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.fabNewEvent.setOnClickListener { viewModel.onNewEventClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.roles.collect { roles ->
                        if (roles.isRemoved) {
                            signOut(R.string.account_removed)
                            return@collect
                        }
                        binding.fabNewEvent.isVisible = roles.showsNewEventButton
                        binding.toolbar.menu.findItem(R.id.action_manage_organizers).isVisible = roles.isAdmin
                        binding.toolbar.subtitle = subtitleFor(roles)
                    }
                }
                launch { viewModel.actionState.collect(::renderActions) }
            }
        }
    }

    private fun render(state: DashboardUiState) = with(binding) {
        val tabIndex = if (state.tab == EventsTab.UPCOMING) 0 else 1
        if (tabs.selectedTabPosition != tabIndex) tabs.getTabAt(tabIndex)?.select()
        renderChips(state)

        progressBar.isVisible = state.isLoading && eventAdapter.itemCount == 0
        if (!state.isLoading) swipeRefresh.isRefreshing = false

        eventAdapter.currentUserId = state.currentUserId
        eventAdapter.submitList(state.events)
        // Keeps reminders in step with RSVPs and edits made anywhere, including other devices.
        state.reminderEvents?.let { EventReminderScheduler.syncAll(this@MainActivity, it, state.currentUserId) }

        val emptyText = when {
            state.isLoading -> null
            state.errorMessage != null -> getString(R.string.error_loading_events, state.errorMessage)
            state.events.isNotEmpty() -> null
            state.query.isNotBlank() -> getString(R.string.empty_search, state.query.trim())
            state.filter == EventFilter.Going -> getString(
                if (state.tab == EventsTab.UPCOMING) R.string.empty_going_upcoming else R.string.empty_going_past
            )
            state.filter is EventFilter.Club -> getString(R.string.empty_club, state.filter.name)
            state.tab == EventsTab.PAST -> getString(R.string.empty_past)
            else -> getString(R.string.no_upcoming_events)
        }
        textEmpty.text = emptyText
        textEmpty.isVisible = emptyText != null
    }

    private fun setUpSearch() {
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_events_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText.orEmpty())
                return true
            }
        })
        // Closing the search clears it, so the full list comes back.
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.search("")
                return true
            }
        })
    }

    /** Signs out and goes to the login screen, which shows [message] if given. */
    private fun signOut(@StringRes message: Int? = null) {
        // Reminders belong to this account's RSVPs.
        EventReminderScheduler.cancelAll(this)
        authRepository.signOut()
        startActivity(LoginActivity.newIntent(this, message))
        finish()
    }

    /** The role only; the email is in the menu, so the toolbar never cuts the role off. */
    private fun subtitleFor(roles: UserRoles): String? =
        when {
            roles.isAdmin -> getString(R.string.role_admin)
            roles.isOrganizer && roles.organizerClub != null ->
                getString(R.string.role_organizer_of, roles.organizerClub)
            roles.isOrganizer -> getString(R.string.role_organizer_no_club)
            roles.isStudent -> getString(R.string.role_student)
            else -> null
        }

    private fun renderActions(state: DashboardActionState) {
        state.joinableClubs?.let { clubs ->
            viewModel.onJoinPickerShown()
            showJoinClubPicker(clubs)
        }
        if (state.openCreateEvent) {
            viewModel.onCreateEventOpened()
            createEventLauncher.launch(CreateEventActivity.newIntent(this))
        }
        state.message?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).setAnchorView(binding.fabNewEvent).show()
            viewModel.onMessageShown()
        }
    }

    private fun showJoinClubPicker(clubs: List<String>) {
        var selected = -1
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.join_club_title)
            .setSingleChoiceItems(clubs.toTypedArray(), selected) { dialog, which ->
                selected = which
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.join_club) { _, _ ->
                if (selected >= 0) confirmJoinClub(clubs[selected])
            }
            .create()
        // Enabled once a club is picked.
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false }
        dialog.show()
    }

    // Organizers can join only once, so make the choice explicit.
    private fun confirmJoinClub(club: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.join_club_confirm_title, club))
            .setMessage(R.string.join_club_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.join_club) { _, _ -> viewModel.joinClub(club) }
            .show()
    }

    private fun renderChips(state: DashboardUiState) {
        val filters = listOf(EventFilter.All, EventFilter.Going) + state.clubs.map { EventFilter.Club(it) }
        val group = binding.chipGroupFilters
        if (filters != shownChipFilters) {
            group.removeAllViews()
            filters.forEach { filter ->
                val chip = layoutInflater.inflate(R.layout.chip_filter, group, false) as Chip
                chip.id = View.generateViewId()
                chip.tag = filter
                chip.text = when (filter) {
                    EventFilter.All -> getString(R.string.filter_all)
                    EventFilter.Going -> getString(R.string.filter_going)
                    is EventFilter.Club -> filter.name
                }
                group.addView(chip)
            }
            shownChipFilters = filters
        }
        // Checking the chip that's already checked doesn't fire the listener, so this doesn't loop.
        (0 until group.childCount)
            .map { group.getChildAt(it) as Chip }
            .firstOrNull { it.tag == state.filter }
            ?.let { if (!it.isChecked) group.check(it.id) }
    }

    companion object {
        /** Opens the dashboard as a fresh task, replacing the login screen. */
        fun newIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
