package com.familytracker.app.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

enum class ReportingInterval(val millis: Long) {
    ONE_MINUTE(60_000),
    FIVE_MINUTES(300_000);

    companion object {
        fun fromMillis(value: Long) = entries.firstOrNull { it.millis == value } ?: ONE_MINUTE
    }
}

data class Profile(val uid: String, val name: String, val email: String)

data class Member(val uid: String, val name: String)

data class Circle(
    val id: String,
    val name: String,
    val members: List<Member> = emptyList(),
    val requests: List<Member> = emptyList(),
)

data class LocationPoint(
    val id: String,
    val uid: String,
    val circleId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val speed: Double?,
    val battery: Int?,
    val deviceTime: Long,
    val uploadedAt: Long = 0,
    val intervalMs: Long = 60_000,
) {
    fun isValid(now: Long = System.currentTimeMillis()) =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            accuracy.isFinite() &&
            accuracy in 0.0..100_000.0 &&
            (speed == null || speed.isFinite() && speed in 0.0..400.0) &&
            (battery == null || battery in 0..100) &&
            deviceTime > 0 &&
            deviceTime <= now + 60_000 &&
            uid.isNotBlank() &&
            circleId.isNotBlank()

    fun isFresh(now: Long) =
        deviceTime <= now + 60_000 && now - deviceTime <= maxOf(180_000, intervalMs * 2)
}

data class DayRange(val start: Long, val endExclusive: Long)

fun dayRange(date: LocalDate, zone: ZoneId): DayRange =
    DayRange(
        date.atStartOfDay(zone).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

fun routeDistanceMeters(points: List<LocationPoint>): Double =
    points
        .sortedBy { it.deviceTime }
        .zipWithNext()
        .sumOf { (a, b) ->
            val p1 = Math.toRadians(a.latitude)
            val p2 = Math.toRadians(b.latitude)
            val dp = p2 - p1
            val dl = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
            6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }

enum class TrackingState {
    STOPPED,
    STARTING,
    ACTIVE,
    ERROR,
}

fun mayStartTracking(
    signedIn: Boolean,
    approved: Boolean,
    permission: Boolean,
    userRequested: Boolean,
) = signedIn && approved && permission && userRequested
