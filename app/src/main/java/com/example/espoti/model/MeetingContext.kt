package com.example.espoti.model

import kotlin.math.*

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double
)

data class MeetingContext(
    val distanceMeters: Double,
    val walkingMinutes: Int,
    val message: String,
    val near: Boolean,
    val checkedIn: Boolean
)

object MeetingContextCalculator {

    const val ARRIVAL_RADIUS_METERS = 100.0

    fun calculate(
        meeting: Meeting,
        fix: LocationFix,
        now: Long,
        checkedIn: Boolean = false
    ): MeetingContext {
        require(fix.accuracyMeters.isFinite() && fix.accuracyMeters >= 0)
        require(fix.latitude in -90.0..90.0)
        require(fix.longitude in -180.0..180.0)

        val meetingLatitude = requireNotNull(meeting.latitude)
        val meetingLongitude = requireNotNull(meeting.longitude)
        val meetingStart = requireNotNull(meeting.startsAtEpochMillis)

        require(meetingLatitude in -90.0..90.0)
        require(meetingLongitude in -180.0..180.0)

        // Haversine: distancia en línea recta, en metros.
        val distance = TravelEstimates.distanceMeters(
            latitude1 = fix.latitude,
            longitude1 = fix.longitude,
            latitude2 = meetingLatitude,
            longitude2 = meetingLongitude
        )

        val walkingMinutes = TravelEstimates.walkingMinutes(distance)

        // Se exige buena precisión y que incluso el margen de error
        // quede dentro del radio de llegada.
        val near = fix.accuracyMeters <= 50.0 &&
                distance + fix.accuracyMeters <= ARRIVAL_RADIUS_METERS

        val remainingMinutes =
            floor((meetingStart - now) / 60_000.0).toLong()

        // Llegar cinco minutos antes.
        val leaveIn = remainingMinutes - walkingMinutes - 5

        val message = when {
            checkedIn -> "Arrived (checked in)"
            near -> "You are near the meeting point"
            remainingMinutes <= 0 -> "Meeting has started"
            leaveIn <= 0 -> "Leave now"
            else -> "You should leave in $leaveIn min"
        }

        return MeetingContext(
            distanceMeters = distance,
            walkingMinutes = walkingMinutes,
            message = message,
            near = near,
            checkedIn = checkedIn
        )
    }
}