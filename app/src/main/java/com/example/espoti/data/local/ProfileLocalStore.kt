package com.example.espoti.data.local

import android.database.sqlite.SQLiteDatabase
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The signed-in user's own profile, saved on the device so Profile and Edit
 * profile can show it without internet (cache then network).
 */
interface ProfileLocalStore {
    suspend fun get(uid: String): User?
    suspend fun save(user: User)
    suspend fun clear(uid: String)
}

/** Table `users` of [EspotiDatabase]. */
class SqliteProfileLocalStore(
    private val database: EspotiDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : ProfileLocalStore {

    override suspend fun get(uid: String): User? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            EspotiDatabase.USERS, null, "uid = ?", arrayOf(uid), null, null, null
        ).readAll {
            User(
                id = string("uid"),
                username = string("username"),
                email = string("email"),
                code = string("code"),
                description = string("description"),
                preferences = decodePreferences(string("preferences")),
                maxRadiusKm = int("max_radius_km")
            )
        }.firstOrNull()
    }

    override suspend fun save(user: User) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.insertWithOnConflict(
                EspotiDatabase.USERS,
                null,
                contentValuesOf(
                    "uid" to user.id,
                    "username" to user.username,
                    "email" to user.email,
                    "code" to user.code,
                    "description" to user.description,
                    "preferences" to encodePreferences(user.preferences),
                    "max_radius_km" to user.maxRadiusKm,
                    "cached_at" to clock()
                ),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    override suspend fun clear(uid: String) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(EspotiDatabase.USERS, "uid = ?", arrayOf(uid))
        }
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryProfileLocalStore : ProfileLocalStore {
    private val users = mutableMapOf<String, User>()
    override suspend fun get(uid: String): User? = users[uid]
    override suspend fun save(user: User) { users[user.id] = user }
    override suspend fun clear(uid: String) { users.remove(uid) }
}

// Preferences are stored as "COFFEE,BAR" (enum names); unknown names are skipped.
internal fun encodePreferences(preferences: List<ActivityType>): String =
    preferences.joinToString(",") { it.name }

internal fun decodePreferences(text: String): List<ActivityType> =
    text.split(",").mapNotNull { name -> ActivityType.entries.firstOrNull { it.name == name.trim() } }
