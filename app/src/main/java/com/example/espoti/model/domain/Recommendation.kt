package com.example.espoti.model.domain

/** A place suggested by the backend (Pinecone similarity, re-ranked by distance). */
data class Recommendation(
    val placeId: String,
    val name: String,
    val category: String,
    val rating: Int,
    val score: Double,
    val distanceKm: Double?,
    // Location of the place (both sources fill it when the data has it). Used
    // by the "most selected meeting areas" analytics.
    val cityName: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** A stored place shown as a recommendation card (Firestore fallback, meeting candidates). */
fun Place.toRecommendation(): Recommendation = Recommendation(
    placeId = id,
    name = name,
    category = category.name,
    rating = rating.stars,
    score = 0.0,
    distanceKm = null,
    cityName = location.cityName,
    address = location.address,
    latitude = location.latitude,
    longitude = location.longitude
)
