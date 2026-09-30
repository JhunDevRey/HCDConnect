package com.hcdc.hcdconnect.ui.dashboard

import android.view.View
import com.google.android.material.color.MaterialColors
import com.hcdc.hcdconnect.data.model.EventStatus

/** Theme colors for status labels, shared by the event card and the details screen. */
object EventStatusColors {

    fun colorFor(view: View, status: EventStatus): Int {
        val attr = when (status) {
            EventStatus.CANCELLED -> androidx.appcompat.R.attr.colorError
            EventStatus.COMPLETED -> com.google.android.material.R.attr.colorOnSurfaceVariant
            EventStatus.UPCOMING, EventStatus.ONGOING -> androidx.appcompat.R.attr.colorPrimary
        }
        return MaterialColors.getColor(view, attr)
    }
}
