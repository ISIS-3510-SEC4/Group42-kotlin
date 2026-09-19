package com.example.espoti.ui.model

data class Meeting(
    val id: Int,
    val name: String,
    val rating: Int,
    val travelTime: String,
    val distance: String
)
val sampleMeetings = listOf(
    Meeting(
        id = 1,
        name = "Restaurant Los Andes",
        rating = 4,
        travelTime = "30 Minutes",
        distance = "3km from your location"
    ),
    Meeting(
        id = 2,
        name = "Cafeteria Doña Blanca",
        rating = 5,
        travelTime = "20 Minutes",
        distance = "2km from your location"
    )
)