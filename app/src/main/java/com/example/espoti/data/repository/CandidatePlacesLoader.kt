package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import com.example.espoti.model.domain.toRecommendation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads independent candidate places concurrently.
 *
 * The caller remains responsible for UI state on Main.
 * Data loading and conversion run in the injected IO dispatcher.
 * Structured concurrency cancels child operations with their parent.
 */
class CandidatePlacesLoader(
    private val placeRepository: PlaceRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxParallelism: Int = 4
) {
    init {
        require(maxParallelism > 0)
    }

    suspend fun load(
        placeIds: List<String>
    ): List<Recommendation> = withContext(ioDispatcher) {
        val permits = Semaphore(maxParallelism)

        coroutineScope {
            placeIds.map { placeId ->
                async {
                    permits.withPermit {
                        val result = placeRepository.getPlace(placeId)

                        // Repository Result wrappers must not hide cancellation.
                        currentCoroutineContext().ensureActive()

                        result.getOrNull()?.toRecommendation()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}