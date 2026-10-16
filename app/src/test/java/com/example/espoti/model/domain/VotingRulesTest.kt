package com.example.espoti.model.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ============================================================================
// VOTING RULES TEST
// ----------------------------------------------------------------------------
// When a VOTING meeting is done and which place wins.
// ============================================================================
class VotingRulesTest {

    private val now = 1_000_000L

    private fun meeting(
        participants: List<String> = listOf("a", "b"),
        invited: List<String> = emptyList(),
        deadline: Long = now + 10_000L,
        votes: Map<String, String> = emptyMap()
    ) = MeetingEvent(
        id = "m",
        creatorId = "a",
        participantIds = participants,
        invitedIds = invited,
        candidatePlaceIds = listOf("p1", "p2", "p3"),
        status = StatusMeeting.VOTING,
        votingDeadline = deadline,
        votes = votes
    )

    private fun MeetingEvent.done() = VotingRules.isVotingDone(this, voteList(), now)

    // ---- isVotingDone ---------------------------------------------------

    @Test
    fun notDone_whileSomeParticipantHasNotVoted() {
        assertFalse(meeting(votes = mapOf("a" to "p1")).done())
    }

    @Test
    fun done_whenEveryParticipantVoted() {
        assertTrue(meeting(votes = mapOf("a" to "p1", "b" to "p2")).done())
    }

    @Test
    fun notDone_withPendingInvites_evenIfParticipantsVoted() {
        assertFalse(meeting(invited = listOf("c"), votes = mapOf("a" to "p1", "b" to "p1")).done())
    }

    @Test
    fun done_whenDeadlinePassed_evenWithMissingVotesAndInvites() {
        assertTrue(meeting(invited = listOf("c"), deadline = now, votes = mapOf("a" to "p1")).done())
    }

    @Test
    fun neverDone_whenNotVoting() {
        val decided = meeting(votes = mapOf("a" to "p1", "b" to "p1")).copy(status = StatusMeeting.UPCOMING)
        assertFalse(decided.done())
    }

    @Test
    fun effectiveStatus_expiredVotingCountsAsUpcoming() {
        assertEquals(StatusMeeting.UPCOMING, VotingRules.effectiveStatus(meeting(deadline = now - 1), now))
        assertEquals(StatusMeeting.VOTING, VotingRules.effectiveStatus(meeting(), now))
    }

    // ---- winner ---------------------------------------------------------

    @Test
    fun winner_isMostVoted() {
        val m = meeting(
            participants = listOf("a", "b", "c"),
            votes = mapOf("a" to "p1", "b" to "p2", "c" to "p2")
        )
        assertEquals("p2", VotingRules.winner(m, m.voteList()))
    }

    @Test
    fun tie_goesToTheCreatorsChoice() {
        val m = meeting(votes = mapOf("a" to "p2", "b" to "p1"))
        assertEquals("p2", VotingRules.winner(m, m.voteList()))
    }

    @Test
    fun tie_withoutCreatorVote_goesToCandidateOrder() {
        val m = meeting(
            participants = listOf("a", "b", "c"),
            votes = mapOf("b" to "p3", "c" to "p2")
        )
        assertEquals("p2", VotingRules.winner(m, m.voteList()))
    }

    @Test
    fun votesFromNonParticipantsOrForUnknownPlaces_areIgnored() {
        val m = meeting(votes = mapOf("x" to "p3", "y" to "p3", "b" to "nope"))
        assertNull(VotingRules.winner(m, m.voteList()))
    }
}
