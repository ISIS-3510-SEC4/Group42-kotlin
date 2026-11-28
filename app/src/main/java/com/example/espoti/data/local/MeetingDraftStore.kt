package com.example.espoti.data.local


interface MeetingDraftStore {

    suspend fun save(draft: MeetingDraft)

    suspend fun getDrafts(ownerUid: String): List<MeetingDraft>

    suspend fun getDraft(
        id: String,
        ownerUid: String
    ): MeetingDraft?

    suspend fun delete(
        id: String,
        ownerUid: String
    )
}
