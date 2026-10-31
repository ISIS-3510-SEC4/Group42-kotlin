package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.model.domain.DecisionMethod
import com.example.espoti.model.domain.MeetingEvent
import java.util.Date
import java.util.UUID

// ============================================================================
// PLACE DECISION TELEMETRY (BQ "organizer decision method")
// ----------------------------------------------------------------------------
// BQ: what proportion of meeting organizers use the group voting versus
// choosing the meeting place directly for the group?
// One MEETING_PLACE_DECIDED event per decided meeting in analytics_events:
// - id "{meetingId}_PLACE_DECIDED": at most one document per meeting, a
//   repeated write just overwrites it.
// - userId = the organizer (meeting creator); metadata.closedBy = who closed.
// - metadata.decisionMethod = VOTING | DIRECT, metadata.outcome = UPCOMING |
//   CANCELED (nobody voted), metadata.closeReason = ALL_VOTED | DEADLINE |
//   ORGANIZER_CHOICE.
// How to chart it: filter eventType == "MEETING_PLACE_DECIDED"; per meeting,
// count by decisionMethod; per organizer, distinct userId that used DIRECT at
// least once vs. only VOTING (optionally skip outcome == CANCELED).
// ============================================================================
class PlaceDecisionTelemetry(
    private val emit: (AnalyticsEvent) -> Unit,
    private val closedBy: () -> String? = { null },
    private val millis: () -> Long = System::currentTimeMillis,
    private val sessionId: String = UUID.randomUUID().toString()
) {

    /** Records that [meeting] (as stored after closing) got its place by [method]. */
    fun decided(
        meeting: MeetingEvent,
        method: DecisionMethod,
        closeReason: String
    ) {
        try {
            val now = millis()
            val voteCount = meeting.votes.size

            emit(
                AnalyticsEvent(
                    id = eventIdFor(meeting.id),
                    eventType = AnalyticsEventType.MEETING_PLACE_DECIDED,
                    userId = meeting.creatorId,
                    timestamp = Date(now),
                    sessionId = sessionId,
                    meetingId = meeting.id,
                    placeId = meeting.placeId,
                    step = STEP,
                    metadata = mapOf(
                        "schemaVersion" to 1,
                        "bq" to BQ_NAME,
                        "organizerId" to meeting.creatorId,
                        "decisionMethod" to method.name,
                        "outcome" to meeting.status.name,
                        "closeReason" to closeReason,
                        "closedBy" to (closedBy() ?: meeting.creatorId),
                        "participantCount" to meeting.participantIds.size,
                        "voteCount" to voteCount,
                        "candidateCount" to meeting.candidatePlaceIds.size,
                        "meetingCreatedAt" to meeting.createdAt,
                        "decisionDelayMs" to
                            if (meeting.createdAt > 0) (now - meeting.createdAt).coerceAtLeast(0L) else null
                    )
                )
            )
        } catch (_: Exception) {
            // An analytics failure must never block deciding the meeting.
        }
    }

    companion object {
        const val STEP = "PLACE_DECISION"
        const val BQ_NAME = "ORGANIZER_DECISION_METHOD"
        const val REASON_ALL_VOTED = "ALL_VOTED"
        const val REASON_DEADLINE = "DEADLINE"
        const val REASON_ORGANIZER_CHOICE = "ORGANIZER_CHOICE"

        fun eventIdFor(meetingId: String) = "${meetingId}_PLACE_DECIDED"
    }
}
