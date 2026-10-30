package com.example.espoti.data.local

import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
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

private fun PendingAction.storageType(): String = when (this) {
    is PendingAction.UpdateProfile -> UPDATE_PROFILE
    is PendingAction.SendFriendRequest -> SEND_FRIEND_REQUEST
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