package com.example.espoti.model

import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.Recommendation
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TransportFilters(
    val maxDistanceKm: Double? = null,
    val maxWalkingMinutes: Int? = null
) {
    init {
        require(
            maxDistanceKm == null ||
                    (maxDistanceKm.isFinite() && maxDistanceKm > 0)
        )

        require(
            maxWalkingMinutes == null || maxWalkingMinutes > 0
        )
    }

    val isActive: Boolean
        get() = maxDistanceKm != null || maxWalkingMinutes != null
}

object TransportFilterCalculator {

    fun distanceKm(
        origin: Location?,
        latitude: Double?,
        longitude: Double?
    ): Double? {
        if (origin == null || latitude == null || longitude == null) {
            return null
        }

        if (
            origin.latitude !in -90.0..90.0 ||
            origin.longitude !in -180.0..180.0 ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return null
        }

        return TravelEstimates.distanceMeters(
            latitude1 = origin.latitude,
            longitude1 = origin.longitude,
            latitude2 = latitude,
            longitude2 = longitude
        ) / 1000.0
    }

    fun walkingMinutes(distanceKm: Double?): Int? {
        if (
            distanceKm == null ||
            !distanceKm.isFinite() ||
            distanceKm < 0
        ) {
            return null
        }

        val meters = distanceKm * 1000.0
        if (!meters.isFinite()) return null

        return TravelEstimates.walkingMinutes(meters)
    }

    fun withDistances(
        candidates: List<Recommendation>,
        origin: Location?
    ): List<Recommendation> {
        return candidates.map { candidate ->
            candidate.copy(
                distanceKm = distanceKm(
                    origin = origin,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude
                )
            )
        }
    }

    fun apply(
        candidates: List<Recommendation>,
        filters: TransportFilters
    ): List<Recommendation> {
        if (!filters.isActive) return candidates

        return candidates.filter { candidate ->
            val distance = candidate.distanceKm

            if (
                distance == null ||
                !distance.isFinite() ||
                distance < 0
            ) {
                false
            } else {
                val matchesDistance =
                    filters.maxDistanceKm?.let { distance <= it } ?: true

                val matchesWalking =
                    filters.maxWalkingMinutes?.let { maximum ->
                        val minutes = walkingMinutes(distance)
                        minutes != null && minutes <= maximum
                    } ?: true

                matchesDistance && matchesWalking
            }
        }
    }
}