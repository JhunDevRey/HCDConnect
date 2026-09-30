package com.hcdc.hcdconnect.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hcdc.hcdconnect.data.model.CampusEvent
import java.util.concurrent.TimeUnit

/**
 * Schedules a local notification [LEAD_TIME_MILLIS] before events the user RSVP'd to.
 * There is one unique WorkManager job per event, so rescheduling replaces the old one.
 * The worker checks the event again before notifying (see [EventReminderWorker]).
 */
object EventReminderScheduler {

    /** How long before an event starts the reminder fires. */
    const val LEAD_TIME_MILLIS: Long = 60 * 60 * 1000L

    /**
     * Brings the reminder for [event] in line with its current state: scheduled if [userId]
     * is going and the event still accepts RSVPs, cancelled otherwise.
     */
    fun sync(context: Context, event: CampusEvent, userId: String?, nowMillis: Long = System.currentTimeMillis()) {
        if (event.eventId.isBlank()) return
        val start = event.date?.toDate()?.time
        if (start == null || !event.isGoing(userId) || !event.acceptsRsvps()) {
            cancel(context, event.eventId)
            return
        }
        // Too close to the start for a reminder. Keep any already-scheduled one rather than
        // cancelling it, so a reminder that's about to fire still does.
        val delay = reminderDelayMillis(start, nowMillis) ?: return
        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(EventReminderWorker.KEY_EVENT_ID to event.eventId))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(event.eventId), ExistingWorkPolicy.REPLACE, request)
    }

    fun syncAll(context: Context, events: List<CampusEvent>, userId: String?) {
        events.forEach { sync(context, it, userId) }
    }

    fun cancel(context: Context, eventId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(eventId))
    }

    /** Cancels every reminder, e.g. when the user signs out. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /** Milliseconds until the reminder should fire, or null if that moment has passed. */
    fun reminderDelayMillis(startMillis: Long, nowMillis: Long): Long? {
        val delay = startMillis - LEAD_TIME_MILLIS - nowMillis
        return delay.takeIf { it > 0 }
    }

    private fun workName(eventId: String) = "event-reminder-$eventId"

    private const val TAG = "event-reminder"
}
