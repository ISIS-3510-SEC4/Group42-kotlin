package com.example.espoti.data.repository

import com.example.espoti.model.domain.StatusMeeting


class MeetingPreparationService(
    private val meetingRepository: MeetingEventRepository,
    private val placeRepository: PlaceRepository
) {

    suspend fun prepareRecommendations(
        meetingId: String
    ): Result<Boolean> = runCatching {

        val meeting = meetingRepository
            .getMeeting(meetingId)
            .getOrThrow()
            ?: throw IllegalStateException("Meeting not found.")

        if (meeting.status != StatusMeeting.PREPARING_RECOMMENDATIONS) {
            return@runCatching false
        }


        val center = meeting.centerLocation
            ?: throw IllegalStateException(
                "The meeting center has not been calculated."
            )


        val nearbyPlaces = placeRepository
            .getPlacesNearby(center, radiusKm = 5.0)
            .getOrThrow()

        // 5. Extract valid and unique candidate IDs.
        val candidateIds = nearbyPlaces
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_CANDIDATES)

        // 6. Validate that there are enough candidates.
        if (candidateIds.size < 2) {
            throw IllegalStateException(
                "There are not enough places within 5 km of the meeting center. " +
                        "At least two places are required to start voting."
            )
        }

        // 7. Atomically save candidates and start voting.
        meetingRepository
            .saveCandidatesAndStartVoting(meetingId, candidateIds)
            .getOrThrow()
    }

    companion object {
        private const val MAX_CANDIDATES = 10
    }
}
