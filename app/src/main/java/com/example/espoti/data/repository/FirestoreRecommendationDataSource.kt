package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import com.example.espoti.recommendation.AlphabeticalStrategy
import com.example.espoti.recommendation.RecommendationContext
import com.example.espoti.recommendation.RecommendationScoringStrategy
import com.example.espoti.recommendation.rankedBy
import kotlinx.coroutines.CancellationException

class FirestoreRecommendationDataSource(
    private val repository: PlaceRepository = PlaceRepository(),
    // Strategy: how candidates are scored/ordered. Swap it without touching consumers.
    private val strategy: RecommendationScoringStrategy = AlphabeticalStrategy,
    private val contextProvider: () -> RecommendationContext = { RecommendationContext() }
) : RecommendationDataSource {

    override val source = "FIRESTORE"

    override suspend fun getRecommendations(
        meetingId: String?
    ): Result<List<Recommendation>> {
        val result = repository.getPlaces()

        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure

        return result.map { places ->
            places
                .map { place ->
                    Recommendation(
                        placeId = place.id,
                        name = place.name,
                        category = place.category.name,
                        rating = place.rating.stars,
                        score = 0.0,
                        distanceKm = null,
                        cityName = place.location.cityName,
                        address = place.location.address,
                        latitude = place.location.latitude,
                        longitude = place.location.longitude
                    )
                }
                .rankedBy(strategy, contextProvider())
        }
    }
}