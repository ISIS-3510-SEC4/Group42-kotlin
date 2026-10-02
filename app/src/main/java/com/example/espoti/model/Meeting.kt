package com.example.espoti.model

data class Meeting(
    val id: Int,
    val name: String,
    val rating: Int,
    val travelTime: String,
    val distance: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startsAtEpochMillis: Long? = null
)