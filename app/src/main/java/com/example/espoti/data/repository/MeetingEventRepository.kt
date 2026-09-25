package com.example.espoti.data.repository

import com.example.espoti.model.domain.Memory
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Review
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.Vote
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed meetings: meetings/{id} with sub-collections votes, reviews
 * and memories. Separate from the prototype [MeetingRepository] (sample data)
 * until the ViewModels are migrated.
 */
class MeetingEventRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val meetings = db.collection("meetings")

    suspend fun createMeeting(meeting: MeetingEvent): Result<String> = runCatching {
        val ref = meetings.document()
        ref.set(meeting.copy(id = ref.id)).await()
        ref.id
    }

    suspend fun getMeeting(id: String): Result<MeetingEvent?> = runCatching {
        meetings.document(id).get().await().toObject(MeetingEvent::class.java)?.copy(id = id)
    }

    suspend fun getMeetingsForUser(uid: String, status: StatusMeeting): Result<List<MeetingEvent>> = runCatching {
        meetings.whereArrayContains("participantIds", uid)
            .whereEqualTo("status", status.name)
            .get().await()
            .documents.mapNotNull { d -> d.toObject(MeetingEvent::class.java)?.copy(id = d.id) }
    }

    suspend fun updateStatus(id: String, status: StatusMeeting): Result<Unit> = runCatching {
        meetings.document(id).update("status", status.name).await()
        Unit
    }

    suspend fun addVote(meetingId: String, vote: Vote): Result<Unit> = runCatching {
        meetings.document(meetingId).collection("votes").document(vote.userId)
            .set(vote.copy(id = vote.userId, timeStamp = System.currentTimeMillis())).await()
        Unit
    }

    suspend fun getVotes(meetingId: String): Result<List<Vote>> = runCatching {
        meetings.document(meetingId).collection("votes").get().await().toObjects(Vote::class.java)
    }

    suspend fun addReview(meetingId: String, review: Review): Result<Unit> = runCatching {
        val ref = meetings.document(meetingId).collection("reviews").document()
        ref.set(review.copy(id = ref.id, date = System.currentTimeMillis())).await()
        Unit
    }

    suspend fun addMemory(meetingId: String, memory: Memory): Result<Unit> = runCatching {
        val ref = meetings.document(meetingId).collection("memories").document()
        ref.set(memory.copy(id = ref.id, date = System.currentTimeMillis())).await()
        Unit
    }
}
