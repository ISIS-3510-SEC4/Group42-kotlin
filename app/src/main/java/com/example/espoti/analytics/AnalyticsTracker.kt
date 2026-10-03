package com.example.espoti.analytics

import com.example.espoti.data.repository.AnalyticsRepository
import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AnalyticsTracker(
    private val repository: AnalyticsRepository,
    private val scope: CoroutineScope,
    private val currentUserId: () -> String?,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val onStorageError: (Throwable) -> Unit = {}
) {

    // Analytics session for this application process.
    private val sessionId = UUID.randomUUID().toString()

    class RecommendationTrace internal constructor(
        val requestId: String,
        internal val requestedEvent: AnalyticsEvent,
        internal val startedNanos: Long
    ) {
        internal val finished = AtomicBoolean(false)
    }

    fun trackEvent(
        type: AnalyticsEventType,
        meetingId: String? = null,
        placeId: String? = null,
        step: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        val userId = currentUserId() ?: return

        persist(
            createEvent(
                type = type,
                userId = userId,
                meetingId = meetingId,
                placeId = placeId,
                step = step,
                metadata = metadata
            )
        )
    }

    fun featureUsed(
        feature: String,
        meetingId: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        trackEvent(
            type = AnalyticsEventType.FEATURE_USED,
            meetingId = meetingId,
            metadata = metadata + mapOf(
                "feature" to feature
            )
        )
    }

    fun recommendationRequested(
        meetingId: String?,
        source: String
    ): RecommendationTrace? {
        val startedNanos = monotonicNanos()
        val userId = currentUserId() ?: return null
        val requestId = UUID.randomUUID().toString()

        val event = createEvent(
            type = AnalyticsEventType.RECOMMENDATION_REQUESTED,
            userId = userId,
            meetingId = meetingId,
            metadata = mapOf(
                "requestId" to requestId,
                "source" to source,
                "recommendationScope" to
                        if (meetingId == null) "USER_PREVIEW" else "MEETING"
            )
        )

        persist(event)

        return RecommendationTrace(
            requestId = requestId,
            requestedEvent = event,
            startedNanos = startedNanos
        )
    }

    fun recommendationDisplayed(
        trace: RecommendationTrace,
        resultCount: Int
    ) {
        if (resultCount <= 0) return

        // Do not attribute a result to an account that has changed.
        if (currentUserId() != trace.requestedEvent.userId) {
            cancelRecommendation(trace)
            return
        }

        if (!trace.finished.compareAndSet(false, true)) return

        val durationMs =
            (monotonicNanos() - trace.startedNanos)
                .coerceAtLeast(0L) / 1_000_000L

        val displayed = trace.requestedEvent.copy(
            id = UUID.randomUUID().toString(),
            eventType = AnalyticsEventType.RECOMMENDATION_DISPLAYED,
            timestamp = Date(wallTimeMillis()),
            durationMs = durationMs,
            metadata = trace.requestedEvent.metadata + mapOf(
                "resultCount" to resultCount,
                "displayBoundary" to "FIRST_VISIBLE_RESULT_DRAWN"
            )
        )

        persist(displayed)
    }

    fun cancelRecommendation(trace: RecommendationTrace?) {
        trace?.finished?.set(true)
    }

    private fun createEvent(
        type: AnalyticsEventType,
        userId: String,
        meetingId: String? = null,
        placeId: String? = null,
        step: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ): AnalyticsEvent {
        return AnalyticsEvent(
            id = UUID.randomUUID().toString(),
            eventType = type,
            userId = userId,
            timestamp = Date(wallTimeMillis()),
            sessionId = sessionId,
            meetingId = meetingId,
            placeId = placeId,
            step = step,
            metadata = metadata.toMap()
        )
    }

    private fun persist(event: AnalyticsEvent) {
        scope.launch {
            try {
                repository.saveEvent(event)
                    .exceptionOrNull()
                    ?.let(onStorageError)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onStorageError(error)
            }
        }
    }
}