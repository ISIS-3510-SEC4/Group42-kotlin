package com.example.espoti.model.domain

// ============================================================================
// VOTING RULES
// ----------------------------------------------------------------------------
// Pure decision logic for a meeting in VOTING, shared by the repository (which
// writes the result inside a transaction) and the UI (which must never show a
// meeting as "Voting" once its deadline passed, even if nobody closed it yet).
// - Voting is done when there are no pending invites and every participant
//   voted, or when the deadline passed.
// - Winner: most voted candidate; ties go to the creator's choice, then to
//   the order of candidatePlaceIds.
// ============================================================================
object VotingRules {

    fun isVotingDone(meeting: MeetingEvent, votes: List<Vote>, now: Long): Boolean {
        if (meeting.status != StatusMeeting.VOTING) return false
        if (meeting.votingDeadline in 1..now) return true
        val voters = votes.map { it.userId }.toSet()
        return meeting.invitedIds.isEmpty() && meeting.participantIds.all { it in voters }
    }

    /** Effective status to show: an expired VOTING meeting counts as decided. */
    fun effectiveStatus(meeting: MeetingEvent, now: Long): StatusMeeting =
        if (meeting.status == StatusMeeting.VOTING &&
            meeting.votingDeadline in 1..now
        ) StatusMeeting.UPCOMING else meeting.status

    /** Winning placeId, or null when nobody voted a valid candidate. */
    fun winner(meeting: MeetingEvent, votes: List<Vote>): String? {
        // Only votes of current participants for real candidates count.
        val valid = votes.filter {
            it.userId in meeting.participantIds && it.placeId in meeting.candidatePlaceIds
        }
        if (valid.isEmpty()) return null

        val counts = valid.groupingBy { it.placeId }.eachCount()
        val best = counts.values.max()
        val tied = counts.filterValues { it == best }.keys

        val creatorChoice = valid.firstOrNull { it.userId == meeting.creatorId }?.placeId
        if (creatorChoice != null && creatorChoice in tied) return creatorChoice
        return meeting.candidatePlaceIds.first { it in tied }
    }
}
