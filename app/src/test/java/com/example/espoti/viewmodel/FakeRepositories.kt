package com.example.espoti.viewmodel

import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.User
import com.example.espoti.model.domain.VotingRules

// ============================================================================
// FAKE REPOSITORIES (shared by the ViewModel tests)
// ----------------------------------------------------------------------------
// In-memory stand-ins for the Firestore repositories. Their Firestore
// provider throws, so a test fails loudly if anything reaches the real SDK.
// ============================================================================
private val noFirestore = { error("Firestore must not be used in unit tests") }

/** Meetings kept in a map; voting is closed with the real VotingRules. */
class FakeMeetingEventRepository(
    initial: List<MeetingEvent> = emptyList()
) : MeetingEventRepository(dbProvider = noFirestore) {

    val meetings = initial.associateBy { it.id }.toMutableMap()
    val closeCalls = mutableListOf<String>()
    private var nextId = 1

    override suspend fun createMeeting(meeting: MeetingEvent): Result<String> {
        val id = "meeting-${nextId++}"
        meetings[id] = meeting.copy(id = id)
        return Result.success(id)
    }

    override suspend fun getMeeting(id: String) = Result.success(meetings[id])

    override suspend fun getMeetingsForUser(uid: String) =
        Result.success(meetings.values.filter { uid in it.participantIds })

    override suspend fun castVote(meetingId: String, uid: String, placeId: String): Result<Unit> {
        val meeting = meetings.getValue(meetingId)
        meetings[meetingId] = meeting.copy(votes = meeting.votes + (uid to placeId))
        return Result.success(Unit)
    }

    override suspend fun closeVotingIfDone(meetingId: String, now: Long): Result<MeetingEvent?> {
        closeCalls += meetingId
        val meeting = meetings[meetingId] ?: return Result.success(null)
        if (!VotingRules.isVotingDone(meeting, meeting.voteList(), now)) return Result.success(meeting)
        val winner = VotingRules.winner(meeting, meeting.voteList())
        val closed = meeting.copy(
            status = if (winner != null) StatusMeeting.UPCOMING else StatusMeeting.CANCELED,
            placeId = winner,
            invitedIds = emptyList()
        )
        meetings[meetingId] = closed
        return Result.success(closed)
    }

    val statusUpdates = mutableListOf<Pair<String, StatusMeeting>>()

    override suspend fun updateStatus(id: String, status: StatusMeeting): Result<Unit> {
        statusUpdates += id to status
        meetings[id]?.let { meetings[id] = it.copy(status = status) }
        return Result.success(Unit)
    }
}

/**
 * Friendships kept in memory. [friendIds] are ACCEPTED friends of [ownerUid];
 * remove / block / unblock change the list like the real repository does.
 */
class FakeFriendshipRepository(
    friendIds: List<String> = emptyList(),
    ownerUid: String = "uid-1"
) : FriendshipRepository(dbProvider = noFirestore) {

    val friendships = friendIds.mapIndexed { index, friendId ->
        Friendship(id = "f$index", userIds = listOf(ownerUid, friendId), status = FriendshipStatus.ACCEPTED)
    }.toMutableList()
    private var nextId = friendIds.size

    override suspend fun getFriendships(uid: String) =
        Result.success(friendships.filter { uid in it.userIds })

    override suspend fun removeFriend(uid: String, otherId: String): Result<Unit> {
        friendships.removeAll { uid in it.userIds && otherId in it.userIds }
        return Result.success(Unit)
    }

    override suspend fun block(uid: String, otherId: String): Result<Unit> {
        friendships.removeAll { uid in it.userIds && otherId in it.userIds }
        friendships += Friendship(
            id = "f${nextId++}",
            userIds = listOf(uid, otherId),
            status = FriendshipStatus.BLOCKED,
            blockedBy = uid
        )
        return Result.success(Unit)
    }

    override suspend fun unblock(friendshipId: String): Result<Unit> {
        friendships.removeAll { it.id == friendshipId }
        return Result.success(Unit)
    }
}

class FakeUserRepository(
    users: List<User> = emptyList()
) : UserRepository(dbProvider = noFirestore) {

    private val byId = users.associateBy { it.id }

    override suspend fun getUser(uid: String): Result<User?> =
        Result.success(byId[uid])

    override suspend fun getCachedUser(uid: String): Result<User?> =
        Result.success(byId[uid])
}

class FakePlaceRepository(
    places: List<Place> = emptyList()
) : PlaceRepository(dbProvider = noFirestore) {
    private val byId = places.associateBy { it.id }
    override suspend fun getPlace(id: String) = Result.success(byId[id])
}
