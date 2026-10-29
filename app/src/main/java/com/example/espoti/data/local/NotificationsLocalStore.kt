package com.example.espoti.data.local

import android.database.sqlite.SQLiteDatabase
import com.example.espoti.model.AppNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notifications as they were the last time they were loaded from Firestore. */
data class SavedNotifications(
    val notifications: List<AppNotification>,
    val savedAt: Long
)

/** Last notifications seen by each user, shown when the panel can't reach Firestore. */
interface NotificationsLocalStore {
    /** Null if this user's notifications were never saved on this device. */
    suspend fun get(ownerUid: String): SavedNotifications?
    /** Replaces everything saved for [ownerUid]. */
    suspend fun save(ownerUid: String, notifications: List<AppNotification>)
    suspend fun clear(ownerUid: String)
}

/** Table `notifications` of [EspotiDatabase]; `type` is FRIEND or MEETING. */
class SqliteNotificationsLocalStore(
    private val database: EspotiDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : NotificationsLocalStore {

    override suspend fun get(ownerUid: String): SavedNotifications? = withContext(Dispatchers.IO) {
        val rows = database.readableDatabase.query(
            EspotiDatabase.NOTIFICATIONS, null, "owner_uid = ?", arrayOf(ownerUid),
            null, null, "position ASC"
        ).readAll {
            val notification = when (string("type")) {
                FRIEND -> AppNotification.FriendInvite(
                    id = string("id"),
                    fromUserId = stringOrNull("from_user_id").orEmpty(),
                    fromName = string("from_name")
                )
                MEETING -> AppNotification.MeetingInvite(
                    id = string("id"),
                    fromName = string("from_name"),
                    title = stringOrNull("title").orEmpty(),
                    dateLabel = stringOrNull("date_label")
                )
                else -> null // marker row of an empty save
            }
            notification to long("cached_at")
        }
        if (rows.isEmpty()) return@withContext null
        SavedNotifications(
            notifications = rows.mapNotNull { it.first },
            savedAt = rows.maxOf { it.second }
        )
    }

    override suspend fun save(ownerUid: String, notifications: List<AppNotification>) {
        withContext(Dispatchers.IO) {
            val now = clock()
            database.writableDatabase.inTransaction {
                delete(EspotiDatabase.NOTIFICATIONS, "owner_uid = ?", arrayOf(ownerUid))
                if (notifications.isEmpty()) {
                    // Marker row: "saved, but there was nothing" (vs. never saved).
                    insert(EspotiDatabase.NOTIFICATIONS, null, contentValuesOf(
                        "owner_uid" to ownerUid, "id" to "", "type" to NONE, "from_name" to "",
                        "position" to 0, "cached_at" to now
                    ))
                }
                notifications.forEachIndexed { index, notification ->
                    val values = when (notification) {
                        is AppNotification.FriendInvite -> contentValuesOf(
                            "owner_uid" to ownerUid,
                            "id" to notification.id,
                            "type" to FRIEND,
                            "from_user_id" to notification.fromUserId,
                            "from_name" to notification.fromName,
                            "position" to index,
                            "cached_at" to now
                        )
                        is AppNotification.MeetingInvite -> contentValuesOf(
                            "owner_uid" to ownerUid,
                            "id" to notification.id,
                            "type" to MEETING,
                            "from_name" to notification.fromName,
                            "title" to notification.title,
                            "date_label" to notification.dateLabel,
                            "position" to index,
                            "cached_at" to now
                        )
                    }
                    insertWithOnConflict(EspotiDatabase.NOTIFICATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        }
    }

    override suspend fun clear(ownerUid: String) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(EspotiDatabase.NOTIFICATIONS, "owner_uid = ?", arrayOf(ownerUid))
        }
    }

    private companion object {
        const val FRIEND = "FRIEND"
        const val MEETING = "MEETING"
        const val NONE = "NONE"
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryNotificationsLocalStore(
    private val clock: () -> Long = System::currentTimeMillis
) : NotificationsLocalStore {
    private val saved = mutableMapOf<String, SavedNotifications>()
    override suspend fun get(ownerUid: String): SavedNotifications? = saved[ownerUid]
    override suspend fun save(ownerUid: String, notifications: List<AppNotification>) {
        saved[ownerUid] = SavedNotifications(notifications, clock())
    }
    override suspend fun clear(ownerUid: String) { saved.remove(ownerUid) }
}
