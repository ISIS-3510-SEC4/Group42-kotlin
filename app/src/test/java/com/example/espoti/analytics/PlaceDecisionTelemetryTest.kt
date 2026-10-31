package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.model.domain.DecisionMethod
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.StatusMeeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceDecisionTelemetryTest {

    private val closedMeeting = MeetingEvent(
        id = "m1",
        creatorId = "organizer",
        participantIds = listOf("organizer", "friend"),
        candidatePlaceIds = listOf("p1", "p2", "p3"),
        status = StatusMeeting.UPCOMING,
        placeId = "p2",
        createdAt = 1_000L,
        votes = mapOf("friend" to "p1")
    )

    private fun record(
        meeting: MeetingEvent,
        method: DecisionMethod,
        reason: String,
        closedBy: String? = "friend"
    ): AnalyticsEvent {
        val events = mutableListOf<AnalyticsEvent>()
        PlaceDecisionTelemetry(
            emit = { events += it },
            closedBy = { closedBy },
            millis = { 61_000L }
        ).decided(meeting, method, reason)
        assertEquals(1, events.size)
        return events.single()
    }

    @Test
    fun directChoiceIsAttributedToTheOrganizer() {
        val event = record(
            closedMeeting,
            DecisionMethod.DIRECT,
            PlaceDecisionTelemetry.REASON_ORGANIZER_CHOICE,
            closedBy = "organizer"
        )

        assertEquals("m1_PLACE_DECIDED", event.id)
        assertEquals(AnalyticsEventType.MEETING_PLACE_DECIDED, event.eventType)
        assertEquals("organizer", event.userId)
        assertEquals("m1", event.meetingId)
        assertEquals("p2", event.placeId)
        assertEquals(PlaceDecisionTelemetry.STEP, event.step)
        assertEquals("DIRECT", event.metadata["decisionMethod"])
        assertEquals("UPCOMING", event.metadata["outcome"])
        assertEquals("ORGANIZER_CHOICE", event.metadata["closeReason"])
        assertEquals("organizer", event.metadata["organizerId"])
        assertEquals(2, event.metadata["participantCount"])
        assertEquals(1, event.metadata["voteCount"])
        assertEquals(3, event.metadata["candidateCount"])
        assertEquals(60_000L, event.metadata["decisionDelayMs"])
        assertEquals(1, event.metadata["schemaVersion"])
    }

    @Test
    fun votingKeepsOrganizerAsSubjectEvenIfAFriendClosedIt() {
        val event = record(closedMeeting, DecisionMethod.VOTING, PlaceDecisionTelemetry.REASON_ALL_VOTED)

        assertEquals("organizer", event.userId)
        assertEquals("friend", event.metadata["closedBy"])
        assertEquals("VOTING", event.metadata["decisionMethod"])
        assertEquals("ALL_VOTED", event.metadata["closeReason"])
    }

    @Test
    fun canceledVotingAfterDeadlineIsStillVoting() {
        val canceled = closedMeeting.copy(status = StatusMeeting.CANCELED, placeId = null, votes = emptyMap())
        val event = record(canceled, DecisionMethod.VOTING, PlaceDecisionTelemetry.REASON_DEADLINE, closedBy = null)

        assertEquals("CANCELED", event.metadata["outcome"])
        assertEquals("DEADLINE", event.metadata["closeReason"])
        assertNull(event.placeId)
        // Without a signed-in user the organizer is used as the closer.
        assertEquals("organizer", event.metadata["closedBy"])
    }

    @Test
    fun sameMeetingAlwaysGetsTheSameEventId() {
        val first = record(closedMeeting, DecisionMethod.VOTING, PlaceDecisionTelemetry.REASON_ALL_VOTED)
        val second = record(closedMeeting, DecisionMethod.VOTING, PlaceDecisionTelemetry.REASON_DEADLINE)
        assertEquals(first.id, second.id)
    }

    @Test
    fun emitFailureDoesNotPropagate() {
        var called = false
        PlaceDecisionTelemetry(emit = {
            called = true
            throw IllegalStateException("analytics down")
        }).decided(closedMeeting, DecisionMethod.DIRECT, PlaceDecisionTelemetry.REASON_ORGANIZER_CHOICE)
        assertTrue(called)
    }
}
