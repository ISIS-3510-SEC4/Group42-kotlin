package com.example.espoti.data.repository

import com.example.espoti.model.analytics.AnalyticsEvent
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreAnalyticsRepository(
    dbProvider: () -> FirebaseFirestore = {
        FirebaseFirestore.getInstance()
    }
) : AnalyticsRepository {

    private val events by lazy {
        dbProvider().collection("analytics_events")
    }

    override suspend fun saveEvent(
        event: AnalyticsEvent
    ): Result<Unit> {
        return try {
            val document = mapOf(
                "id" to event.id,
                "eventType" to event.eventType.name,
                "userId" to event.userId,
                "timestamp" to event.timestamp,
                "sessionId" to event.sessionId,
                "platform" to event.platform.name,
                "meetingId" to event.meetingId,
                "placeId" to event.placeId,
                "step" to event.step,
                "durationMs" to event.durationMs,
                "metadata" to event.metadata
            )

            events.document(event.id)
                .set(document)
                .await()

            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}