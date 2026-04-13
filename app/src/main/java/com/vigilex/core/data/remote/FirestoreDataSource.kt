package com.vigilex.core.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vigilex.core.model.Company
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.LocationPoint
import com.vigilex.core.model.Role
import com.vigilex.core.model.Trip
import com.vigilex.core.model.TripStatus
import com.vigilex.core.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val db: FirebaseFirestore
) {

    // ── Users ──────────────────────────────────────────────────────────────

    suspend fun getUser(uid: String): User? {
        val doc = db.collection("users").document(uid).get().await()
        return if (doc.exists()) doc.toUser() else null
    }

    /**
     * Look up a user by phone number — used after phone OTP auth to find
     * existing Firestore docs that were created with a different UID
     * (e.g., manually added by admin, or migrating from email/password).
     * Handles both formats: "9897831882" and "+919897831882"
     */
    suspend fun getUserByPhone(fullPhone: String): User? {
        // Strip country prefix so we can match Firestore docs stored without it
        val stripped = fullPhone
            .removePrefix("+91")
            .removePrefix("+1")
            .trimStart('0')

        // Try exact match first (includes +91 format)
        db.collection("users")
            .whereEqualTo("phone", fullPhone)
            .limit(1).get().await()
            .documents.firstOrNull()?.toUser()
            ?.let { return it }

        // Then try stripped (10-digit) format
        return db.collection("users")
            .whereEqualTo("phone", stripped)
            .limit(1).get().await()
            .documents.firstOrNull()?.toUser()
    }

    suspend fun createUser(user: User) {
        db.collection("users").document(user.uid).set(user.toMap()).await()
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    fun observeDriversForOwner(ownerId: String, companyId: String): Flow<List<User>> =
        callbackFlow {
            val reg = db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("role", Role.DRIVER.toFirestoreValue())
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { it.toUser() } ?: emptyList())
                }
            awaitClose { reg.remove() }
        }

    fun observeAllUsers(): Flow<List<User>> = callbackFlow {
        val reg = db.collection("users").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toUser() } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ── Companies ──────────────────────────────────────────────────────────

    suspend fun createCompany(company: Company) {
        db.collection("companies").document(company.id).set(company.toMap()).await()
    }

    fun observeAllCompanies(): Flow<List<Company>> = callbackFlow {
        val reg = db.collection("companies").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toCompany() } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ── Trips ──────────────────────────────────────────────────────────────

    suspend fun createTrip(trip: Trip): String {
        val id = UUID.randomUUID().toString()
        db.collection("trips").document(id).set(trip.copy(id = id).toMap()).await()
        return id
    }

    suspend fun updateTripLocation(tripId: String, location: LocationPoint) {
        db.collection("trips").document(tripId).update(
            mapOf(
                "lastLocation" to mapOf(
                    "lat" to location.lat,
                    "lng" to location.lng,
                    "speed" to location.speed,
                    "updatedAt" to location.updatedAt
                )
            )
        ).await()
    }

    suspend fun updateTripStatus(tripId: String, status: TripStatus) {
        db.collection("trips").document(tripId)
            .update("status", status.toFirestoreValue()).await()
    }

    suspend fun incrementTripCounter(tripId: String, field: String) {
        // field = "drowsyEventCount" or "closeAttemptCount"
        val ref = db.collection("trips").document(tripId)
        db.runTransaction { tx ->
            val current = tx.get(ref).getLong(field) ?: 0
            tx.update(ref, field, current + 1)
        }.await()
    }

    fun observeActiveTrip(driverId: String): Flow<Trip?> = callbackFlow {
        val reg = db.collection("trips")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", TripStatus.ACTIVE.toFirestoreValue())
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val trip = snap?.documents?.firstOrNull()?.toTrip()
                trySend(trip)
            }
        awaitClose { reg.remove() }
    }

    fun observeTripsForOwner(ownerId: String): Flow<List<Trip>> = callbackFlow {
        val reg = db.collection("trips")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toTrip() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun observeAllTrips(): Flow<List<Trip>> = callbackFlow {
        val reg = db.collection("trips").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toTrip() } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ── Events ─────────────────────────────────────────────────────────────

    suspend fun writeEvent(event: ImpairmentEvent): String {
        val id = UUID.randomUUID().toString()
        db.collection("events").document(id).set(event.copy(id = id).toMap()).await()
        return id
    }

    fun observeEventsForTrip(tripId: String): Flow<List<ImpairmentEvent>> = callbackFlow {
        val reg = db.collection("events")
            .whereEqualTo("tripId", tripId)
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toEvent() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── OTP ────────────────────────────────────────────────────────────────

    suspend fun writeOtp(tripId: String, code: String, expiresAt: Long) {
        db.collection("otps").document(tripId).set(
            mapOf(
                "code" to code,
                "generatedAt" to System.currentTimeMillis(),
                "expiresAt" to expiresAt,
                "used" to false
            )
        ).await()
    }

    suspend fun validateOtp(tripId: String, code: String): Boolean {
        val doc = db.collection("otps").document(tripId).get().await()
        if (!doc.exists()) return false
        val storedCode = doc.getString("code") ?: return false
        val expiresAt = doc.getLong("expiresAt") ?: 0L
        val used = doc.getBoolean("used") ?: true
        val valid = storedCode == code && System.currentTimeMillis() < expiresAt && !used
        if (valid) {
            db.collection("otps").document(tripId).update("used", true).await()
        }
        return valid
    }

    // ── Super Admin seed ───────────────────────────────────────────────────

    suspend fun isSuperAdminSeeded(): Boolean {
        val doc = db.collection("superadmin").document("config").get().await()
        return doc.exists()
    }

    suspend fun seedSuperAdmin(email: String, phone: String, uid: String) {
        db.collection("superadmin").document("config").set(
            mapOf("email" to email, "phone" to phone, "uid" to uid),
            SetOptions.merge()
        ).await()
    }
}

// ── Firestore ↔ model mappers ──────────────────────────────────────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? = runCatching {
    User(
        uid = id,
        name = getString("name") ?: "",
        email = getString("email") ?: "",
        phone = getString("phone") ?: "",
        role = Role.from(getString("role") ?: "driver"),
        companyId = getString("companyId") ?: "",
        fcmToken = getString("fcmToken") ?: ""
    )
}.getOrNull()

private fun User.toMap() = mapOf(
    "name" to name,
    "email" to email,
    "phone" to phone,
    "role" to role.toFirestoreValue(),
    "companyId" to companyId,
    "fcmToken" to fcmToken,
    "createdAt" to System.currentTimeMillis()
)

private fun com.google.firebase.firestore.DocumentSnapshot.toCompany(): Company? = runCatching {
    Company(
        id = id,
        companyName = getString("companyName") ?: "",
        ownerUid = getString("ownerUid") ?: "",
        createdAt = getLong("createdAt") ?: 0L,
        driverCount = getLong("driverCount")?.toInt() ?: 0
    )
}.getOrNull()

private fun Company.toMap() = mapOf(
    "companyName" to companyName,
    "ownerUid" to ownerUid,
    "createdAt" to createdAt,
    "driverCount" to driverCount
)

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.toTrip(): Trip? = runCatching {
    val loc = get("lastLocation") as? Map<String, Any>
    Trip(
        id = id,
        driverId = getString("driverId") ?: "",
        ownerId = getString("ownerId") ?: "",
        companyId = getString("companyId") ?: "",
        destination = (get("destination") as? Map<String, Any>)?.let {
            com.vigilex.core.model.Destination(
                name = it["name"] as? String ?: "",
                lat = (it["lat"] as? Double) ?: 0.0,
                lng = (it["lng"] as? Double) ?: 0.0
            )
        } ?: com.vigilex.core.model.Destination(),
        startTime = getLong("startTime") ?: 0L,
        endTime = getLong("endTime") ?: 0L,
        status = TripStatus.from(getString("status") ?: "active"),
        lastLocation = loc?.let {
            LocationPoint(
                lat = (it["lat"] as? Double) ?: 0.0,
                lng = (it["lng"] as? Double) ?: 0.0,
                speed = ((it["speed"] as? Double) ?: 0.0).toFloat(),
                updatedAt = (it["updatedAt"] as? Long) ?: 0L
            )
        },
        drowsyEventCount = getLong("drowsyEventCount")?.toInt() ?: 0,
        closeAttemptCount = getLong("closeAttemptCount")?.toInt() ?: 0
    )
}.getOrNull()

private fun Trip.toMap() = mapOf(
    "driverId" to driverId,
    "ownerId" to ownerId,
    "companyId" to companyId,
    "destination" to mapOf("name" to destination.name, "lat" to destination.lat, "lng" to destination.lng),
    "startTime" to startTime,
    "endTime" to endTime,
    "status" to status.toFirestoreValue(),
    "drowsyEventCount" to drowsyEventCount,
    "closeAttemptCount" to closeAttemptCount
)

private fun com.google.firebase.firestore.DocumentSnapshot.toEvent(): ImpairmentEvent? = runCatching {
    ImpairmentEvent(
        id = id,
        type = com.vigilex.core.model.EventType.values()
            .firstOrNull { it.toFirestoreValue() == getString("type") }
            ?: com.vigilex.core.model.EventType.IMPAIRMENT,
        subtype = com.vigilex.core.model.ImpairmentSubtype.values()
            .firstOrNull { it.toFirestoreValue() == getString("subtype") }
            ?: com.vigilex.core.model.ImpairmentSubtype.COMBINED,
        driverId = getString("driverId") ?: "",
        tripId = getString("tripId") ?: "",
        companyId = getString("companyId") ?: "",
        timestamp = getLong("timestamp") ?: 0L,
        lat = getDouble("lat") ?: 0.0,
        lng = getDouble("lng") ?: 0.0,
        severity = com.vigilex.core.model.Severity.from(getString("severity") ?: "medium"),
        synced = true
    )
}.getOrNull()

private fun ImpairmentEvent.toMap() = mapOf(
    "type" to type.toFirestoreValue(),
    "subtype" to subtype.toFirestoreValue(),
    "driverId" to driverId,
    "tripId" to tripId,
    "companyId" to companyId,
    "timestamp" to timestamp,
    "lat" to lat,
    "lng" to lng,
    "severity" to severity.toFirestoreValue(),
    "synced" to true
)
