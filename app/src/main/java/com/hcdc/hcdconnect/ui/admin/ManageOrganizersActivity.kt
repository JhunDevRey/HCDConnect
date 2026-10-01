package com.hcdc.hcdconnect.ui.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.ui.common.setUpBrandedSystemBars
import com.hcdc.hcdconnect.databinding.ActivityManageOrganizersBinding
import com.hcdc.hcdconnect.databinding.DialogEditRolesBinding
import com.hcdc.hcdconnect.databinding.DialogManageClubsBinding
import kotlinx.coroutines.launch

/**
 * Admin-only screen: give users admin or organizer roles, set organizers' clubs, delete users,
 * and manage the club list.
 */
class ManageOrganizersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageOrganizersBinding
    private val viewModel: ManageOrganizersViewModel by viewModels()
    private val rowAdapter = OrganizerRowAdapter(::showEditRolesDialog)

    // The open Clubs dialog's content, kept so the club list updates live while it's open.
    private var clubsDialog: DialogManageClubsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageOrganizersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpBrandedSystemBars(binding.root, binding.toolbar, includeIme = true)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_manage_users)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clubs) {
                showClubsDialog()
                true
            } else {
                false
            }
        }
        binding.editSearch.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }
        binding.recyclerUsers.adapter = rowAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.actionState.collect(::renderActions) }
            }
        }
    }

    private fun render(state: ManageUsersUiState) = with(binding) {
        progressBar.isVisible = state.isLoading
        rowAdapter.submitList(state.rows)
        val emptyText = when {
            state.isLoading -> null
            state.errorMessage != null -> getString(R.string.error_loading_users, state.errorMessage)
            state.rows.isEmpty() -> getString(R.string.no_users_found)
            else -> null
        }
        textEmpty.text = emptyText
        textEmpty.isVisible = emptyText != null
        clubsDialog?.let { bindClubChips(it, state.clubs) }
    }

    private fun renderActions(state: ManageUsersActionState) {
        binding.progressSaving.isVisible = state.isSaving
        state.message?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    private fun showEditRolesDialog(row: UserRow) {
        val dialogBinding = DialogEditRolesBinding.inflate(layoutInflater)
        val noClub = getString(R.string.no_club_yet)
        val clubChoices = listOf(noClub) + (viewModel.uiState.value.clubs + listOfNotNull(row.club)).distinct().sorted()

        with(dialogBinding) {
            switchAdmin.isChecked = row.isAdmin
            // The rules stop admins removing themselves, so there's always at least one.
            switchAdmin.isEnabled = !(row.isSelf && row.isAdmin)
            textSelfAdminNote.isVisible = row.isSelf && row.isAdmin
            switchOrganizer.isChecked = row.isOrganizer
            dropdownClub.setSimpleItems(clubChoices.toTypedArray())
            dropdownClub.setText(row.club ?: noClub, false)
            layoutClub.isEnabled = row.isOrganizer
            switchOrganizer.setOnCheckedChangeListener { _, checked -> layoutClub.isEnabled = checked }
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(row.user.email)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val picked = dialogBinding.dropdownClub.text.toString()
                val isOrganizer = dialogBinding.switchOrganizer.isChecked
                viewModel.saveRoles(
                    row = row,
                    isAdmin = dialogBinding.switchAdmin.isChecked,
                    isOrganizer = isOrganizer,
                    club = picked.takeIf { isOrganizer && it != noClub && it.isNotBlank() }
                )
            }
        // Admins can delete anyone but themselves.
        if (!row.isSelf) {
            builder.setNeutralButton(R.string.delete_user) { _, _ -> confirmDeleteUser(row) }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setTextColor(MaterialColors.getColor(dialog.window!!.decorView, androidx.appcompat.R.attr.colorError))
    }

    private fun confirmDeleteUser(row: UserRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_user_title, row.user.email))
            .setMessage(R.string.delete_user_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_user_confirm) { _, _ -> viewModel.deleteUser(row) }
            .show()
    }

    private fun showClubsDialog() {
        val dialogBinding = DialogManageClubsBinding.inflate(layoutInflater)
        fun addTypedClub() {
            val name = dialogBinding.editNewClub.text?.toString().orEmpty()
            if (name.isNotBlank()) {
                viewModel.addClub(name)
                dialogBinding.editNewClub.text = null
            }
        }
        dialogBinding.layoutNewClub.setEndIconOnClickListener { addTypedClub() }
        dialogBinding.editNewClub.setOnEditorActionListener { _, _, _ ->
            addTypedClub()
            true
        }
        bindClubChips(dialogBinding, viewModel.uiState.value.clubs)
        clubsDialog = dialogBinding

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clubs)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.done, null)
            .setOnDismissListener { clubsDialog = null }
            .show()
    }

    private fun bindClubChips(dialogBinding: DialogManageClubsBinding, clubs: List<String>) {
        val group = dialogBinding.chipGroupClubs
        group.removeAllViews()
        clubs.forEach { club ->
            val chip = layoutInflater.inflate(R.layout.chip_club, group, false) as Chip
            chip.text = club
            chip.setOnCloseIconClickListener { confirmDeleteClub(club) }
            group.addView(chip)
        }
        dialogBinding.textNoClubs.isVisible = clubs.isEmpty()
    }

    private fun confirmDeleteClub(club: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_club_title, club))
            .setMessage(R.string.delete_club_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteClub(club) }
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ManageOrganizersActivity::class.java)
    }
}
