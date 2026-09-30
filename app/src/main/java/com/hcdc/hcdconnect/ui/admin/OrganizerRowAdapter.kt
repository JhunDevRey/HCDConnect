package com.hcdc.hcdconnect.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.databinding.ItemOrganizerRowBinding

class OrganizerRowAdapter(
    private val onRowClick: (UserRow) -> Unit
) : ListAdapter<UserRow, OrganizerRowAdapter.RowViewHolder>(RowDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemOrganizerRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RowViewHolder(
        private val binding: ItemOrganizerRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: UserRow) = with(binding) {
            val context = root.context
            val email = row.user.email
            textEmail.text = if (row.isSelf) context.getString(R.string.email_you, email) else email
            val roles = buildList {
                if (row.isAdmin) add(context.getString(R.string.role_admin))
                if (row.isOrganizer) add(
                    row.club?.let { context.getString(R.string.role_organizer_of, it) }
                        ?: context.getString(R.string.role_organizer_no_club)
                )
            }
            textClubs.text = roles.joinToString(" · ").ifEmpty { context.getString(R.string.role_member) }
            root.setOnClickListener { onRowClick(row) }
        }
    }

    private object RowDiffCallback : DiffUtil.ItemCallback<UserRow>() {
        override fun areItemsTheSame(oldItem: UserRow, newItem: UserRow) =
            oldItem.user.userId == newItem.user.userId

        override fun areContentsTheSame(oldItem: UserRow, newItem: UserRow) = oldItem == newItem
    }
}
