package com.example.espoti.model.analytics

import java.util.Date

data class AnalyticsEvent(
    val id: String,
    val eventType: AnalyticsEventType,
    val userId: String,
    val timestamp: Date,
    val sessionId: String,
    val platform: PlatformType = PlatformType.KOTLIN,
    val meetingId: String? = null,
    val placeId: String? = null,
    val step: String? = null,
    val durationMs: Long? = null,
    val metadata: Map<String, Any?> = emptyMap()
)