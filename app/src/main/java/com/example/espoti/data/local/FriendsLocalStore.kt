package com.example.espoti.data.local

import android.database.sqlite.SQLiteDatabase
import com.example.espoti.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Friends list as it was the last time it was loaded from Firestore. */
data class SavedFriends(
    val friends: List<Friend>,
    val blocked: List<Friend>,
    val savedAt: Long
)

/** Accepted and blocked friends of each user, so Friends works offline (cache then network). */
interface FriendsLocalStore {
    /** Null if this user's friends were never saved on this device. */
    suspend fun get(ownerUid: String): SavedFriends?
    /** Replaces everything saved for [ownerUid]. */
    suspend fun save(ownerUid: String, friends: List<Friend>, blocked: List<Friend>)
    suspend fun clear(ownerUid: String)
}

/** Table `friends` of [EspotiDatabase]; `relation` is ACCEPTED or BLOCKED. */
class SqliteFriendsLocalStore(
    private val database: EspotiDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : FriendsLocalStore {

    private data class Row(val friend: Friend, val relation: String, val savedAt: Long)

    override suspend fun get(ownerUid: String): SavedFriends? = withContext(Dispatchers.IO) {
        val rows = database.readableDatabase.query(
            EspotiDatabase.FRIENDS, null, "owner_uid = ?", arrayOf(ownerUid),
            null, null, "position ASC"
        ).readAll {
            Row(Friend(name = string("username"), userId = string("friend_uid")), string("relation"), long("cached_at"))
        }
        // A save with no friends at all leaves a marker row (relation NONE) so
        // "never saved" and "saved, but empty" can be told apart.
        if (rows.isEmpty()) return@withContext null
        SavedFriends(
            friends = rows.filter { it.relation == ACCEPTED }.map { it.friend },
            blocked = rows.filter { it.relation == BLOCKED }.map { it.friend },
            savedAt = rows.maxOf { it.savedAt }
        )
    }

    override suspend fun save(ownerUid: String, friends: List<Friend>, blocked: List<Friend>) {
        withContext(Dispatchers.IO) {
            val now = clock()
            database.writableDatabase.inTransaction {
                delete(EspotiDatabase.FRIENDS, "owner_uid = ?", arrayOf(ownerUid))
                val rows = friends.map { it to ACCEPTED } + blocked.map { it to BLOCKED }
                val toInsert = rows.ifEmpty { listOf(Friend(name = "", userId = "") to NONE) }
                toInsert.forEachIndexed { index, (friend, relation) ->
                    insertWithOnConflict(
                        EspotiDatabase.FRIENDS,
                        null,
                        contentValuesOf(
                            "owner_uid" to ownerUid,
                            "friend_uid" to friend.userId,
                            "username" to friend.name,
                            "relation" to relation,
                            "position" to index,
                            "cached_at" to now
                        ),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
        }
    }

    override suspend fun clear(ownerUid: String) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(EspotiDatabase.FRIENDS, "owner_uid = ?", arrayOf(ownerUid))
        }
    }

    private companion object {
        const val ACCEPTED = "ACCEPTED"
        const val BLOCKED = "BLOCKED"
        const val NONE = "NONE"
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryFriendsLocalStore(
    private val clock: () -> Long = System::currentTimeMillis
) : FriendsLocalStore {
    private val saved = mutableMapOf<String, SavedFriends>()
    override suspend fun get(ownerUid: String): SavedFriends? = saved[ownerUid]
    override suspend fun save(ownerUid: String, friends: List<Friend>, blocked: List<Friend>) {
        saved[ownerUid] = SavedFriends(friends, blocked, clock())
    }
    override suspend fun clear(ownerUid: String) { saved.remove(ownerUid) }
}
