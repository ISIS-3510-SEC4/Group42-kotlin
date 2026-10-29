package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import kotlinx.coroutines.CancellationException
import java.util.Date
import java.util.UUID

class VotingTelemetry(
    private val emit: (AnalyticsEvent) -> Unit,
    private val errorCode: (Throwable) -> String = { "UNKNOWN" },
    private val nanos: () -> Long = System::nanoTime,
    private val millis: () -> Long = System::currentTimeMillis,
    private val sessionId: String = UUID.randomUUID().toString()
) {

    suspend fun record(
        meetingId: String,
        uid: String,
        placeId: String,
        write: suspend () -> Unit
    ): Result<Unit> {
        val attemptId = UUID.randomUUID().toString()
        val startedNanos = nanos()
        val startedAtMillis = millis()

        fun emitEvent(
            type: AnalyticsEventType,
            code: String? = null
        ) {
            try {
                val durationMs =
                    if (type == AnalyticsEventType.VOTE_ATTEMPTED) {
                        null
                    } else {
                        (nanos() - startedNanos)
                            .coerceAtLeast(0L) / 1_000_000L
                    }

                emit(
                    AnalyticsEvent(
                        id = "${attemptId}_${type.name}",
                        eventType = type,
                        userId = uid,
                        timestamp = Date(millis()),
                        sessionId = sessionId,
                        meetingId = meetingId,
                        placeId = placeId,
                        step = "VOTE_BATCH_COMMIT",
                        durationMs = durationMs,
                        metadata = mapOf(
                            "attemptId" to attemptId,
                            "attemptStartedAtMs" to startedAtMillis,
                            "schemaVersion" to 1,
                            "boundary" to "FIRESTORE_BATCH_ACK",
                            "errorCode" to code
                        )
                    )
                )
            } catch (_: Exception) {
                // Un fallo de analítica no debe impedir guardar el voto.
            }
        }

        emitEvent(AnalyticsEventType.VOTE_ATTEMPTED)

        return try {
            write()

            emitEvent(AnalyticsEventType.VOTE_SUCCEEDED)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            emitEvent(AnalyticsEventType.VOTE_CANCELLED)
            throw cancelled
        } catch (error: Exception) {
            emitEvent(
                AnalyticsEventType.VOTE_FAILED,
                errorCode(error)
            )
            Result.failure(error)
        }
    }
}