package com.example.espoti.model.domain

/** A place suggested by the backend (Pinecone similarity, re-ranked by distance). */
data class Recommendation(
    val placeId: String,
    val name: String,
    val category: String,
    val rating: Int,
    val score: Double,
    val distanceKm: Double?,
    // Location of the place; only the Firestore source fills them. Used by the
    // "most selected meeting areas" analytics.
    val cityName: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)
