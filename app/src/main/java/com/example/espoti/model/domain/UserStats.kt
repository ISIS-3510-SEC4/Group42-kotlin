package com.example.espoti.model.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================================
// USER STATS (BQ type 2, answered to the user inside the app)
// ----------------------------------------------------------------------------
// "Which friends and places does the user meet with / visit most often, and
//  how often does their vote decide the final place?"
// UserStatsCalculator turns the user's meetings into raw counts (ids only);
// StatsViewModel then resolves names and stores the finished UserStats on
// the device, so the screen can be shown offline exactly as it was computed.
// ============================================================================

/** One row of a ranking ("juan · 4 meetups"). */
data class RankedItem(val id: String, val label: String, val count: Int)

/** Meetups in one calendar month ("Nov" · 3). [key] is "2026-11". */
data class MonthCount(val key: String, val label: String, val count: Int)

/** A place from analytics_results/popular_meeting_areas (the team-wide BQ). */
data class TrendingPlace(val name: String, val cityName: String, val selections: Int)

/** Everything the "My stats" screen shows, with names already resolved. */
data class UserStats(
    val attended: Int = 0,
    val upcoming: Int = 0,
    val canceled: Int = 0,
    val votesCast: Int = 0,
    /** Decided meetings where the user voted... */
    val decidedWithVote: Int = 0,
    /** ...and, of those, the ones where the final place was the user's vote. */
    val wonVotes: Int = 0,
    val topFriends: List<RankedItem> = emptyList(),
    val topPlaces: List<RankedItem> = emptyList(),
    val favoriteActivity: ActivityType? = null,
    val perMonth: List<MonthCount> = emptyList(),
    val trendingPlaces: List<TrendingPlace> = emptyList(),
    val computedAt: Long = 0L
) {
    /** 0..100, or null until the user voted in a decided meeting. */
    val voteWinPercent: Int?
        get() = if (decidedWithVote == 0) null else wonVotes * 100 / decidedWithVote

    val isEmpty: Boolean
        get() = attended == 0 && upcoming == 0 && canceled == 0 && votesCast == 0
}

/** Raw result of [UserStatsCalculator]: ids and counts, no names yet. */
data class UserStatsCounts(
    val attended: Int,
    val upcoming: Int,
    val canceled: Int,
    val votesCast: Int,
    val decidedWithVote: Int,
    val wonVotes: Int,
    /** friend uid -> shared finished meetups, best first (max [UserStatsCalculator.TOP_N]). */
    val topFriends: List<Pair<String, Int>>,
    /** place id -> finished meetups there, best first. */
    val topPlaces: List<Pair<String, Int>>,
    /** place id -> one meeting that used it (needed to read custom places). */
    val placeMeeting: Map<String, String>,
    val favoriteActivity: ActivityType?,
    val perMonth: List<MonthCount>
)

object UserStatsCalculator {

    const val TOP_N = 3
    const val MONTHS = 6

    private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

    /**
     * One pass over [meetings] (O(n) with hash maps): every meeting's status is
     * computed once with [MeetingLifecycle.effectiveStatus], so a meeting that
     * is over counts as attended even if nobody has written PREVIOUS yet.
     */
    fun compute(
        uid: String,
        meetings: List<MeetingEvent>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): UserStatsCounts {
        var attended = 0
        var upcoming = 0
        var canceled = 0
        var votesCast = 0
        var decidedWithVote = 0
        var wonVotes = 0
        val friends = HashMap<String, Int>()
        val places = HashMap<String, Int>()
        val placeMeeting = HashMap<String, String>()
        val activities = IntArray(ActivityType.entries.size)

        val currentMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val firstMonth = currentMonth.minusMonths((MONTHS - 1).toLong())
        val perMonth = IntArray(MONTHS)

        for (meeting in meetings) {
            if (uid !in meeting.participantIds) continue
            val status = MeetingLifecycle.effectiveStatus(meeting, now, zone)
            val myVote = meeting.votes[uid]
            if (myVote != null) votesCast++

            when (status) {
                // Not happened yet (still being prepared, voting or confirmed).
                StatusMeeting.COLLECTING_LOCATIONS,
                StatusMeeting.PREPARING_RECOMMENDATIONS,
                StatusMeeting.VOTING,
                StatusMeeting.UPCOMING -> upcoming++
                StatusMeeting.CANCELED -> canceled++
                StatusMeeting.PREVIOUS -> {
                    attended++
                    for (other in meeting.participantIds) {
                        if (other != uid) friends.merge(other, 1, Int::plus)
                    }
                    meeting.placeId?.let { placeId ->
                        places.merge(placeId, 1, Int::plus)
                        placeMeeting.putIfAbsent(placeId, meeting.id)
                    }
                    activities[meeting.activity.ordinal]++
                    if (meeting.date > 0) {
                        // MeetingEvent.date is the picked day at UTC midnight.
                        val month = YearMonth.from(
                            Instant.ofEpochMilli(meeting.date).atZone(ZoneOffset.UTC)
                        )
                        if (!month.isBefore(firstMonth) && !month.isAfter(currentMonth)) {
                            val index = (month.year - firstMonth.year) * 12 +
                                (month.monthValue - firstMonth.monthValue)
                            perMonth[index]++
                        }
                    }
                }
            }

            // Vote influence: only meetings whose place is already decided.
            val decided = status == StatusMeeting.UPCOMING || status == StatusMeeting.PREVIOUS
            val finalPlace = meeting.placeId
            if (decided && finalPlace != null && myVote != null) {
                decidedWithVote++
                if (myVote == finalPlace) wonVotes++
            }
        }

        val favorite = activities.indices
            .filter { activities[it] > 0 }
            .maxByOrNull { activities[it] } // first (enum order) wins a tie
            ?.let { ActivityType.entries[it] }

        return UserStatsCounts(
            attended = attended,
            upcoming = upcoming,
            canceled = canceled,
            votesCast = votesCast,
            decidedWithVote = decidedWithVote,
            wonVotes = wonVotes,
            topFriends = top(friends),
            topPlaces = top(places),
            placeMeeting = placeMeeting,
            favoriteActivity = favorite,
            perMonth = List(MONTHS) { i ->
                val month = firstMonth.plusMonths(i.toLong())
                MonthCount(key = month.toString(), label = month.format(MONTH_LABEL), count = perMonth[i])
            }
        )
    }

    /** Highest counts first; ties by id so the order is stable. */
    private fun top(counts: Map<String, Int>): List<Pair<String, Int>> =
        counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_N)
            .map { it.key to it.value }
}
