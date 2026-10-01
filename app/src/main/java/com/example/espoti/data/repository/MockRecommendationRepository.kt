package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import kotlinx.coroutines.delay

class MockRecommendationRepository(
    private val simulatedDelayMs: Long = 0L
) : RecommendationDataSource {

    override val source = "MOCK"

    override suspend fun getRecommendations(
        meetingId: String?
    ): Result<List<Recommendation>> {
        if (simulatedDelayMs > 0L) {
            delay(simulatedDelayMs)
        }

        return Result.success(
            listOf(
                Recommendation(
                    placeId = "mock-1",
                    name = "Restaurante 1",
                    category = "RESTAURANT",
                    rating = 4,
                    score = 0.0,
                    distanceKm = 1.0
                ),
                Recommendation(
                    placeId = "mock-2",
                    name = "Restaurante 2",
                    category = "RESTAURANT",
                    rating = 3,
                    score = 0.0,
                    distanceKm = 2.0
                ),
                Recommendation(
                    placeId = "mock-3",
                    name = "Restaurante 3",
                    category = "RESTAURANT",
                    rating = 5,
                    score = 0.0,
                    distanceKm = 1.5
                )
            )
        )
    }
}