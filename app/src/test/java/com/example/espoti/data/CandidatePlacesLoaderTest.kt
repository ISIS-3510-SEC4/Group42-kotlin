package com.example.espoti.data

import com.example.espoti.data.repository.CandidatePlacesLoader
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.model.domain.Place
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CandidatePlacesLoaderTest {

    @Test
    fun loadsConcurrently_withBoundedParallelism_andPreservesOrder() =
        runTest {
            var active = 0
            var peak = 0

            val repository = object : PlaceRepository(
                dbProvider = { error("No Firestore in unit tests") }
            ) {
                override suspend fun getPlace(
                    id: String
                ): Result<Place?> {
                    active++
                    peak = maxOf(peak, active)

                    return try {
                        delay(100)
                        Result.success(Place(id = id, name = id))
                    } finally {
                        active--
                    }
                }
            }

            val loader = CandidatePlacesLoader(
                placeRepository = repository,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                maxParallelism = 2
            )

            val startedAt = currentTime
            val result = loader.load(listOf("a", "b", "c"))

            assertEquals(listOf("a", "b", "c"), result.map { it.placeId })
            assertEquals(2, peak)
            assertEquals(200L, currentTime - startedAt)
            assertEquals(0, active)
        }

    @Test
    fun unavailablePlace_doesNotDiscardOtherCandidates() = runTest {
        val repository = object : PlaceRepository(
            dbProvider = { error("No Firestore in unit tests") }
        ) {
            override suspend fun getPlace(
                id: String
            ): Result<Place?> =
                if (id == "missing") {
                    Result.failure(IllegalStateException("Unavailable"))
                } else {
                    Result.success(Place(id = id, name = id))
                }
        }

        val loader = CandidatePlacesLoader(
            placeRepository = repository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = loader.load(listOf("a", "missing", "b"))

        assertEquals(listOf("a", "b"), result.map { it.placeId })
    }

    @Test
    fun cancellation_reachesRunningChildReads() = runTest {
        val started = CompletableDeferred<Unit>()
        var canceledReads = 0

        val repository = object : PlaceRepository(
            dbProvider = { error("No Firestore in unit tests") }
        ) {
            override suspend fun getPlace(
                id: String
            ): Result<Place?> {
                started.complete(Unit)

                try {
                    awaitCancellation()
                } finally {
                    canceledReads++
                }
            }
        }

        val loader = CandidatePlacesLoader(
            placeRepository = repository,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            maxParallelism = 1
        )

        val job = launch {
            loader.load(listOf("a", "b"))
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, canceledReads)
    }
}