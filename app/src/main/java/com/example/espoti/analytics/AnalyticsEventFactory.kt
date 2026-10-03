package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import java.util.Date

abstract class AnalyticsEventFactory(
    protected val sessionId: String,
    protected val wallTimeMillis: () -> Long = System::currentTimeMillis
) {

    abstract fun createEvent(
        type: AnalyticsEventType,
        userId: String,
        meetingId: String? = null,
        placeId: String? = null,
        step: String? = null,
        durationMs: Long? = null,
        metadata: Map<String, Any?> = emptyMap()
    ): AnalyticsEvent
}