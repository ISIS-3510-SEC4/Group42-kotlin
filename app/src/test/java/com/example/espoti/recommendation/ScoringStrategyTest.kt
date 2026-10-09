package com.example.espoti.recommendation

import com.example.espoti.model.domain.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringStrategyTest {

    private fun place(id: String, rating: Int, lat: Double? = null, lng: Double? = null) =
        Recommendation(id, "P$id", "RESTAURANT", rating, 0.0, null, latitude = lat, longitude = lng)

    private val places = listOf(
        place("1", 3, 4.60, -74.08),
        place("2", 5, 4.90, -74.00),
        place("3", 4, 4.61, -74.07)
    )
    private val here = RecommendationContext(userLatitude = 4.60, userLongitude = -74.08)

    private fun ids(strategy: RecommendationScoringStrategy) =
        places.rankedBy(strategy, here).map { it.placeId }

    @Test fun alphabetical_keepsNameOrder() = assertEquals(listOf("1", "2", "3"), ids(AlphabeticalStrategy))

    @Test fun rating_bestFirst() = assertEquals(listOf("2", "3", "1"), ids(RatingStrategy))

    @Test fun proximity_closestFirst() = assertEquals(listOf("1", "3", "2"), ids(ProximityStrategy))

    @Test fun proximity_withoutLocation_scoresZero() =
        assertEquals(0.0, ProximityStrategy.score(places[0], RecommendationContext()), 0.0)

    @Test fun weighted_combinesStrategies() {
        val s = WeightedStrategy(listOf(RatingStrategy to 0.5, ProximityStrategy to 0.5))
        assertEquals(listOf("1", "3", "2"), ids(s))
    }
}
