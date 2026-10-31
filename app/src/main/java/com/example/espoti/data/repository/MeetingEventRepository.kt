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
import com.example.espoti.model.domain.Location
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.analytics.VotingTelemetry
import com.example.espoti.analytics.PlaceDecisionTelemetry
import com.example.espoti.model.domain.DecisionMethod
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.example.espoti.model.domain.Place

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

    // BQ "organizer decision method" (see PlaceDecisionTelemetry).
    private val placeDecisionTelemetry by lazy {
        PlaceDecisionTelemetry(
            emit = { event ->
                AnalyticsDependencies.tracker.persist(event)
            },
            closedBy = { AuthRepository().currentUserId }
        )
    }

    open suspend fun createMeeting(meeting: MeetingEvent): Result<String> = runCatching {
        val ref = meetings.document()
        ref.set(meeting.copy(id = ref.id)).await()
        ref.id
    }

    /**
     * Creates a meeting and its custom places in one atomic batch.
     *
     * Custom places are stored exclusively in:
     * meetings/{meetingId}/customPlaces/{placeId}
     *
     * They are never added to the global places collection.
     */
    open suspend fun createMeetingWithCustomPlaces(
        meeting: MeetingEvent,
        customPlaces: List<Place>
    ): Result<String> = runCatching {

        val customPlaceIds = customPlaces.map { it.id }

        require(customPlaceIds.all { it.startsWith("custom_") }) {
            "Custom place IDs must start with custom_."
        }

        require(customPlaceIds.distinct().size == customPlaceIds.size) {
            "Custom place IDs must be unique."
        }

        require(meeting.candidatePlaceIds.containsAll(customPlaceIds)) {
            "Every custom place must be included in the meeting candidates."
        }

        val meetingRef = meetings.document()
        val meetingId = meetingRef.id

        val batch = db.batch()

        // 1. Save the meeting.
        batch.set(
            meetingRef,
            meeting.copy(id = meetingId)
        )

        // 2. Save each custom place inside the meeting.
        customPlaces.forEach { place ->
            val customPlaceRef = meetingRef
                .collection("customPlaces")
                .document(place.id)

            batch.set(customPlaceRef, place)
        }

        // 3. Commit everything together.
        batch.commit().await()

        meetingId
    }


    /**
     * Adds a custom place to an existing meeting while voting is active.
     *
     * The place is saved inside the meeting's customPlaces subcollection,
     * and its ID is added to candidatePlaceIds atomically.
     */
    open suspend fun addCustomPlaceToVoting(
        meetingId: String,
        uid: String,
        place: Place
    ): Result<Unit> = runCatching {

        require(place.id.startsWith("custom_")) {
            "Custom place IDs must start with custom_."
        }

        val meetingRef = meetings.document(meetingId)
        val customPlaceRef = meetingRef
            .collection("customPlaces")
            .document(place.id)

        db.runTransaction { transaction ->

            // Read the meeting before performing any writes.
            val snapshot = transaction.get(meetingRef)

            val meeting = snapshot.toObject(MeetingEvent::class.java)
                ?: throw IllegalStateException("Meeting not found.")

            // Only participants can add candidates.
            require(uid in meeting.participantIds) {
                "Only meeting participants can add places."
            }

            // Custom places can only be added while voting is active.
            require(meeting.status == StatusMeeting.VOTING) {
                "This meeting is not accepting new candidates."
            }

            // Save the custom place inside this meeting.
            transaction.set(customPlaceRef, place)

            // Add its ID to the shared candidate list.
            transaction.update(
                meetingRef,
                "candidatePlaceIds",
                FieldValue.arrayUnion(place.id)
            )

        }.await()

        Unit
    }


    /**
     * Reads a custom place belonging to a specific meeting.
     */
    open suspend fun getCustomPlace(
        meetingId: String,
        placeId: String
    ): Result<Place?> = runCatching {

        meetings.document(meetingId)
            .collection("customPlaces")
            .document(placeId)
            .get()
            .await()
            .toObject(Place::class.java)
            ?.copy(id = placeId)
    }

    /**
     * Reads a custom place only from the local Firestore cache.
     */
    open suspend fun getCachedCustomPlace(
        meetingId: String,
        placeId: String
    ): Result<Place?> = runCatching {

        meetings.document(meetingId)
            .collection("customPlaces")
            .document(placeId)
            .get(Source.CACHE)
            .await()
            .toObject(Place::class.java)
            ?.copy(id = placeId)
    }

    open suspend fun getMeeting(id: String): Result<MeetingEvent?> = runCatching {
        meetings.document(id).get().await().toObject(MeetingEvent::class.java)?.copy(id = id)
    }

    /** Reads only the meeting already available on this device. */
    open suspend fun getCachedMeeting(
        id: String
    ): Result<MeetingEvent?> = runCatching {
        meetings.document(id)
            .get(Source.CACHE)
            .await()
            .toObject(MeetingEvent::class.java)
            ?.copy(id = id)
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

    /**
     * Returns the most recent starting location recorded by this user
     * in a previous meeting.
     */
    open suspend fun getPreviousStartingLocation(
        uid: String,
        currentMeetingId: String
    ): Result<Location?> = runCatching {
        val previousMeetings = getMeetingsForUser(uid).getOrThrow()

        previousMeetings
            .asSequence()
            .filter { it.id != currentMeetingId }
            .filter { it.date < System.currentTimeMillis() }
            .sortedByDescending { it.date }
            .mapNotNull { it.startingLocations[uid] }
            .firstOrNull()
    }

    open suspend fun getMeetingsPendingLocation(
        uid: String
    ): Result<List<MeetingEvent>> = runCatching {
        meetings
            .whereArrayContains("participantIds", uid)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(MeetingEvent::class.java)
                    ?.copy(id = document.id)
            }
            .filter { meeting ->
                meeting.status == StatusMeeting.COLLECTING_LOCATIONS &&
                        meeting.startingLocations[uid] == null
            }
    }


    /** Meetings the user was invited to and hasn't answered yet. */
    open suspend fun getInvitationsFor(uid: String): Result<List<MeetingEvent>> = runCatching {
        meetings.whereArrayContains("invitedIds", uid)
            .get().await()
            .documents.mapNotNull { d -> d.toObject(MeetingEvent::class.java)?.copy(id = d.id) }
    }

    private fun getReadyUpdates(
        invitedIds: List<String>,
        participantIds: List<String>,
        startingLocations: Map<String, Location>
    ): Map<String, Any> {
        val updates = mutableMapOf<String, Any>(
            "invitedIds" to invitedIds,
            "participantIds" to participantIds,
            "startingLocations" to startingLocations
        )

        val allParticipantsHaveLocation =
            participantIds.isNotEmpty() &&
                    participantIds.all { it in startingLocations }

        if (invitedIds.isEmpty() && allParticipantsHaveLocation) {
            val locations = participantIds.map { startingLocations.getValue(it) }

            val center = Location(
                latitude = locations.map { it.latitude }.average(),
                longitude = locations.map { it.longitude }.average()
            )

            updates["centerLocation"] = center
            updates["status"] =
                StatusMeeting.PREPARING_RECOMMENDATIONS.name
        }

        return updates
    }

    /** Moves [uid] from invitedIds to participantIds in one atomic update. */
    open suspend fun acceptInvitation(
        meetingId: String,
        uid: String
    ): Result<Unit> = runCatching {
        val meetingRef = meetings.document(meetingId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val meeting = snapshot.toObject(MeetingEvent::class.java)
                ?: throw IllegalStateException("Meeting not found.")

            require(meeting.status == StatusMeeting.COLLECTING_LOCATIONS) {
                "The meeting is not accepting invitations."
            }

            require(uid in meeting.invitedIds) {
                "This user does not have a pending invitation."
            }

            val updatedInvitedIds = meeting.invitedIds - uid
            val updatedParticipantIds = (meeting.participantIds + uid).distinct()

            val updates = getReadyUpdates(
                invitedIds = updatedInvitedIds,
                participantIds = updatedParticipantIds,
                startingLocations = meeting.startingLocations
            )

            transaction.update(meetingRef, updates)
        }.await()

        Unit
    }


    /**
     * Saves a participant's starting location.
     *
     * Returns true when all invitations have been answered
     * and every accepted participant has a location,
     * advancing the meeting to PREPARING_RECOMMENDATIONS.
     */
    open suspend fun saveStartingLocation(
        meetingId: String,
        uid: String,
        location: Location
    ): Result<Boolean> = runCatching {
        val meetingRef = meetings.document(meetingId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val meeting = snapshot.toObject(MeetingEvent::class.java)
                ?: throw IllegalStateException("Meeting not found.")

            require(uid in meeting.participantIds) {
                "This user is not an accepted participant."
            }

            require(meeting.status == StatusMeeting.COLLECTING_LOCATIONS) {
                "The meeting is not collecting locations."
            }

            val updatedLocations = meeting.startingLocations +
                    (uid to location)

            val allLocationsCollected =
                meeting.participantIds.isNotEmpty() &&
                        meeting.participantIds.all { it in updatedLocations }

            val updates = getReadyUpdates(
                invitedIds = meeting.invitedIds,
                participantIds = meeting.participantIds,
                startingLocations = updatedLocations
            )

            transaction.update(meetingRef, updates)

            meeting.invitedIds.isEmpty() && allLocationsCollected
        }.await()
    }


    /**
     * Saves the candidate places and starts voting atomically.
     * Returns true only if this call successfully transitions the meeting
     * from PREPARING_RECOMMENDATIONS to VOTING.
     */
    open suspend fun saveCandidatesAndStartVoting(
        meetingId: String,
        candidatePlaceIds: List<String>
    ): Result<Boolean> = runCatching {
        val candidates = candidatePlaceIds
            .filter { it.isNotBlank() }
            .distinct()

        require(candidates.size >= 2) {
            "At least two candidate places are required to start voting."
        }

        val meetingRef = meetings.document(meetingId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val meeting = snapshot.toObject(MeetingEvent::class.java)
                ?: throw IllegalStateException("Meeting not found.")

            // Another participant may already have started voting.
            if (meeting.status != StatusMeeting.PREPARING_RECOMMENDATIONS) {
                return@runTransaction false
            }

            transaction.update(
                meetingRef,
                mapOf(
                    "candidatePlaceIds" to candidates,
                    "status" to StatusMeeting.VOTING.name
                )
            )

            true
        }.await()
    }



    open suspend fun declineInvitation(
        meetingId: String,
        uid: String
    ): Result<Unit> = runCatching {
        val meetingRef = meetings.document(meetingId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val meeting = snapshot.toObject(MeetingEvent::class.java)
                ?: throw IllegalStateException("Meeting not found.")

            require(meeting.status == StatusMeeting.COLLECTING_LOCATIONS) {
                "The meeting is not accepting responses."
            }

            require(uid in meeting.invitedIds) {
                "This user does not have a pending invitation."
            }

            val updatedInvitedIds = meeting.invitedIds - uid

            val updates = getReadyUpdates(
                invitedIds = updatedInvitedIds,
                participantIds = meeting.participantIds,
                startingLocations = meeting.startingLocations
            )

            transaction.update(meetingRef, updates)
        }.await()

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
     * Returns the meeting as stored after the call. Only the call that
     * actually closes it records MEETING_PLACE_DECIDED (decisionMethod VOTING).
     */
    open suspend fun closeVotingIfDone(
        meetingId: String,
        now: Long = System.currentTimeMillis()
    ): Result<MeetingEvent?> = runCatching {
        val ref = meetings.document(meetingId)
        // Firestore may retry the lambda, so this is reset on every attempt.
        var closedNow: MeetingEvent? = null
        var closeReason = PlaceDecisionTelemetry.REASON_ALL_VOTED

        val result = db.runTransaction { transaction ->
            closedNow = null
            val meeting = transaction.get(ref).toObject(MeetingEvent::class.java)?.copy(id = meetingId)
            if (meeting == null || !VotingRules.isVotingDone(meeting, meeting.voteList(), now)) {
                return@runTransaction meeting
            }

            val winner = VotingRules.winner(meeting, meeting.voteList())
            val closed = meeting.copy(
                status = if (winner != null) StatusMeeting.UPCOMING else StatusMeeting.CANCELED,
                placeId = winner,
                invitedIds = emptyList(),
                decisionMethod = DecisionMethod.VOTING
            )
            transaction.update(
                ref,
                mapOf(
                    "status" to closed.status.name,
                    "placeId" to winner,
                    "invitedIds" to emptyList<String>(),
                    "decisionMethod" to DecisionMethod.VOTING.name
                )
            )
            closeReason = if (meeting.votingDeadline in 1..now) {
                PlaceDecisionTelemetry.REASON_DEADLINE
            } else {
                PlaceDecisionTelemetry.REASON_ALL_VOTED
            }
            closedNow = closed
            closed
        }.await()

        closedNow?.let { placeDecisionTelemetry.decided(it, DecisionMethod.VOTING, closeReason) }
        result
    }

    /**
     * The organizer picks the place for everybody instead of waiting for the
     * group voting: only the creator, only while VOTING (deadline not passed)
     * and only one of the candidates. Sets UPCOMING with that place, drops the
     * pending invites and records MEETING_PLACE_DECIDED (decisionMethod DIRECT).
     * Other participants' votes stay stored but no longer count.
     */
    open suspend fun chooseDirectly(
        meetingId: String,
        uid: String,
        placeId: String,
        now: Long = System.currentTimeMillis()
    ): Result<MeetingEvent> = runCatching {
        val ref = meetings.document(meetingId)

        val chosen = db.runTransaction { transaction ->
            val meeting = transaction.get(ref).toObject(MeetingEvent::class.java)?.copy(id = meetingId)
                ?: throw IllegalStateException("Meeting not found.")

            require(meeting.creatorId == uid) {
                "Only the organizer can choose the place for the group."
            }
            require(
                meeting.status == StatusMeeting.VOTING &&
                    meeting.votingDeadline !in 1..now
            ) {
                "The voting is already closed."
            }
            require(placeId in meeting.candidatePlaceIds) {
                "Choose one of the candidate places."
            }

            val closed = meeting.copy(
                status = StatusMeeting.UPCOMING,
                placeId = placeId,
                invitedIds = emptyList(),
                decisionMethod = DecisionMethod.DIRECT
            )
            transaction.update(
                ref,
                mapOf(
                    "status" to StatusMeeting.UPCOMING.name,
                    "placeId" to placeId,
                    "invitedIds" to emptyList<String>(),
                    "decisionMethod" to DecisionMethod.DIRECT.name
                )
            )
            closed
        }.await()

        placeDecisionTelemetry.decided(
            chosen,
            DecisionMethod.DIRECT,
            PlaceDecisionTelemetry.REASON_ORGANIZER_CHOICE
        )
        chosen
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
    open suspend fun getCachedMeetingsForUser(
        uid: String
    ): Result<List<MeetingEvent>> = runCatching {
        meetings.whereArrayContains("participantIds", uid)
            .get(Source.CACHE)
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(MeetingEvent::class.java)
                    ?.copy(id = document.id)
            }
    }
}
