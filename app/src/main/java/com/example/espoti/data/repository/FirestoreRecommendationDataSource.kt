package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import kotlinx.coroutines.CancellationException

class FirestoreRecommendationDataSource(
    private val repository: PlaceRepository = PlaceRepository()
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
                .sortedWith(compareBy({ it.name }, { it.id }))
                .map { place ->
                    Recommendation(
                        placeId = place.id,
                        name = place.name,
                        category = place.category.name,
                        rating = place.rating.stars,
                        score = 0.0,
                        distanceKm = null
                    )
                }
        }
    }
}