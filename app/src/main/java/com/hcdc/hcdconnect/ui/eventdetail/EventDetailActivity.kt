package com.hcdc.hcdconnect.ui.eventdetail

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.ui.common.setUpBrandedSystemBars
import com.hcdc.hcdconnect.ui.common.CampusTime
import com.hcdc.hcdconnect.MainActivity
import androidx.activity.OnBackPressedCallback
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.reminders.EventReminderScheduler
import com.hcdc.hcdconnect.reminders.ReminderNotifications
import com.hcdc.hcdconnect.databinding.ActivityEventDetailBinding
import com.hcdc.hcdconnect.ui.createevent.CreateEventActivity
import com.hcdc.hcdconnect.ui.dashboard.EventStatusColors
import kotlinx.coroutines.launch
import java.text.DateFormat

class EventDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailBinding
    private val viewModel: EventDetailViewModel by viewModels()

    private val dateFormat: DateFormat =
        CampusTime.dateTimeFormat(DateFormat.FULL, DateFormat.SHORT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpBrandedSystemBars(binding.root, binding.toolbar)
        onBackPressedDispatcher.addCallback(this, backToDashboard)

        with(binding) {
            toolbar.setNavigationOnClickListener { navigateUp() }
            toolbar.inflateMenu(R.menu.menu_event_detail)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_add_to_calendar -> {
                        currentEvent()?.let(::addToCalendar)
                        true
                    }
                    R.id.action_share -> {
                        currentEvent()?.let(::shareEvent)
                        true
                    }
                    R.id.action_edit -> {
                        eventId()?.let { startActivity(CreateEventActivity.newEditIntent(this@EventDetailActivity, it)) }
                        true
                    }
                    R.id.action_delete -> {
                        confirmDelete()
                        true
                    }
                    else -> false
                }
            }
            buttonRetry.setOnClickListener { viewModel.retry() }
            buttonRsvp.setOnClickListener { onRsvpClicked() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.actionState.collect(::renderActions) }
            }
        }
    }

    private fun eventId(): String? = intent.getStringExtra(EXTRA_EVENT_ID)

    // If this screen was opened on its own (e.g. from a reminder after the app was closed),
    // Back and Up go to the dashboard instead of leaving the app.
    private val backToDashboard = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = navigateUp()
    }

    private fun navigateUp() {
        if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun currentEvent(): CampusEvent? = (viewModel.uiState.value as? EventDetailUiState.Success)?.event

    // Ask for notification permission the first time someone RSVPs, so reminders can show.
    // The RSVP goes ahead whatever they answer.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Snackbar.make(binding.root, R.string.reminders_off, Snackbar.LENGTH_LONG).show()
            }
        }

    private fun onRsvpClicked() {
        val state = viewModel.uiState.value as? EventDetailUiState.Success
        // canNotify() is only false on Android 13+, where the permission exists.
        if (state != null && !state.isGoing && !ReminderNotifications.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.toggleGoing()
    }

    /** Opens the calendar app with the event filled in. Events have no end time, so it assumes two hours. */
    private fun addToCalendar(event: CampusEvent) {
        val start = event.date?.toDate()?.time ?: return
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            .putExtra(CalendarContract.Events.DESCRIPTION, calendarDescription(event))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + DEFAULT_DURATION_MILLIS)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_calendar_app, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun calendarDescription(event: CampusEvent): String =
        listOf(event.organizerClub, event.description).filter { it.isNotBlank() }.joinToString("\n\n")

    private fun shareEvent(event: CampusEvent) {
        val details = listOfNotNull(
            event.title,
            event.organizerClub.ifBlank { null },
            event.date?.toDate()?.let(dateFormat::format),
            event.location.ifBlank { null },
            event.description.ifBlank { null }
        ).joinToString("\n")
        val text = getString(R.string.share_event_text, details)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share_event)
            )
        )
    }

    private fun render(state: EventDetailUiState) = with(binding) {
        progressBar.isVisible = state is EventDetailUiState.Loading
        scrollContent.isVisible = state is EventDetailUiState.Success
        groupError.isVisible = state is EventDetailUiState.Error

        val success = state as? EventDetailUiState.Success
        val canManage = success?.canManage == true
        toolbar.menu.findItem(R.id.action_add_to_calendar).isVisible = success?.event?.date != null
        toolbar.menu.findItem(R.id.action_share).isVisible = success != null
        toolbar.menu.findItem(R.id.action_edit).isVisible = canManage
        toolbar.menu.findItem(R.id.action_delete).isVisible = canManage

        when (state) {
            EventDetailUiState.Loading -> Unit
            is EventDetailUiState.Success -> bindEvent(state)
            is EventDetailUiState.Error ->
                textError.text = getString(R.string.error_loading_event, state.message)
        }
    }

    private fun bindEvent(state: EventDetailUiState.Success) = with(binding) {
        val event = state.event
        val status = event.displayStatus()
        textTitle.text = event.title
        textStatus.text = status.label
        textStatus.setTextColor(EventStatusColors.colorFor(root, status))
        textOrganizer.text = event.organizerClub
        textDate.text = event.date?.toDate()?.let(dateFormat::format)
            ?: getString(R.string.date_to_be_announced)
        textLocation.text = event.location
        textDescription.text = event.description.ifBlank { getString(R.string.no_description) }

        val count = event.attendees.size
        textGoingCount.text = if (count == 0) {
            getString(R.string.nobody_going_yet)
        } else {
            resources.getQuantityString(R.plurals.going_count, count, count)
        }
        bindRsvpButton(buttonRsvp, event, state.isGoing)

        // Keep this event's reminder in step with the RSVP and any edits to the event.
        EventReminderScheduler.sync(this@EventDetailActivity, event, AuthRepository().currentUser?.uid)
    }

    private fun bindRsvpButton(button: MaterialButton, event: CampusEvent, isGoing: Boolean) {
        val accepting = event.acceptsRsvps()
        button.isVisible = accepting || isGoing
        button.setText(if (isGoing) R.string.rsvp_cancel else R.string.rsvp_going)
        button.isEnabled = accepting && !viewModel.actionState.value.isUpdatingRsvp
    }

    private fun renderActions(state: EventDetailActionState) = with(binding) {
        (viewModel.uiState.value as? EventDetailUiState.Success)?.let {
            bindRsvpButton(buttonRsvp, it.event, it.isGoing)
        }
        state.message?.let {
            Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
            viewModel.onMessageShown()
        }
        if (state.isDeleted) {
            finish()
        }
    }

    private fun confirmDelete() {
        val title = (viewModel.uiState.value as? EventDetailUiState.Success)?.event?.title.orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_event_confirm_title)
            .setMessage(getString(R.string.delete_event_confirm_message, title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_event) { _, _ -> viewModel.delete() }
            .show()
    }

    companion object {
        const val EXTRA_EVENT_ID = "com.hcdc.hcdconnect.extra.EVENT_ID"
        private const val DEFAULT_DURATION_MILLIS = 2 * 60 * 60 * 1000L

        fun newIntent(context: Context, eventId: String): Intent =
            Intent(context, EventDetailActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, eventId)
    }
}
