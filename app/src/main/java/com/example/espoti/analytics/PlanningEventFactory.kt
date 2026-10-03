package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.model.analytics.PlanningAnalyticsEvent

class PlanningEventFactory(
    sessionId: String,
    wallTimeMillis: () -> Long = System::currentTimeMillis
) : AnalyticsEventFactory(sessionId, wallTimeMillis) {

    override fun createEvent(
        type: AnalyticsEventType,
        userId: String,
        meetingId: String?,
        placeId: String?,
        step: String?,
        durationMs: Long?,
        metadata: Map<String, Any?>
    ): AnalyticsEvent {
        return PlanningAnalyticsEvent(
            id = java.util.UUID.randomUUID().toString(),
            eventType = type,
            userId = userId,
            timestamp = java.util.Date(wallTimeMillis()),
            sessionId = sessionId,
            meetingId = meetingId,
            placeId = placeId,
            step = step,
            durationMs = durationMs,
            metadata = metadata.toMap()
        )
    }
}