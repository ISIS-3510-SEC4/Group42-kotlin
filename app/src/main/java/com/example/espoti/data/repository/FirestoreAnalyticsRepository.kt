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

    override suspend fun getGlobalRejectionRates(): Result<Map<String, Double>> {
        return try {
            val snapshot = events
                .whereIn(
                    "eventType",
                    listOf("PLACE_SELECTED", "RECOMMENDATION_REJECTED")
                )
                .get()
                .await()

            val selections = mutableMapOf<String, Int>()
            val rejections = mutableMapOf<String, Int>()

            for (document in snapshot.documents) {
                val eventType = document.getString("eventType")
                val placeId = document.getString("placeId")
                    ?: continue

                when (eventType) {
                    "PLACE_SELECTED" -> {
                        selections[placeId] =
                            (selections[placeId] ?: 0) + 1
                    }

                    "RECOMMENDATION_REJECTED" -> {
                        rejections[placeId] =
                            (rejections[placeId] ?: 0) + 1
                    }
                }
            }

            val placeIds = selections.keys + rejections.keys

            val rates = placeIds.associateWith { placeId ->
                val selected = selections[placeId] ?: 0
                val rejected = rejections[placeId] ?: 0
                val total = selected + rejected

                (rejected + 1.0) / (total + 2.0)
            }

            Result.success(rates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

}