package com.example.espoti.data.local

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SqliteMeetingDraftStore(
    private val database: EspotiDatabase
) : MeetingDraftStore {

    override suspend fun save(draft: MeetingDraft) {
        withContext(Dispatchers.IO) {
            val payload = encodeDraft(draft)

            val insertedId = database.writableDatabase.insertWithOnConflict(
                EspotiDatabase.MEETING_DRAFTS,
                null,
                ContentValues().apply {
                    put("id", draft.id)
                    put("owner_uid", draft.ownerUid)
                    put("payload", payload)
                    put("created_at", draft.createdAt)
                    put("updated_at", draft.updatedAt)
                },
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )

            check(insertedId != -1L) {
                "Couldn't save the meeting draft."
            }
        }
    }

    override suspend fun getDrafts(
        ownerUid: String
    ): List<MeetingDraft> = withContext(Dispatchers.IO) {

        database.readableDatabase.query(
            EspotiDatabase.MEETING_DRAFTS,
            null,
            "owner_uid = ?",
            arrayOf(ownerUid),
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->

            buildList {
                while (cursor.moveToNext()) {
                    add(
                        decodeDraft(
                            id = cursor.getString(
                                cursor.getColumnIndexOrThrow("id")
                            ),
                            ownerUid = cursor.getString(
                                cursor.getColumnIndexOrThrow("owner_uid")
                            ),
                            payload = cursor.getString(
                                cursor.getColumnIndexOrThrow("payload")
                            ),
                            createdAt = cursor.getLong(
                                cursor.getColumnIndexOrThrow("created_at")
                            ),
                            updatedAt = cursor.getLong(
                                cursor.getColumnIndexOrThrow("updated_at")
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun getDraft(
        id: String,
        ownerUid: String
    ): MeetingDraft? = withContext(Dispatchers.IO) {

        database.readableDatabase.query(
            EspotiDatabase.MEETING_DRAFTS,
            null,
            "id = ? AND owner_uid = ?",
            arrayOf(id, ownerUid),
            null,
            null,
            null,
            "1"
        ).use { cursor ->

            if (!cursor.moveToFirst()) {
                null
            } else {
                decodeDraft(
                    id = cursor.getString(
                        cursor.getColumnIndexOrThrow("id")
                    ),
                    ownerUid = cursor.getString(
                        cursor.getColumnIndexOrThrow("owner_uid")
                    ),
                    payload = cursor.getString(
                        cursor.getColumnIndexOrThrow("payload")
                    ),
                    createdAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow("created_at")
                    ),
                    updatedAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow("updated_at")
                    )
                )
            }
        }
    }

    override suspend fun delete(
        id: String,
        ownerUid: String
    ) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(
                EspotiDatabase.MEETING_DRAFTS,
                "id = ? AND owner_uid = ?",
                arrayOf(id, ownerUid)
            )
        }
    }

    //Aca va JSOn serialization

    private fun encodeDraft(draft: MeetingDraft): String {
        return JSONObject().apply {
            put("whatToDo", draft.whatToDo)
            put("date", draft.date ?: JSONObject.NULL)
            put("time", draft.time ?: JSONObject.NULL)
            put("latitude", draft.latitude ?: JSONObject.NULL)
            put("longitude", draft.longitude ?: JSONObject.NULL)

            put(
                "invitedFriendIds",
                JSONArray(draft.invitedFriendIds.toList())
            )

            put(
                "votingDeadlineMillis",
                draft.votingDeadlineMillis ?: JSONObject.NULL
            )
        }.toString()
    }

    private fun decodeDraft(
        id: String,
        ownerUid: String,
        payload: String,
        createdAt: Long,
        updatedAt: Long
    ): MeetingDraft {

        val json = JSONObject(payload)

        val friendIdsJson = json.optJSONArray("invitedFriendIds")
            ?: JSONArray()

        val friendIds = buildSet {
            for (index in 0 until friendIdsJson.length()) {
                add(friendIdsJson.getString(index))
            }
        }

        return MeetingDraft(
            id = id,
            ownerUid = ownerUid,

            whatToDo = json.optString("whatToDo", ""),

            date = json.optNullableString("date"),
            time = json.optNullableString("time"),

            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),

            invitedFriendIds = friendIds,

            votingDeadlineMillis =
                json.optNullableLong("votingDeadlineMillis"),

            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun JSONObject.optNullableString(
        key: String
    ): String? {
        return if (isNull(key)) null else optString(key)
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {
        return if (isNull(key) || !has(key)) {
            null
        } else {
            optDouble(key).takeIf { it.isFinite() }
        }
    }

    private fun JSONObject.optNullableLong(
        key: String
    ): Long? {
        return if (isNull(key) || !has(key)) {
            null
        } else {
            optLong(key)
        }
    }
}
