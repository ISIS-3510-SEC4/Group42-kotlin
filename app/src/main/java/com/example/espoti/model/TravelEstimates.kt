package com.example.espoti.model

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TravelEstimates {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val WALKING_METERS_PER_MINUTE = 80.0
    private const val DETOUR_FACTOR = 1.3

    fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        require(latitude1 in -90.0..90.0)
        require(longitude1 in -180.0..180.0)
        require(latitude2 in -90.0..90.0)
        require(longitude2 in -180.0..180.0)

        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(longitude2 - longitude1)

        val sinLat = sin(deltaLat / 2)
        val sinLon = sin(deltaLon / 2)

        val a = (
                sinLat * sinLat +
                        cos(lat1) * cos(lat2) * sinLon * sinLon
                ).coerceIn(0.0, 1.0)

        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
    }

    fun walkingMinutes(distanceMeters: Double): Int {
        require(distanceMeters.isFinite() && distanceMeters >= 0)

        return ceil(
            distanceMeters * DETOUR_FACTOR /
                    WALKING_METERS_PER_MINUTE
        ).toInt()
    }
}