package com.example.espoti.model

import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.Recommendation
import org.junit.Assert.*
import org.junit.Test

class TransportFiltersTest {

    private fun candidate(
        id: String,
        distance: Double?
    ) = Recommendation(
        placeId = id,
        name = id,
        category = "CAFFE",
        rating = 4,
        score = 0.0,
        distanceKm = distance
    )

    @Test
    fun noFiltersPreserveAllCandidates() {
        val places = listOf(
            candidate("near", 0.5),
            candidate("unknown", null)
        )

        assertEquals(
            places,
            TransportFilterCalculator.apply(
                places,
                TransportFilters()
            )
        )
    }

    @Test
    fun distanceFilterIncludesItsBoundary() {
        val places = listOf(
            candidate("near", 0.5),
            candidate("boundary", 1.0),
            candidate("far", 1.01)
        )

        val result = TransportFilterCalculator.apply(
            places,
            TransportFilters(maxDistanceKm = 1.0)
        )

        assertEquals(
            listOf("near", "boundary"),
            result.map { it.placeId }
        )
    }

    @Test
    fun walkingFilterUsesRoundedEstimatedMinutes() {
        val places = listOf(
            candidate("tenMinutes", 0.6),
            candidate("twelveMinutes", 0.7)
        )

        val result = TransportFilterCalculator.apply(
            places,
            TransportFilters(maxWalkingMinutes = 10)
        )

        assertEquals(
            listOf("tenMinutes"),
            result.map { it.placeId }
        )
    }

    @Test
    fun bothFiltersMustBeSatisfied() {
        val places = listOf(
            candidate("matches", 0.5),
            candidate("tooSlow", 0.9),
            candidate("tooFar", 1.5)
        )

        val result = TransportFilterCalculator.apply(
            places,
            TransportFilters(
                maxDistanceKm = 1.0,
                maxWalkingMinutes = 10
            )
        )

        assertEquals(
            listOf("matches"),
            result.map { it.placeId }
        )
    }

    @Test
    fun activeFiltersExcludeUnknownOrInvalidDistances() {
        val places = listOf(
            candidate("unknown", null),
            candidate("negative", -1.0),
            candidate("nan", Double.NaN),
            candidate("infinite", Double.POSITIVE_INFINITY)
        )

        assertTrue(
            TransportFilterCalculator.apply(
                places,
                TransportFilters(maxDistanceKm = 1.0)
            ).isEmpty()
        )
    }

    @Test
    fun distancesUseTheProvidedStartingLocation() {
        val place = candidate("sameLocation", 999.0).copy(
            latitude = 4.6,
            longitude = -74.0
        )

        val result = TransportFilterCalculator.withDistances(
            listOf(place),
            Location(latitude = 4.6, longitude = -74.0)
        ).single()

        assertEquals("sameLocation", result.placeId)
        assertEquals(0.0, result.distanceKm!!, 0.000001)
    }

    @Test
    fun sharedTravelEstimatesHaveExpectedUnits() {
        val meters = TravelEstimates.distanceMeters(
            latitude1 = 0.0,
            longitude1 = 0.0,
            latitude2 = 0.0,
            longitude2 = 1.0
        )

        assertEquals(111_195.0, meters, 1.0)
        assertEquals(17, TravelEstimates.walkingMinutes(1000.0))
        assertEquals(0, TravelEstimates.walkingMinutes(0.0))
    }
}