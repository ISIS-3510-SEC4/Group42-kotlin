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
    val preferences: List<ActivityType> = emptyList(),
    // Diagram's "maxium: ENUM" (Profile screen's "Maximum radio"): stored as a
    // plain km radius instead of an enum, matching the km units the backend's
    // recommendation proximity search already uses.
    val maxRadiusKm: Int = 10,
    // True once the user enrolled biometric login on a device (set from Edit Profile).
    // The secret itself never leaves the device: only this flag is stored.
    val biometricEnabled: Boolean = false
)

/**
 * Document friendships/{id}. [userIds] always holds the 2 users (diagram 2..2).
 * When [status] is BLOCKED, [blockedBy] is the uid who blocked (only they can
 * unblock); a block stops requests and invites in both directions.
 */
data class Friendship(
    val id: String = "",
    val userIds: List<String> = emptyList(),
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val dateAdded: Long = 0L,
    val blockedBy: String = ""
)

/**
 * Document meetings/{id}. Named MeetingEvent to not clash with the UI-level Meeting.
 * Lifecycle: created as VOTING with [invitedIds] (friends who haven't answered
 * yet) and [candidatePlaceIds] (the places everybody votes on). Accepting an
 * invite moves the uid from [invitedIds] to [participantIds]. When everybody
 * voted or [votingDeadline] passes, [placeId] gets the winner and the status
 * becomes UPCOMING (see VotingRules / MeetingEventRepository.closeVotingIfDone).
 */
data class MeetingEvent(
    val id: String = "",
    val creatorId: String = "",
    val participantIds: List<String> = emptyList(),
    val invitedIds: List<String> = emptyList(),
    val candidatePlaceIds: List<String> = emptyList(),
    val date: Long = 0L,
    val time: String = "",
    val activity: ActivityType = ActivityType.OTHER,
    val status: StatusMeeting = StatusMeeting.UPCOMING,
    val placeId: String? = null,
    val location: Location? = null,
    val startingLocations: Map<String, Location> = emptyMap(),
    val centerLocation: Location? = null,
    val timeEstimated: String = "",
    val votingDeadline: Long = 0L,
    val createdAt: Long = 0L,
    // uid -> voted placeId. Mirrors votes/{uid} so the closing transaction can
    // read every vote from this single document (transactions can't query).
    val votes: Map<String, String> = emptyMap()
) {
    /** [votes] as Vote objects, the shape VotingRules works with. */
    fun voteList(): List<Vote> = votes.map { (uid, placeId) ->
        Vote(id = uid, userId = uid, placeId = placeId, selected = true)
    }
}

/** Sub-collection meetings/{id}/votes, doc id = voter uid (one vote per user). */
data class Vote(
    val id: String = "",
    val userId: String = "",
    val placeId: String = "",
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
