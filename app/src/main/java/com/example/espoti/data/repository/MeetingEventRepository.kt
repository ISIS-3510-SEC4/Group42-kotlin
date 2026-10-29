package com.example.espoti.data.repository

import com.example.espoti.model.domain.Memory
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.MeetingLifecycle
import com.example.espoti.model.domain.Review
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.Vote
import com.example.espoti.model.domain.VotingRules
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.analytics.VotingTelemetry
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Firestore-backed meetings: meetings/{id} with sub-collections votes, reviews
 * and memories. Separate from the prototype [MeetingRepository] (sample data,
 * still used by Home).
 * `open` + lazy [FirebaseFirestore] so ViewModel unit tests can subclass it
 * without initializing Firebase (same pattern as UserRepository).
 */
open class MeetingEventRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val db by lazy(dbProvider)
    private val meetings by lazy { db.collection("meetings") }

    private val votingTelemetry by lazy {
        VotingTelemetry(
            emit = { event ->
                AnalyticsDependencies.tracker.persist(event)
            },
            errorCode = { error ->
                (error as? FirebaseFirestoreException)
                    ?.code
                    ?.name
                    ?: "UNKNOWN"
            }
        )
    }
    open suspend fun createMeeting(meeting: MeetingEvent): Result<String> = runCatching {
        val ref = meetings.document()
        ref.set(meeting.copy(id = ref.id)).await()
        ref.id
    }

    open suspend fun getMeeting(id: String): Result<MeetingEvent?> = runCatching {
        meetings.document(id).get().await().toObject(MeetingEvent::class.java)?.copy(id = id)
    }

    /**
     * Every meeting the user takes part in, any status. One array-contains
     * query, so no composite index is needed; the caller groups by status.
     */
    open suspend fun getMeetingsForUser(uid: String): Result<List<MeetingEvent>> = runCatching {
        meetings.whereArrayContains("participantIds", uid)
            .get().await()
            .documents.mapNotNull { d -> d.toObject(MeetingEvent::class.java)?.copy(id = d.id) }
    }

    /** Meetings the user was invited to and hasn't answered yet. */
    open suspend fun getInvitationsFor(uid: String): Result<List<MeetingEvent>> = runCatching {
        meetings.whereArrayContains("invitedIds", uid)
            .get().await()
            .documents.mapNotNull { d -> d.toObject(MeetingEvent::class.java)?.copy(id = d.id) }
    }

    /** Moves [uid] from invitedIds to participantIds in one atomic update. */
    open suspend fun acceptInvitation(meetingId: String, uid: String): Result<Unit> = runCatching {
        meetings.document(meetingId).update(
            "invitedIds", FieldValue.arrayRemove(uid),
            "participantIds", FieldValue.arrayUnion(uid)
        ).await()
        Unit
    }

    open suspend fun declineInvitation(meetingId: String, uid: String): Result<Unit> = runCatching {
        meetings.document(meetingId).update("invitedIds", FieldValue.arrayRemove(uid)).await()
        Unit
    }

    /**
     * Stores (or replaces) [uid]'s vote: votes/{uid} plus the `votes` map in the
     * meeting document, written together in one batch so they never diverge.
     */
    open suspend fun castVote(
        meetingId: String,
        uid: String,
        placeId: String
    ): Result<Unit> = votingTelemetry.record(
        meetingId = meetingId,
        uid = uid,
        placeId = placeId
    ) {
        val meetingRef = meetings.document(meetingId)

        val vote = Vote(
            id = uid,
            userId = uid,
            placeId = placeId,
            selected = true,
            timeStamp = System.currentTimeMillis()
        )

        db.batch()
            .set(
                meetingRef.collection("votes").document(uid),
                vote
            )
            .update(
                meetingRef,
                "votes.$uid",
                placeId
            )
            .commit()
            .await()

        Unit
    }

    /**
     * Closes the voting if [VotingRules] says it is done: sets the winning
     * placeId and UPCOMING (or CANCELED if nobody voted), and drops invites
     * that were never answered. Runs in a transaction that re-reads the
     * meeting, so two clients closing at once can't overwrite each other.
     * Returns the meeting as stored after the call.
     */
    open suspend fun closeVotingIfDone(
        meetingId: String,
        now: Long = System.currentTimeMillis()
    ): Result<MeetingEvent?> = runCatching {
        val ref = meetings.document(meetingId)
        db.runTransaction { transaction ->
            val meeting = transaction.get(ref).toObject(MeetingEvent::class.java)?.copy(id = meetingId)
            if (meeting == null || !VotingRules.isVotingDone(meeting, meeting.voteList(), now)) {
                return@runTransaction meeting
            }

            val winner = VotingRules.winner(meeting, meeting.voteList())
            val closed = meeting.copy(
                status = if (winner != null) StatusMeeting.UPCOMING else StatusMeeting.CANCELED,
                placeId = winner,
                invitedIds = emptyList()
            )
            transaction.update(
                ref,
                mapOf(
                    "status" to closed.status.name,
                    "placeId" to winner,
                    "invitedIds" to emptyList<String>()
                )
            )
            closed
        }.await()
    }

    open suspend fun updateStatus(id: String, status: StatusMeeting): Result<Unit> = runCatching {
        meetings.document(id).update("status", status.name).await()
        Unit
    }

    /**
     * Stores PREVIOUS for a confirmed meeting that is already over (see
     * [MeetingLifecycle]); there is no backend job, so callers run this when
     * they load meetings. Returns the meeting as it should be shown: updated
     * on success, unchanged if nothing had to change or the write failed.
     */
    suspend fun markPreviousIfFinished(
        meeting: MeetingEvent,
        now: Long = System.currentTimeMillis()
    ): MeetingEvent {
        if (meeting.status != StatusMeeting.UPCOMING || !MeetingLifecycle.isFinished(meeting, now)) {
            return meeting
        }
        return updateStatus(meeting.id, StatusMeeting.PREVIOUS)
            .map { meeting.copy(status = StatusMeeting.PREVIOUS) }
            .getOrDefault(meeting)
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
