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
// ============================================================================

/** A change waiting to be sent to Firestore. */
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
    /** Actions of [ownerUid], oldest first. */
    suspend fun pendingFor(ownerUid: String): List<StoredAction>
    suspend fun remove(id: Long)
    suspend fun recordFailure(id: Long)
}

/** The profile as the user will see it once [pending] reaches Firestore. */
fun User.withPending(pending: List<StoredAction>): User =
    pending.map { it.action }
        .filterIsInstance<PendingAction.UpdateProfile>()
        .lastOrNull { it.ownerUid == id }
        ?.let { copy(description = it.description, preferences = it.preferences) }
        ?: this

/** Table `pending_actions` of [EspotiDatabase]; the action's fields go in `payload` as JSON. */
class SqlitePendingActionStore(private val database: EspotiDatabase) : PendingActionStore {

    override suspend fun enqueue(action: PendingAction) {
        withContext(Dispatchers.IO) {
            val (type, targetId, payload) = when (action) {
                is PendingAction.UpdateProfile -> Triple(
                    UPDATE_PROFILE,
                    action.ownerUid,
                    JSONObject()
                        .put("description", action.description)
                        .put("preferences", JSONArray(action.preferences.map { it.name }))
                        .toString()
                )
            }
            // The table's UNIQUE(type, target_id) ON CONFLICT REPLACE drops the older row.
            database.writableDatabase.insert(
                EspotiDatabase.PENDING_ACTIONS,
                null,
                contentValuesOf(
                    "owner_uid" to action.ownerUid,
                    "type" to type,
                    "target_id" to targetId,
                    "payload" to payload,
                    "created_at" to action.createdAt,
                    "attempts" to 0
                )
            )
        }
    }

    override suspend fun pendingFor(ownerUid: String): List<StoredAction> = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            EspotiDatabase.PENDING_ACTIONS, null, "owner_uid = ?", arrayOf(ownerUid),
            null, null, "created_at ASC"
        ).readAll {
            val action = when (string("type")) {
                UPDATE_PROFILE -> {
                    val json = JSONObject(string("payload"))
                    val names = json.getJSONArray("preferences")
                    PendingAction.UpdateProfile(
                        ownerUid = string("owner_uid"),
                        description = json.getString("description"),
                        preferences = List(names.length()) { names.getString(it) }
                            .mapNotNull { name -> ActivityType.entries.firstOrNull { it.name == name } },
                        createdAt = long("created_at")
                    )
                }
                else -> null
            }
            action?.let { StoredAction(id = long("id"), action = it, attempts = int("attempts")) }
        }.filterNotNull()
    }

    override suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(EspotiDatabase.PENDING_ACTIONS, "id = ?", arrayOf(id.toString()))
        }
    }

    override suspend fun recordFailure(id: Long) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.execSQL(
                "UPDATE ${EspotiDatabase.PENDING_ACTIONS} SET attempts = attempts + 1 WHERE id = ?",
                arrayOf<Any>(id)
            )
        }
    }

    private companion object {
        const val UPDATE_PROFILE = "UPDATE_PROFILE"
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryPendingActionStore : PendingActionStore {
    private val actions = mutableListOf<StoredAction>()
    private var nextId = 1L

    val all: List<StoredAction> get() = actions.toList()

    override suspend fun enqueue(action: PendingAction) {
        // Same rule as the SQLite table: one pending profile update per user.
        actions.removeAll { it.action::class == action::class && it.action.ownerUid == action.ownerUid }
        actions += StoredAction(id = nextId++, action = action, attempts = 0)
    }

    override suspend fun pendingFor(ownerUid: String): List<StoredAction> =
        actions.filter { it.action.ownerUid == ownerUid }.sortedBy { it.action.createdAt }

    override suspend fun remove(id: Long) { actions.removeAll { it.id == id } }

    override suspend fun recordFailure(id: Long) {
        val index = actions.indexOfFirst { it.id == id }
        if (index >= 0) actions[index] = actions[index].copy(attempts = actions[index].attempts + 1)
    }
}
