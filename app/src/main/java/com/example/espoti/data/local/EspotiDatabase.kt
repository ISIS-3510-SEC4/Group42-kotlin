package com.example.espoti.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ============================================================================
// LOCAL DATABASE (SQLite) - local storage strategy
// ----------------------------------------------------------------------------
// One private, app-specific SQLite file (espoti.db) that keeps what the user
// needs to keep using the app without internet:
//   users            profile of the signed-in user (Profile / Edit profile)
//   friends          accepted + blocked friends of each signed-in user (Friends)
//   notifications    last friend requests / meeting invites seen (bell panel)
//   pending_actions  changes made offline, waiting to be sent (outbox)
//   user_stats       last "My stats" snapshot of each user (Sprint 4, v2)
// Every row of user data carries the owner's uid, so two accounts on the same
// phone never see each other's data and logout can wipe just one of them.
//
// Plain SQLiteOpenHelper (no Room): no annotation processor in the build, and
// the queries are few and simple. One instance for the whole app (AppContainer);
// SQLiteDatabase is thread-safe, and every store calls it from Dispatchers.IO
// so disk access never runs on the main thread.
// ============================================================================

class EspotiDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $USERS (
                uid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                email TEXT NOT NULL,
                code TEXT NOT NULL,
                description TEXT NOT NULL,
                preferences TEXT NOT NULL,
                max_radius_km INTEGER NOT NULL,
                cached_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $FRIENDS (
                owner_uid TEXT NOT NULL,
                friend_uid TEXT NOT NULL,
                username TEXT NOT NULL,
                relation TEXT NOT NULL,
                position INTEGER NOT NULL,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY (owner_uid, friend_uid, relation)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $NOTIFICATIONS (
                owner_uid TEXT NOT NULL,
                id TEXT NOT NULL,
                type TEXT NOT NULL,
                from_user_id TEXT,
                from_name TEXT NOT NULL,
                title TEXT,
                date_label TEXT,
                position INTEGER NOT NULL,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY (owner_uid, id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $PENDING_ACTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uid TEXT NOT NULL,
                type TEXT NOT NULL,
                target_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                UNIQUE (type, target_id) ON CONFLICT REPLACE
            )
            """.trimIndent()
        )
        createUserStats(db)
        createMeetingNotes(db)
        createMeetingDraftsTable(db)
    }

    private fun createMeetingDraftsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $MEETING_DRAFTS (
                id TEXT PRIMARY KEY,
                owner_uid TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_meeting_drafts_owner
            ON $MEETING_DRAFTS(owner_uid, updated_at)
            """.trimIndent()
        )
    }

    // Step-by-step migrations: each version only adds what it introduced, so
    // pending_actions (changes not sent yet) and the other caches survive an
    // app update.
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createUserStats(db)
        }

        if (oldVersion < 3) {
            createMeetingNotes(db)
            createMeetingDraftsTable(db)
        }

        if (oldVersion <4 ) {
            createUserStats(db)
            createMeetingDraftsTable(db)
            createMeetingNotes(db)}
    }


    /** v2: one JSON snapshot of "My stats" per user. */
    private fun createUserStats(db: SQLiteDatabase) {
        db.execSQL(
            """ 
            CREATE TABLE IF NOT EXISTS $USER_STATS (
                owner_uid TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                computed_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createMeetingNotes(db: SQLiteDatabase) {
        db.execSQL(
            """
        CREATE TABLE IF NOT EXISTS $MEETING_NOTES (
            owner_uid TEXT NOT NULL,
            meeting_id TEXT NOT NULL,
            content TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY (owner_uid, meeting_id)
        )
        """.trimIndent()
        )
    }


    companion object {
        const val NAME = "espoti.db"
        const val VERSION = 4

        const val USERS = "users"
        const val FRIENDS = "friends"
        const val NOTIFICATIONS = "notifications"
        const val PENDING_ACTIONS = "pending_actions"
        const val USER_STATS = "user_stats"
        const val MEETING_DRAFTS = "meeting_drafts"
        const val MEETING_NOTES = "meeting_notes"
    }
}


// ---- Small cursor / values helpers shared by the SQLite stores -------------

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal inline fun <T> Cursor.readAll(read: Cursor.() -> T): List<T> = use {
    buildList { while (moveToNext()) add(read()) }
}

internal fun contentValuesOf(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    pairs.forEach { (key, value) ->
        when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0)
            else -> error("Unsupported column type for $key")
        }
    }
}

/** Runs [block] in one transaction: all rows are written, or none. */
internal inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}
