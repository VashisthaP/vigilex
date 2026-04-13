package com.vigilex.core.model

data class Trip(
    val id: String = "",
    val driverId: String = "",
    val ownerId: String = "",
    val companyId: String = "",
    val destination: Destination = Destination(),
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val status: TripStatus = TripStatus.ACTIVE,
    val lastLocation: LocationPoint? = null,
    val drowsyEventCount: Int = 0,
    val closeAttemptCount: Int = 0
)

data class Destination(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class LocationPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Float = 0f,      // m/s from FusedLocation
    val updatedAt: Long = 0L
)

enum class TripStatus {
    ACTIVE, COMPLETE, HIGH_RISK;

    fun toFirestoreValue(): String = name.lowercase()

    companion object {
        fun from(value: String) = when (value.lowercase()) {
            "complete" -> COMPLETE
            "high_risk" -> HIGH_RISK
            else -> ACTIVE
        }
    }
}
