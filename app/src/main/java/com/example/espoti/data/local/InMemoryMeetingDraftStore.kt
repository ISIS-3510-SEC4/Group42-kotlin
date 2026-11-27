package com.example.espoti.data.local


class InMemoryMeetingDraftStore : MeetingDraftStore {

    private val drafts = mutableMapOf<String, MeetingDraft>()

    override suspend fun save(draft: MeetingDraft) {
        synchronized(drafts) {
            drafts[draft.id] = draft
        }
    }

    override suspend fun getDrafts(
        ownerUid: String
    ): List<MeetingDraft> {
        return synchronized(drafts) {
            drafts.values
                .filter { it.ownerUid == ownerUid }
                .sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun getDraft(
        id: String,
        ownerUid: String
    ): MeetingDraft? {
        return synchronized(drafts) {
            drafts[id]?.takeIf { it.ownerUid == ownerUid }
        }
    }

    override suspend fun delete(
        id: String,
        ownerUid: String
    ) {
        synchronized(drafts) {
            val draft = drafts[id]

            if (draft?.ownerUid == ownerUid) {
                drafts.remove(id)
            }
        }
    }
}
