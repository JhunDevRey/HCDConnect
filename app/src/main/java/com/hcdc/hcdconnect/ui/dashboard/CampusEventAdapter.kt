package com.hcdc.hcdconnect.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.databinding.ItemCampusEventBinding
import com.hcdc.hcdconnect.ui.common.CampusTime
import java.text.DateFormat

class CampusEventAdapter(
    private val onEventClick: (CampusEvent) -> Unit = {}
) : ListAdapter<CampusEvent, CampusEventAdapter.EventViewHolder>(EventDiffCallback) {

    /** The signed-in user, to mark events they're going to. Call [submitList] after changing it. */
    var currentUserId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemCampusEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(
        private val binding: ItemCampusEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat: DateFormat =
            CampusTime.dateTimeFormat(DateFormat.MEDIUM, DateFormat.SHORT)

        fun bind(event: CampusEvent) = with(binding) {
            val status = event.displayStatus()
            textTitle.text = event.title
            textOrganizer.text = event.organizerClub
            textDate.text = event.date?.toDate()?.let(dateFormat::format).orEmpty()
            textLocation.text = event.location
            textDescription.text = event.description
            // Description is optional; hide it so the card doesn't show a blank gap.
            textDescription.isVisible = event.description.isNotBlank()
            textStatus.text = status.label
            textStatus.setTextColor(EventStatusColors.colorFor(root, status))
            textGoing.text = goingText(event)
            textGoing.isVisible = textGoing.text.isNotEmpty()
            root.setOnClickListener { onEventClick(event) }
        }

        private fun goingText(event: CampusEvent): String {
            val context = binding.root.context
            val count = event.attendees.size
            val countText = if (count > 0) {
                context.resources.getQuantityString(R.plurals.going_count, count, count)
            } else {
                ""
            }
            return when {
                event.isGoing(currentUserId) && count > 1 ->
                    context.getString(R.string.you_are_going_with_count, countText)
                event.isGoing(currentUserId) -> context.getString(R.string.you_are_going)
                else -> countText
            }
        }
    }

    private object EventDiffCallback : DiffUtil.ItemCallback<CampusEvent>() {
        override fun areItemsTheSame(oldItem: CampusEvent, newItem: CampusEvent) =
            oldItem.eventId == newItem.eventId

        override fun areContentsTheSame(oldItem: CampusEvent, newItem: CampusEvent) =
            oldItem == newItem
    }
}
