
package com.example.espoti.data.repository

import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.PlaceCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Collection places/{id}.
 * Firestore is lazy so ViewModel tests can subclass without Firebase.
 */
open class PlaceRepository(
    dbProvider: () -> FirebaseFirestore = {
        FirebaseFirestore.getInstance()
    }
) {
    private val places by lazy {
        dbProvider().collection("places")
    }

    open suspend fun getPlace(id: String): Result<Place?> =
        runCatching {
            places.document(id)
                .get()
                .await()
                .toObject(Place::class.java)
                ?.copy(id = id)
        }

    open suspend fun getPlaces(
        category: PlaceCategory? = null
    ): Result<List<Place>> =
        runCatching {
            val query = if (category == null) {
                places
            } else {
                places.whereEqualTo("category", category.name)
            }

            query.get().await().documents.mapNotNull { document ->
                document.toObject(Place::class.java)
                    ?.copy(id = document.id)
            }
        }

    /**
     * Returns places within [radiusKm] of the given center.
     * Distance is calculated using the Haversine formula.
     */
    suspend fun getPlacesNearby(
        center: Location,
        radiusKm: Double = 5.0,
        category: PlaceCategory? = null
    ): Result<List<Place>> {
        if (radiusKm <= 0.0) {
            return Result.failure(
                IllegalArgumentException("The radius must be greater than zero.")
            )
        }

        return getPlaces(category).map { allPlaces ->
            allPlaces.mapNotNull { place ->
                val latitude = place.location.latitude
                val longitude = place.location.longitude

                // Ignore coordinates outside valid geographic ranges.
                if (latitude !in -90.0..90.0 ||
                    longitude !in -180.0..180.0
                ) {
                    null
                } else {
                    val distance = distanceKm(
                        center.latitude,
                        center.longitude,
                        latitude,
                        longitude
                    )

                    if (distance <= radiusKm) {
                        place.copy(distance = distance)
                    } else {
                        null
                    }
                }
            }.sortedBy { it.distance }
        }
    }

    private fun distanceKm(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val earthRadiusKm = 6371.0

        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val deltaLat = Math.toRadians(latitude2 - latitude1)
        val deltaLon = Math.toRadians(longitude2 - longitude1)

        val a = sin(deltaLat / 2).let { sinLat ->
            sinLat * sinLat
        } + cos(lat1) * cos(lat2) *
                sin(deltaLon / 2).let { sinLon ->
                    sinLon * sinLon
                }

        val safeA = a.coerceIn(0.0, 1.0)

        return earthRadiusKm * 2 * atan2(
            sqrt(safeA),
            sqrt(1.0 - safeA)
        )
    }

    open suspend fun savePlace(place: Place): Result<String> =
        runCatching {
            val ref = if (place.id.isBlank()) {
                places.document()
            } else {
                places.document(place.id)
            }

            ref.set(place.copy(id = ref.id)).await()
            ref.id
        }
}
