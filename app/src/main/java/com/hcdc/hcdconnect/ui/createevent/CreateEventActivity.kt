package com.hcdc.hcdconnect.ui.createevent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.ui.common.setUpBrandedSystemBars
import com.hcdc.hcdconnect.data.model.EventStatus
import com.hcdc.hcdconnect.databinding.ActivityCreateEventBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CreateEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private val viewModel: CreateEventViewModel by viewModels()

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    // Clubs currently in the dropdown, so it's only rebuilt when they change.
    private var shownClubs: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpBrandedSystemBars(binding.root, binding.toolbar, includeIme = true)

        setUpForm()
        reattachPickerListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun setUpForm() = with(binding) {
        toolbar.setNavigationOnClickListener { finish() }

        clearErrorOnEdit(editTitle, FormField.TITLE)
        dropdownClub.setOnItemClickListener { parent, _, position, _ ->
            viewModel.onClubSelected(parent.getItemAtPosition(position) as String)
        }
        dropdownStatus.setSimpleItems(STATUS_CHOICES.map { it.label }.toTypedArray())
        dropdownStatus.setOnItemClickListener { _, _, position, _ ->
            viewModel.onStatusSelected(STATUS_CHOICES[position])
        }
        clearErrorOnEdit(editLocation, FormField.LOCATION)

        editDate.setOnClickListener { showDatePicker() }
        layoutDate.setEndIconOnClickListener { showDatePicker() }
        editTime.setOnClickListener { showTimePicker() }
        layoutTime.setEndIconOnClickListener { showTimePicker() }

        buttonSave.setOnClickListener {
            viewModel.save(
                title = editTitle.text.toString(),
                location = editLocation.text.toString(),
                description = editDescription.text.toString()
            )
        }
    }

    private fun clearErrorOnEdit(editText: TextInputEditText, field: FormField) {
        editText.doAfterTextChanged { viewModel.onFieldEdited(field) }
    }

    private fun render(state: CreateEventUiState) = with(binding) {
        toolbar.setTitle(if (state.isEditMode) R.string.edit_event_title else R.string.create_event_title)
        buttonSave.setText(if (state.isEditMode) R.string.save_changes else R.string.save_event)
        layoutStatus.isVisible = state.isEditMode
        dropdownStatus.setText(state.status.label, false)

        state.prefill?.let {
            editTitle.setText(it.title)
            editLocation.setText(it.location)
            editDescription.setText(it.description)
            viewModel.onPrefillApplied()
        }

        if (state.clubs != shownClubs) {
            dropdownClub.setSimpleItems(state.clubs.toTypedArray())
            shownClubs = state.clubs
        }
        // false = don't filter the dropdown's items to match this text.
        dropdownClub.setText(state.club.orEmpty(), false)
        layoutOrganizer.isEnabled = !state.isLoading && state.clubs.isNotEmpty()

        editDate.setText(state.date?.format(dateFormatter).orEmpty())
        editTime.setText(state.time?.format(timeFormatter).orEmpty())

        layoutTitle.showError(state.fieldErrors[FormField.TITLE])
        layoutOrganizer.showError(state.fieldErrors[FormField.ORGANIZER])
        layoutLocation.showError(state.fieldErrors[FormField.LOCATION])
        layoutDate.showError(state.fieldErrors[FormField.DATE])
        layoutTime.showError(state.fieldErrors[FormField.TIME])

        buttonSave.isEnabled = !state.isSaving && !state.isLoading && state.clubs.isNotEmpty()
        progressSaving.isVisible = state.isSaving || state.isLoading

        val errorText = state.errorMessageRes?.let(::getString)
            ?: state.errorMessage?.let { getString(R.string.error_saving_event, it) }
        errorText?.let {
            Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
            viewModel.onErrorShown()
        }

        if (state.isSaved) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun TextInputLayout.showError(messageRes: Int?) {
        error = messageRes?.let(::getString)
    }

    // The keyboard would otherwise stay open and cover the picker's buttons.
    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        WindowCompat.getInsetsController(window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun showDatePicker() {
        if (supportFragmentManager.findFragmentByTag(TAG_DATE_PICKER) != null) return
        hideKeyboard()

        // MaterialDatePicker works in UTC milliseconds at the start of the selected day.
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val selection = viewModel.uiState.value.date
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: today

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.select_date)
            .setSelection(selection)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(today))
                    .build()
            )
            .build()
        attachDateListener(picker)
        picker.show(supportFragmentManager, TAG_DATE_PICKER)
    }

    private fun showTimePicker() {
        if (supportFragmentManager.findFragmentByTag(TAG_TIME_PICKER) != null) return
        hideKeyboard()

        val current = viewModel.uiState.value.time ?: DEFAULT_TIME
        val format = if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        val picker = MaterialTimePicker.Builder()
            .setTitleText(R.string.select_time)
            .setTimeFormat(format)
            .setHour(current.hour)
            .setMinute(current.minute)
            .build()
        attachTimeListener(picker)
        picker.show(supportFragmentManager, TAG_TIME_PICKER)
    }

    private fun attachDateListener(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    private fun attachTimeListener(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            viewModel.onTimeSelected(LocalTime.of(picker.hour, picker.minute))
        }
    }

    // Picker dialogs survive rotation but their listeners don't, so reconnect them.
    @Suppress("UNCHECKED_CAST")
    private fun reattachPickerListeners() {
        (supportFragmentManager.findFragmentByTag(TAG_DATE_PICKER) as? MaterialDatePicker<Long>)
            ?.let(::attachDateListener)
        (supportFragmentManager.findFragmentByTag(TAG_TIME_PICKER) as? MaterialTimePicker)
            ?.let(::attachTimeListener)
    }

    companion object {
        const val EXTRA_EVENT_ID = "com.hcdc.hcdconnect.extra.EDIT_EVENT_ID"

        // Completed is shown automatically once an event's time has passed, so it isn't offered.
        private val STATUS_CHOICES = listOf(EventStatus.UPCOMING, EventStatus.ONGOING, EventStatus.CANCELLED)

        fun newIntent(context: Context): Intent = Intent(context, CreateEventActivity::class.java)

        fun newEditIntent(context: Context, eventId: String): Intent =
            newIntent(context).putExtra(EXTRA_EVENT_ID, eventId)

        private const val TAG_DATE_PICKER = "date_picker"
        private const val TAG_TIME_PICKER = "time_picker"
        private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
    }
}
