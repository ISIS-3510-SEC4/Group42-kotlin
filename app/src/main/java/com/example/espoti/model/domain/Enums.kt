package com.example.espoti.model.domain

// Enums from the "Modelo de Dominio" component diagram.
// Stored in Firestore by name (Firestore maps enums to/from strings).

enum class ActivityType { COFFEE, PARK, RESTAURANT, BAR, CINEMA, SPORT, OTHER }

enum class FriendshipStatus { PENDING, ACCEPTED, BLOCKED }

enum class Rating(val stars: Int) { ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5) }

enum class PlaceCategory { RESTAURANT, CAFFE, BAR }

enum class StatusMeeting { UPCOMING, PREVIOUS, CANCELED }

enum class UserType { FRIEND, ADMIN }
