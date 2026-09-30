package com.hcdc.hcdconnect.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.ui.common.CampusTime
import com.hcdc.hcdconnect.ui.eventdetail.EventDetailActivity
import java.text.DateFormat

object ReminderNotifications {

    private const val CHANNEL_ID = "event_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, event: CampusEvent) {
        if (!canNotify(context)) return
        createChannel(context)

        // Opens the event, with the dashboard behind it for Back.
        val openEvent = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(EventDetailActivity.newIntent(context, event.eventId))
            .getPendingIntent(
                event.eventId.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val time = event.date?.toDate()?.let { CampusTime.timeFormat(DateFormat.SHORT).format(it) }.orEmpty()
        val text = listOf(time, event.location).filter { it.isNotBlank() }.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.hcdc_maroon))
            .setContentTitle(context.getString(R.string.reminder_title, event.title))
            .setContentText(text)
            .setContentIntent(openEvent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(event.eventId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call; nothing to show.
        }
    }
}
