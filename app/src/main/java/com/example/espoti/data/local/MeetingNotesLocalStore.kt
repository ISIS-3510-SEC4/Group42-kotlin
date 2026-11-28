
package com.example.espoti.data.local

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MeetingNote(
    val meetingId: String,
    val content: String,
    val updatedAt: Long
)

interface MeetingNotesLocalStore {
    suspend fun get(ownerUid: String, meetingId: String): MeetingNote?

    suspend fun save(
        ownerUid: String,
        meetingId: String,
        content: String
    )

    suspend fun delete(ownerUid: String, meetingId: String)

    suspend fun clear(ownerUid: String)
}

class SqliteMeetingNotesLocalStore(
    private val database: EspotiDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : MeetingNotesLocalStore {

    override suspend fun get(
        ownerUid: String,
        meetingId: String
    ): MeetingNote? = withContext(Dispatchers.IO) {

        database.readableDatabase.query(
            EspotiDatabase.MEETING_NOTES,
            null,
            "owner_uid = ? AND meeting_id = ?",
            arrayOf(ownerUid, meetingId),
            null,
            null,
            null
        ).readAll {
            MeetingNote(
                meetingId = string("meeting_id"),
                content = string("content"),
                updatedAt = long("updated_at")
            )
        }.firstOrNull()
    }

    override suspend fun save(
        ownerUid: String,
        meetingId: String,
        content: String
    ) {
        require(ownerUid.isNotBlank() && meetingId.isNotBlank())

        withContext(Dispatchers.IO) {
            val rowId = database.writableDatabase.insertWithOnConflict(
                EspotiDatabase.MEETING_NOTES,
                null,
                contentValuesOf(
                    "owner_uid" to ownerUid,
                    "meeting_id" to meetingId,
                    "content" to content,
                    "updated_at" to clock()
                ),
                SQLiteDatabase.CONFLICT_REPLACE
            )

            check(rowId != -1L) { "Couldn't save meeting note" }
        }
    }

    override suspend fun delete(
        ownerUid: String,
        meetingId: String
    ) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(
                EspotiDatabase.MEETING_NOTES,
                "owner_uid = ? AND meeting_id = ?",
                arrayOf(ownerUid, meetingId)
            )
        }
    }

    override suspend fun clear(ownerUid: String) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(
                EspotiDatabase.MEETING_NOTES,
                "owner_uid = ?",
                arrayOf(ownerUid)
            )
        }
    }
}

class InMemoryMeetingNotesLocalStore(
    private val clock: () -> Long = System::currentTimeMillis
) : MeetingNotesLocalStore {

    private val notes = mutableMapOf<Pair<String, String>, MeetingNote>()

    override suspend fun get(
        ownerUid: String,
        meetingId: String
    ): MeetingNote? = notes[ownerUid to meetingId]

    override suspend fun save(
        ownerUid: String,
        meetingId: String,
        content: String
    ) {
        notes[ownerUid to meetingId] = MeetingNote(
            meetingId = meetingId,
            content = content,
            updatedAt = clock()
        )
    }

    override suspend fun delete(ownerUid: String, meetingId: String) {
        notes.remove(ownerUid to meetingId)
    }

    override suspend fun clear(ownerUid: String) {
        notes.keys.removeAll { it.first == ownerUid }
    }
}
