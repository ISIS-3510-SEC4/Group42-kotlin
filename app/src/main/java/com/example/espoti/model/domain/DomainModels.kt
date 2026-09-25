package com.example.espoti.model.domain

// ============================================================================
// DOMAIN MODELS (Firestore documents)
// ----------------------------------------------------------------------------
// Mirror the "Modelo de Dominio" diagram. Every property has a default value
// so Firestore can build the object (it needs a no-arg constructor).
// - Ids are Firestore document ids (String), not Int.
// - Dates are epoch millis (Long).
// - The diagram's User.password is NOT stored: Firebase Auth owns credentials.
// - Friend / Admin (User subclasses) are modelled as User.type.
// ============================================================================

data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val cityName: String = ""
)

data class Place(
    val id: String = "",
    val name: String = "",
    val rating: Rating = Rating.THREE,
    val distance: Double = 0.0,
    val placeImg: String = "",
    val category: PlaceCategory = PlaceCategory.RESTAURANT,
    val location: Location = Location()
)

/** Document users/{uid}. Preferences are embedded (a user's list of activity types). */
data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val code: String = "",
    val description: String = "",
    val profileImg: String = "",
    val type: UserType = UserType.FRIEND,
    val preferences: List<ActivityType> = emptyList()
)

/** Document friendships/{id}. [userIds] always holds the 2 users (diagram 2..2). */
data class Friendship(
    val id: String = "",
    val userIds: List<String> = emptyList(),
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val dateAdded: Long = 0L
)

/** Document meetings/{id}. Named MeetingEvent to not clash with the UI-level Meeting. */
data class MeetingEvent(
    val id: String = "",
    val creatorId: String = "",
    val participantIds: List<String> = emptyList(),
    val date: Long = 0L,
    val time: String = "",
    val activity: ActivityType = ActivityType.OTHER,
    val status: StatusMeeting = StatusMeeting.UPCOMING,
    val placeId: String? = null,
    val location: Location? = null,
    val timeEstimated: String = ""
)

/** Sub-collection meetings/{id}/votes. */
data class Vote(
    val id: String = "",
    val userId: String = "",
    val selected: Boolean = false,
    val timeStamp: Long = 0L
)

/** Sub-collection meetings/{id}/reviews. */
data class Review(
    val id: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val date: Long = 0L
)

/** Sub-collection meetings/{id}/memories. */
data class Memory(
    val id: String = "",
    val imageUrl: String = "",
    val date: Long = 0L
)
