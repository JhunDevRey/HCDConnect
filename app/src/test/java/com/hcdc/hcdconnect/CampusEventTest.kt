package com.hcdc.hcdconnect

import com.google.firebase.Timestamp
import com.hcdc.hcdconnect.data.model.CampusEvent
import com.hcdc.hcdconnect.data.model.EventStatus
import com.hcdc.hcdconnect.reminders.EventReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CampusEventTest {

    private val now = Date(1_800_000_000_000L)
    private val hour = 60 * 60 * 1000L

    private fun eventAt(offsetMillis: Long, status: EventStatus = EventStatus.UPCOMING, attendees: List<String> = emptyList()) =
        CampusEvent(
            eventId = "e1",
            title = "Club Fair",
            date = Timestamp(Date(now.time + offsetMillis)),
            status = status,
            attendees = attendees
        )

    @Test
    fun futureEvent_showsItsStoredStatus() {
        assertEquals(EventStatus.UPCOMING, eventAt(hour).displayStatus(now))
        assertEquals(EventStatus.ONGOING, eventAt(hour, EventStatus.ONGOING).displayStatus(now))
    }

    @Test
    fun pastEvent_showsCompleted() {
        assertEquals(EventStatus.COMPLETED, eventAt(-hour).displayStatus(now))
        assertEquals(EventStatus.COMPLETED, eventAt(-hour, EventStatus.ONGOING).displayStatus(now))
    }

    @Test
    fun cancelledEvent_staysCancelledEvenAfterItsTime() {
        assertEquals(EventStatus.CANCELLED, eventAt(hour, EventStatus.CANCELLED).displayStatus(now))
        assertEquals(EventStatus.CANCELLED, eventAt(-hour, EventStatus.CANCELLED).displayStatus(now))
    }

    @Test
    fun rsvps_onlyAcceptedForUpcomingOrOngoingEvents() {
        assertTrue(eventAt(hour).acceptsRsvps(now))
        assertTrue(eventAt(hour, EventStatus.ONGOING).acceptsRsvps(now))
        assertFalse(eventAt(hour, EventStatus.CANCELLED).acceptsRsvps(now))
        assertFalse(eventAt(-hour).acceptsRsvps(now))
    }

    @Test
    fun isGoing_checksAttendeeList() {
        val event = eventAt(hour, attendees = listOf("alice"))
        assertTrue(event.isGoing("alice"))
        assertFalse(event.isGoing("bob"))
        assertFalse(event.isGoing(null))
    }

    @Test
    fun reminder_firesOneHourBeforeStart() {
        val start = now.time + 3 * hour
        assertEquals(2 * hour, EventReminderScheduler.reminderDelayMillis(start, now.time))
    }

    @Test
    fun reminder_skippedWhenLessThanAnHourAway() {
        assertNull(EventReminderScheduler.reminderDelayMillis(now.time + 30 * 60 * 1000L, now.time))
        assertNull(EventReminderScheduler.reminderDelayMillis(now.time + hour, now.time))
        assertNull(EventReminderScheduler.reminderDelayMillis(now.time - hour, now.time))
    }
}
