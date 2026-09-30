package com.hcdc.hcdconnect.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hcdc.hcdconnect.data.repository.AuthRepository
import com.hcdc.hcdconnect.data.repository.CampusEventRepository

/**
 * Fires shortly before an event. Rechecks the event first, so nothing is shown if it was
 * deleted or cancelled, moved into the past, or the user cancelled their RSVP since the
 * reminder was scheduled.
 */
class EventReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.success()
        val userId = AuthRepository().currentUser?.uid ?: return Result.success()

        val event = CampusEventRepository().getEvent(eventId).getOrElse {
            // Deleted events are skipped. Other failures (e.g. offline with nothing cached)
            // are retried, since the reminder is still wanted.
            return if (it is NoSuchElementException) Result.success() else Result.retry()
        }
        val startsInFuture = (event.date?.toDate()?.time ?: 0L) > System.currentTimeMillis()
        if (event.isGoing(userId) && event.acceptsRsvps() && startsInFuture) {
            ReminderNotifications.show(applicationContext, event)
        }
        return Result.success()
    }

    companion object {
        const val KEY_EVENT_ID = "eventId"
    }
}
