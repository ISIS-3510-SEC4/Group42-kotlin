package com.example.espoti.model.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// ============================================================================
// MEETING LIFECYCLE TEST
// ----------------------------------------------------------------------------
// When a meeting starts / ends and which status it shows. Uses a fixed zone
// (Bogota) so the results don't depend on the machine running the tests.
// ============================================================================
class MeetingLifecycleTest {

    private val zone = ZoneId.of("America/Bogota")
    private val day = LocalDate.of(2026, 10, 20)
    private val dayUtc = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun at(hour: Int, minute: Int = 0, onDay: LocalDate = day): Long =
        onDay.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun meeting(time: String, status: StatusMeeting = StatusMeeting.UPCOMING) =
        MeetingEvent(id = "m", date = dayUtc, time = time, status = status)

    @Test
    fun parseMeetingTime_acceptsPickerAndLegacyFormats() {
        assertEquals(LocalTime.of(18, 0), MeetingLifecycle.parseMeetingTime("18:00"))
        assertEquals(LocalTime.of(18, 0), MeetingLifecycle.parseMeetingTime("6:00 pm"))
        assertEquals(LocalTime.of(6, 0), MeetingLifecycle.parseMeetingTime("6 AM"))
        assertNull(MeetingLifecycle.parseMeetingTime("after lunch"))
        assertNull(MeetingLifecycle.parseMeetingTime(""))
    }

    @Test
    fun startsAt_combinesDayAndTime() {
        assertEquals(at(18), MeetingLifecycle.startsAt(dayUtc, "18:00", zone))
        assertEquals(at(0), MeetingLifecycle.startsAt(dayUtc, "", zone))
        assertNull(MeetingLifecycle.startsAt(0L, "18:00", zone))
    }

    @Test
    fun endsAt_isThreeHoursAfterStart_orEndOfDayWithoutTime() {
        assertEquals(at(21), MeetingLifecycle.endsAt(meeting("18:00"), zone))
        assertEquals(at(0, onDay = day.plusDays(1)), MeetingLifecycle.endsAt(meeting(""), zone))
        assertNull(MeetingLifecycle.endsAt(MeetingEvent(date = 0L), zone))
    }

    @Test
    fun isFinished_onlyAfterTheEnd() {
        assertFalse(MeetingLifecycle.isFinished(meeting("18:00"), at(20, 59), zone))
        assertTrue(MeetingLifecycle.isFinished(meeting("18:00"), at(21), zone))
        assertFalse(MeetingLifecycle.isFinished(MeetingEvent(date = 0L), Long.MAX_VALUE, zone))
    }

    @Test
    fun effectiveStatus_finishedUpcomingIsPrevious() {
        assertEquals(StatusMeeting.UPCOMING, MeetingLifecycle.effectiveStatus(meeting("18:00"), at(19), zone))
        assertEquals(StatusMeeting.PREVIOUS, MeetingLifecycle.effectiveStatus(meeting("18:00"), at(22), zone))
    }

    @Test
    fun effectiveStatus_expiredVotingIsDecided_thenPreviousWhenOver() {
        val voting = meeting("18:00", StatusMeeting.VOTING).copy(votingDeadline = at(12))
        assertEquals(StatusMeeting.VOTING, MeetingLifecycle.effectiveStatus(voting, at(11), zone))
        assertEquals(StatusMeeting.UPCOMING, MeetingLifecycle.effectiveStatus(voting, at(13), zone))
        assertEquals(StatusMeeting.PREVIOUS, MeetingLifecycle.effectiveStatus(voting, at(22), zone))
    }

    @Test
    fun effectiveStatus_canceledStaysCanceled() {
        val canceled = meeting("18:00", StatusMeeting.CANCELED)
        assertEquals(StatusMeeting.CANCELED, MeetingLifecycle.effectiveStatus(canceled, at(22), zone))
    }
}
