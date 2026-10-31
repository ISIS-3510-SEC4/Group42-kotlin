package com.example.espoti.data.local

import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.DecisionMethod
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.User
import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.StatusMeeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// PENDING ACTIONS (offline outbox)
// ----------------------------------------------------------------------------
// Changes the user made without internet. They are stored here first and a
// background worker (data/sync/SyncWorker) sends them once the device is
// online again, even if the app was closed in between.
//
// Profile updates are removed after sending. Friend requests keep their final
// result so the Friends screen can explain what happened.
// ============================================================================

enum class FriendRequestState {
    PENDING,
    COMPLETED,
    FAILED
}

/** An offline change or a friend request with its delivery result. */
sealed class PendingAction {
    abstract val ownerUid: String
    abstract val createdAt: Long

    /** Edit profile saved offline: only the fields that can change offline. */
    data class UpdateProfile(
        override val ownerUid: String,
        val description: String,
        val preferences: List<ActivityType>,
        override val createdAt: Long
    ) : PendingAction()

    /** A confirmed friend request, saved for delivery when online. */
    data class SendFriendRequest(
        override val ownerUid: String,
        val targetUid: String,
        val targetName: String,
        override val createdAt: Long,
        val state: FriendRequestState = FriendRequestState.PENDING,
        val resultMessage: String? = null
    ) : PendingAction()

    data class CreateMeeting(
        override val ownerUid: String,
        val meeting: MeetingEvent,
        override val createdAt: Long
    ) : PendingAction()

    data class SubmitStartingLocation(
        override val ownerUid: String,
        val meetingId: String,
        val location: Location,
        override val createdAt: Long
    ) : PendingAction()

    data class CastVote(
        override val ownerUid: String,
        val meetingId: String,
        val placeId: String,
        override val createdAt: Long
    ) : PendingAction()
}

/** A [PendingAction] as stored: its row id and how many times sending it failed. */
data class StoredAction(
    val id: Long,
    val action: PendingAction,
    val attempts: Int
)

interface PendingActionStore {
    /** Adds [action]; a newer action for the same target replaces the older one. */
    suspend fun enqueue(action: PendingAction)

    /** Records of [ownerUid], oldest first, including friend request results. */
    suspend fun pendingFor(ownerUid: String): List<StoredAction>

    suspend fun remove(id: Long)

    suspend fun recordFailure(id: Long)

    /** Updates a request's result without replacing a newer queued request. */
    suspend fun updateFriendRequest(
        id: Long,
        action: PendingAction.SendFriendRequest
    )
}

/** The profile as the user will see it once [pending] reaches Firestore. */
fun User.withPending(pending: List<StoredAction>): User =
    pending.map { it.action }
        .filterIsInstance<PendingAction.UpdateProfile>()
        .lastOrNull { it.ownerUid == id }
        ?.let {
            copy(
                description = it.description,
                preferences = it.preferences
            )
        }
        ?: this

private const val UPDATE_PROFILE = "UPDATE_PROFILE"
private const val SEND_FRIEND_REQUEST = "SEND_FRIEND_REQUEST"
private const val CREATE_MEETING = "CREATE_MEETING"
private const val SUBMIT_STARTING_LOCATION = "SUBMIT_STARTING_LOCATION"
private const val CAST_VOTE = "CAST_VOTE"

private fun PendingAction.storageType(): String = when (this) {
    is PendingAction.UpdateProfile -> UPDATE_PROFILE
    is PendingAction.SendFriendRequest -> SEND_FRIEND_REQUEST
    is PendingAction.CreateMeeting -> CREATE_MEETING
    is PendingAction.SubmitStartingLocation -> SUBMIT_STARTING_LOCATION
    is PendingAction.CastVote -> CAST_VOTE
}

/**
 * Profile updates keep their existing key. Friend requests include both
 * account ids, so requests to different people or from different accounts
 * never replace each other.
 */
private fun PendingAction.targetKey(): String = when (this) {
    is PendingAction.UpdateProfile -> ownerUid

    is PendingAction.SendFriendRequest ->
        "${ownerUid.length}:$ownerUid${targetUid.length}:$targetUid"

    is PendingAction.CreateMeeting ->
        "${ownerUid.length}:$ownerUid${meeting.id.length}:${meeting.id}"

    is PendingAction.SubmitStartingLocation ->
        "${ownerUid.length}:$ownerUid${meetingId.length}:$meetingId"

    is PendingAction.CastVote ->
        "${ownerUid.length}:$ownerUid${meetingId.length}:$meetingId"
}

private fun PendingAction.payload(): String = when (this) {
    is PendingAction.UpdateProfile -> JSONObject()
        .put("description", description)
        .put("preferences", JSONArray(preferences.map { it.name }))
        .toString()

    is PendingAction.SendFriendRequest -> JSONObject()
        .put("targetUid", targetUid)
        .put("targetName", targetName)
        .put("state", state.name)
        .put("resultMessage", resultMessage ?: JSONObject.NULL)
        .toString()

    is PendingAction.CreateMeeting -> JSONObject()
        .put("meeting", meeting.toJson())
        .toString()

    is PendingAction.SubmitStartingLocation -> JSONObject()
        .put("meetingId", meetingId)
        .put("location", location.toJson())
        .toString()

    is PendingAction.CastVote -> JSONObject()
        .put("meetingId", meetingId)
        .put("placeId", placeId)
        .toString()
}

/** Table `pending_actions` of [EspotiDatabase]; the action's fields go in `payload` as JSON. */
class SqlitePendingActionStore(
    private val database: EspotiDatabase
) : PendingActionStore {

    override suspend fun enqueue(action: PendingAction) {
        withContext(Dispatchers.IO) {
            // The table's UNIQUE(type, target_id) ON CONFLICT REPLACE drops the older row.
            val insertedId = database.writableDatabase.insertOrThrow(
                EspotiDatabase.PENDING_ACTIONS,
                null,
                contentValuesOf(
                    "owner_uid" to action.ownerUid,
                    "type" to action.storageType(),
                    "target_id" to action.targetKey(),
                    "payload" to action.payload(),
                    "created_at" to action.createdAt,
                    "attempts" to 0
                )
            )

            check(insertedId != -1L) {
                "Couldn't save the pending action."
            }
        }
    }



    override suspend fun pendingFor(
        ownerUid: String
    ): List<StoredAction> = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            EspotiDatabase.PENDING_ACTIONS,
            null,
            "owner_uid = ?",
            arrayOf(ownerUid),
            null,
            null,
            "created_at ASC, id ASC"
        ).readAll {
            val action = when (string("type")) {
                UPDATE_PROFILE -> {
                    val json = JSONObject(string("payload"))
                    val names = json.getJSONArray("preferences")

                    PendingAction.UpdateProfile(
                        ownerUid = string("owner_uid"),
                        description = json.getString("description"),
                        preferences = List(names.length()) {
                            names.getString(it)
                        }.mapNotNull { name ->
                            ActivityType.entries.firstOrNull {
                                it.name == name
                            }
                        },
                        createdAt = long("created_at")
                    )
                }

                SEND_FRIEND_REQUEST -> {
                    val json = JSONObject(string("payload"))

                    PendingAction.SendFriendRequest(
                        ownerUid = string("owner_uid"),
                        targetUid = json.getString("targetUid"),
                        targetName = json.getString("targetName"),
                        createdAt = long("created_at"),
                        state = FriendRequestState.valueOf(
                            json.getString("state")
                        ),
                        resultMessage = if (
                            json.isNull("resultMessage")
                        ) {
                            null
                        } else {
                            json.getString("resultMessage")
                        }
                    )
                }
                CREATE_MEETING -> {
                    decodeCreateMeeting(
                        ownerUid = string("owner_uid"),
                        payload = string("payload"),
                        createdAt = long("created_at")
                    )

                }

                SUBMIT_STARTING_LOCATION -> {
                    val json = JSONObject(string("payload"))

                    PendingAction.SubmitStartingLocation(
                        ownerUid = string("owner_uid"),
                        meetingId = json.getString("meetingId"),
                        location = json.getJSONObject("location").toLocation(),
                        createdAt = long("created_at")
                    )
                }

                CAST_VOTE -> {
                    val json = JSONObject(string("payload"))

                    PendingAction.CastVote(
                        ownerUid = string("owner_uid"),
                        meetingId = json.getString("meetingId"),
                        placeId = json.getString("placeId"),
                        createdAt = long("created_at")
                    )
                }



                else -> null
            }

            action?.let {
                StoredAction(
                    id = long("id"),
                    action = it,
                    attempts = int("attempts")
                )
            }
        }.filterNotNull()
    }

    override suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(
                EspotiDatabase.PENDING_ACTIONS,
                "id = ?",
                arrayOf(id.toString())
            )
        }
    }

    override suspend fun recordFailure(id: Long) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.execSQL(
                """
                UPDATE ${EspotiDatabase.PENDING_ACTIONS}
                SET attempts = attempts + 1
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any>(id)
            )
        }
    }

    override suspend fun updateFriendRequest(
        id: Long,
        action: PendingAction.SendFriendRequest
    ) {
        withContext(Dispatchers.IO) {
            // Match the original row id. If another action replaced it while
            // syncing, its newer data must remain untouched.
            database.writableDatabase.update(
                EspotiDatabase.PENDING_ACTIONS,
                contentValuesOf("payload" to action.payload()),
                "id = ? AND owner_uid = ? AND type = ?",
                arrayOf(
                    id.toString(),
                    action.ownerUid,
                    SEND_FRIEND_REQUEST
                )
            )
        }
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryPendingActionStore : PendingActionStore {
    private val actions = mutableListOf<StoredAction>()
    private var nextId = 1L

    val all: List<StoredAction>
        get() = actions.toList()

    override suspend fun enqueue(action: PendingAction) {
        // Same replacement rule as the SQLite table.
        actions.removeAll {
            it.action.storageType() == action.storageType() &&
                    it.action.targetKey() == action.targetKey()
        }

        actions += StoredAction(
            id = nextId++,
            action = action,
            attempts = 0
        )
    }

    override suspend fun pendingFor(
        ownerUid: String
    ): List<StoredAction> =
        actions.filter { it.action.ownerUid == ownerUid }
            .sortedWith(
                compareBy<StoredAction> { it.action.createdAt }
                    .thenBy { it.id }
            )

    override suspend fun remove(id: Long) {
        actions.removeAll { it.id == id }
    }

    override suspend fun recordFailure(id: Long) {
        val index = actions.indexOfFirst { it.id == id }

        if (index >= 0) {
            actions[index] = actions[index].copy(
                attempts = actions[index].attempts + 1
            )
        }
    }

    override suspend fun updateFriendRequest(
        id: Long,
        action: PendingAction.SendFriendRequest
    ) {
        val index = actions.indexOfFirst {
            it.id == id &&
                    it.action.ownerUid == action.ownerUid &&
                    it.action is PendingAction.SendFriendRequest
        }

        if (index >= 0) {
            actions[index] = actions[index].copy(action = action)
        }
    }
}

private fun Location.toJson(): JSONObject = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)
    .put("address", address)
    .put("cityName", cityName)

private fun MeetingEvent.toJson(): JSONObject {
    val startingLocationsJson = JSONObject()

    startingLocations.forEach { (uid, location) ->
        startingLocationsJson.put(uid, location.toJson())
    }

    val votesJson = JSONObject()

    votes.forEach { (uid, placeId) ->
        votesJson.put(uid, placeId)
    }

    return JSONObject()
        .put("id", id)
        .put("creatorId", creatorId)
        .put("participantIds", JSONArray(participantIds))
        .put("invitedIds", JSONArray(invitedIds))
        .put("candidatePlaceIds", JSONArray(candidatePlaceIds))
        .put("date", date)
        .put("time", time)
        .put("activity", activity.name)
        .put("status", status.name)
        .put("placeId", placeId ?: JSONObject.NULL)
        .put("location", location?.toJson() ?: JSONObject.NULL)
        .put("startingLocations", startingLocationsJson)
        .put("centerLocation", centerLocation?.toJson() ?: JSONObject.NULL)
        .put("timeEstimated", timeEstimated)
        .put("votingDeadline", votingDeadline)
        .put("createdAt", createdAt)
        .put("votes", votesJson)
        .put("decisionMethod", decisionMethod?.name ?: JSONObject.NULL)
}


private fun JSONObject.toLocation(): Location = Location(
    latitude = optDouble("latitude"),
    longitude = optDouble("longitude"),
    address = optString("address"),
    cityName = optString("cityName")
)

private fun JSONObject.toMeetingEvent(): MeetingEvent {
    val startingLocationsJson = optJSONObject("startingLocations")
        ?: JSONObject()

    val startingLocations = mutableMapOf<String, Location>()

    val locationKeys = startingLocationsJson.keys()

    while (locationKeys.hasNext()) {
        val uid = locationKeys.next()
        val locationJson = startingLocationsJson.optJSONObject(uid)

        if (locationJson != null) {
            startingLocations[uid] = locationJson.toLocation()
        }
    }

    val votesJson = optJSONObject("votes") ?: JSONObject()
    val votes = mutableMapOf<String, String>()

    val voteKeys = votesJson.keys()

    while (voteKeys.hasNext()) {
        val uid = voteKeys.next()
        val placeId = votesJson.optString(uid)

        if (placeId.isNotBlank()) {
            votes[uid] = placeId
        }
    }

    val locationJson = optJSONObject("location")
    val centerLocationJson = optJSONObject("centerLocation")

    return MeetingEvent(
        id = optString("id"),
        creatorId = optString("creatorId"),
        participantIds = optJSONArray("participantIds").toStringList(),
        invitedIds = optJSONArray("invitedIds").toStringList(),
        candidatePlaceIds = optJSONArray("candidatePlaceIds").toStringList(),
        date = optLong("date"),
        time = optString("time"),
        activity = ActivityType.valueOf(optString("activity")),
        status = StatusMeeting.valueOf(optString("status")),
        placeId = optNullableString("placeId"),
        location = locationJson?.toLocation(),
        startingLocations = startingLocations,
        centerLocation = centerLocationJson?.toLocation(),
        timeEstimated = optString("timeEstimated"),
        votingDeadline = optLong("votingDeadline"),
        createdAt = optLong("createdAt"),
        votes = votes,
        decisionMethod = optNullableString("decisionMethod")
            ?.let { DecisionMethod.valueOf(it) }
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()

    return List(length()) { index ->
        optString(index)
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null

    return optString(key).takeIf { it.isNotBlank() }
}

private fun decodeCreateMeeting(
    ownerUid: String,
    payload: String,
    createdAt: Long
): PendingAction.CreateMeeting {
    val json = JSONObject(payload)
    val meetingJson = json.getJSONObject("meeting")

    return PendingAction.CreateMeeting(
        ownerUid = ownerUid,
        meeting = meetingJson.toMeetingEvent(),
        createdAt = createdAt
    )
}