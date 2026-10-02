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
        val lat1 = Math.toRadians(fix.latitude)
        val lat2 = Math.toRadians(meetingLatitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(meetingLongitude - fix.longitude)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)

        val distance = 6_371_000.0 * 2 *
                asin(sqrt(a.coerceIn(0.0, 1.0)))

        // Estimación a pie:
        // 80 metros/minuto y 30% adicional por posibles desvíos.
        val walkingMinutes = ceil(distance * 1.3 / 80.0).toInt()

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