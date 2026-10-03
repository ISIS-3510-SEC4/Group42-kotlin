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

    private val recommendationEventFactory =
        RecommendationEventFactory(sessionId, wallTimeMillis)

    private val planningEventFactory =
        PlanningEventFactory(sessionId, wallTimeMillis)

    private val featureEventFactory =
        FeatureEventFactory(sessionId, wallTimeMillis)

    private val meetingEventFactory =
        MeetingEventFactory(sessionId, wallTimeMillis)

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
            factoryFor(type).createEvent(
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

        val event = factoryFor(
            AnalyticsEventType.RECOMMENDATION_REQUESTED
        ).createEvent(
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

        val displayed = factoryFor(
            AnalyticsEventType.RECOMMENDATION_DISPLAYED
        ).createEvent(
            type = AnalyticsEventType.RECOMMENDATION_DISPLAYED,
            userId = trace.requestedEvent.userId,
            meetingId = trace.requestedEvent.meetingId,
            placeId = trace.requestedEvent.placeId,
            step = trace.requestedEvent.step,
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


    private fun factoryFor(type: AnalyticsEventType): AnalyticsEventFactory {
        return when (type) {
            AnalyticsEventType.RECOMMENDATION_REQUESTED,
            AnalyticsEventType.RECOMMENDATION_DISPLAYED,
            AnalyticsEventType.RECOMMENDATION_VIEWED,
            AnalyticsEventType.RECOMMENDATION_SELECTED ->
                recommendationEventFactory

            AnalyticsEventType.PLANNING_STEP_STARTED,
            AnalyticsEventType.PLANNING_STEP_COMPLETED,
            AnalyticsEventType.PLANNING_FLOW_ABANDONED ->
                planningEventFactory

            AnalyticsEventType.FEATURE_USED,
            AnalyticsEventType.FILTER_USED ->
                featureEventFactory

            AnalyticsEventType.MEETING_CREATED ->
                meetingEventFactory
        }
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