package com.vigilex.core.model

/**
 * Represents a driver impairment event.
 *
 * EventType covers both drowsiness and intoxication-related signals.
 * Subtype indicates the specific signal that fired so owners can see
 * whether the alert was triggered by eye closure (classic drowsiness),
 * head movement, erratic lateral motion (drunk-driving indicator), or
 * a combination of multiple signals at once.
 */
data class ImpairmentEvent(
    val id: String = "",
    val type: EventType = EventType.IMPAIRMENT,
    val subtype: ImpairmentSubtype = ImpairmentSubtype.COMBINED,
    val driverId: String = "",
    val tripId: String = "",
    val companyId: String = "",
    val timestamp: Long = 0L,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val severity: Severity = Severity.MEDIUM,
    val synced: Boolean = false
)

enum class EventType {
    IMPAIRMENT,          // drowsy or intoxication signal
    CLOSE_ATTEMPT,       // driver tried to exit without OTP
    TRIP_COMPLETE,
    OTP_REQUEST;

    fun toFirestoreValue(): String = name.lowercase()
}

/**
 * Why this alert fired — lets the owner understand the signal.
 *
 * EYE_CLOSURE    → both eyes < 0.25 for ≥ 2s (classic microsleep / drunk eye droop)
 * HEAD_DROP      → eulerZ/Y deviation (nodding / head loll)
 * ERRATIC_MOTION → accelerometer lateral spike > 4 m/s² for ≥ 2s (drunk swerving pattern)
 * COMBINED       → multiple signals simultaneously (highest confidence, highest severity)
 */
enum class ImpairmentSubtype {
    EYE_CLOSURE,
    HEAD_DROP,
    ERRATIC_MOTION,
    COMBINED;

    fun toFirestoreValue(): String = name.lowercase()
}

enum class Severity {
    LOW, MEDIUM, HIGH;

    fun toFirestoreValue(): String = name.lowercase()

    companion object {
        fun from(value: String) = when (value.uppercase()) {
            "LOW" -> LOW
            "HIGH" -> HIGH
            else -> MEDIUM
        }
    }
}
