package com.example.espoti.analytics

import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VotingTelemetryTest {

    @Test
    fun successfulVoteEmitsAttemptAndSuccess() = runTest {
        val events = mutableListOf<AnalyticsEvent>()
        var clockNanos = 0L

        val telemetry = VotingTelemetry(
            emit = { events.add(it) },
            nanos = { clockNanos }
        )

        val result = telemetry.record("meeting-1", "user-1", "place-1") {
            // Antes de guardar solo debe existir el intento.
            assertEquals(1, events.size)
            assertEquals(
                AnalyticsEventType.VOTE_ATTEMPTED,
                events.first().eventType
            )

            clockNanos = 5_000_000L
        }

        assertTrue(result.isSuccess)
        assertEquals(2, events.size)
        assertEquals(
            AnalyticsEventType.VOTE_SUCCEEDED,
            events.last().eventType
        )
        assertEquals(
            events.first().metadata["attemptId"],
            events.last().metadata["attemptId"]
        )
        assertEquals(5L, events.last().durationMs)
        assertEquals("meeting-1", events.last().meetingId)
        assertEquals("user-1", events.last().userId)
        assertEquals("place-1", events.last().placeId)
    }

    @Test
    fun failedVotePreservesErrorAndRetryGetsNewAttemptId() = runTest {
        val events = mutableListOf<AnalyticsEvent>()

        val telemetry = VotingTelemetry(
            emit = { events.add(it) },
            errorCode = { "PERMISSION_DENIED" }
        )

        val originalError = IllegalStateException("Private error detail")

        val result = telemetry.record("meeting-1", "user-1", "place-1") {
            throw originalError
        }

        assertSame(originalError, result.exceptionOrNull())
        assertEquals(
            AnalyticsEventType.VOTE_FAILED,
            events[1].eventType
        )
        assertEquals(
            "PERMISSION_DENIED",
            events[1].metadata["errorCode"]
        )
        assertEquals(
            events[0].metadata["attemptId"],
            events[1].metadata["attemptId"]
        )
        assertFalse(
            events[1].metadata.toString().contains("Private error detail")
        )

        val retry = telemetry.record("meeting-1", "user-1", "place-1") {}

        assertTrue(retry.isSuccess)
        assertEquals(4, events.size)
        assertNotEquals(
            events[0].metadata["attemptId"],
            events[2].metadata["attemptId"]
        )
        assertEquals(
            AnalyticsEventType.VOTE_SUCCEEDED,
            events[3].eventType
        )
    }

    @Test
    fun cancellationIsRethrownAndRecordedSeparately() = runTest {
        val events = mutableListOf<AnalyticsEvent>()
        val telemetry = VotingTelemetry(
            emit = { events.add(it) }
        )

        val cancellation = CancellationException("Test cancellation")

        try {
            telemetry.record("meeting-1", "user-1", "place-1") {
                throw cancellation
            }
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }

        assertEquals(
            listOf(
                AnalyticsEventType.VOTE_ATTEMPTED,
                AnalyticsEventType.VOTE_CANCELLED
            ),
            events.map { it.eventType }
        )
    }

    @Test
    fun analyticsFailureDoesNotPreventVoting() = runTest {
        var voteWasSaved = false

        val telemetry = VotingTelemetry(
            emit = { throw IllegalStateException("Analytics unavailable") }
        )

        val result = telemetry.record("meeting-1", "user-1", "place-1") {
            voteWasSaved = true
        }

        assertTrue(voteWasSaved)
        assertTrue(result.isSuccess)
    }
}