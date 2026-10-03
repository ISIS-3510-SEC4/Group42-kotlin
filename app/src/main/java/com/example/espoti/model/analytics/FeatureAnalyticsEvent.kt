package com.example.espoti.model.analytics

import java.util.Date

class FeatureAnalyticsEvent(
    id: String,
    eventType: AnalyticsEventType,
    userId: String,
    timestamp: Date,
    sessionId: String,
    platform: PlatformType = PlatformType.KOTLIN,
    meetingId: String? = null,
    placeId: String? = null,
    step: String? = null,
    durationMs: Long? = null,
    metadata: Map<String, Any?> = emptyMap()
) : AnalyticsEvent(
    id = id,
    eventType = eventType,
    userId = userId,
    timestamp = timestamp,
    sessionId = sessionId,
    platform = platform,
    meetingId = meetingId,
    placeId = placeId,
    step = step,
    durationMs = durationMs,
    metadata = metadata
)
