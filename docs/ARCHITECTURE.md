# VigileX - Architecture Overview

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Navigation | Jetpack Navigation Compose |
| Auth | Firebase Phone Auth (OTP) with phone authorization gate |
| Database | Cloud Firestore (real-time listeners) |
| Push | Firebase Cloud Messaging |
| Camera | CameraX (front camera with live preview + headless analysis) |
| ML | ML Kit Face Detection (on-device) |
| Maps | Google Maps Compose + Google Places SDK |
| Location | Fused Location Provider |
| Local DB | Room (offline event queue) |
| Background | Foreground Service (camera + location types) + WakeLock |

---

## Project Structure

```
app/src/main/java/com/vigilex/
|
|-- core/                          # Shared across all features
|   |-- data/
|   |   |-- local/                 # Room DB, PendingEventDao
|   |   |-- remote/                # FirestoreDataSource (all Firestore operations)
|   |-- model/                     # Data classes: User, Trip, Company, ImpairmentEvent, etc.
|   |-- di/                        # Hilt modules (AppModule, FirebaseModule)
|
|-- feature/
|   |-- auth/                      # Login flow
|   |   |-- LoginScreen.kt         # Phone + OTP input (BasicTextField for Samsung compat)
|   |   |-- AuthViewModel.kt       # Phone authorization gate, Firebase phone auth, role resolution
|   |
|   |-- driver/                    # Driver monitoring
|   |   |-- DriverHomeScreen.kt    # 3-step setup: Permissions -> Bluetooth -> Live Camera Preview
|   |   |-- DriverViewModel.kt     # Service lifecycle, PIN validation, monitoring state
|   |   |-- service/
|   |       |-- MonitoringForegroundService.kt  # Camera + location pipeline + WakeLock
|   |       |-- DrowsinessAnalyzer.kt           # ML Kit face analysis + continuous alerting
|   |       |-- AlertOrchestrator.kt            # Looping alarm, BT SCO -> speaker fallback
|   |       |-- GeofenceReceiver.kt             # Destination arrival detection
|   |       |-- BootReceiver.kt                 # Restart service after reboot
|   |
|   |-- owner/                     # Fleet owner dashboard
|   |   |-- dashboard/             # Owner home screen with stats
|   |   |-- drivers/               # Driver management (add/delete/assign trips)
|   |   |-- trips/                 # Trip history + trip detail with event timeline
|   |   |-- driverdetail/          # Live map + events for a specific driver
|   |   |-- settings/              # Owner settings + sign out
|   |
|   |-- superadmin/                # Super admin panel
|       |-- SuperAdminDashboardScreen.kt  # View authorized owners, stats
|       |-- AddCompanyScreen.kt           # Authorize new owner (name, email, phone)
|       |-- CompanyDetailScreen.kt        # Owner detail view
|       |-- SuperAdminViewModel.kt        # Owner authorization CRUD
|
|-- navigation/
|   |-- VigileXNavGraph.kt         # Central nav graph, role-based routing
|   |-- Routes.kt                  # Route constants
|
|-- ui/
|   |-- theme/                     # Colors (NavyDark, Amber), typography
|   |-- components/                # Shared composables (StatusBadge, PlacesAutocompleteField)
|
|-- MainActivity.kt                # Single activity host
|-- VigileXApplication.kt          # Hilt app, notification channels, Places SDK init
```

---

## Data Flow

### Driver Monitoring Pipeline

```
CameraX (front camera, live preview + ImageAnalysis)
    |
    v
DrowsinessAnalyzer (ImageAnalysis.Analyzer)
    |-- ML Kit FaceDetector -> eye openness, head euler angles
    |-- Accelerometer sensor -> lateral motion detection
    |
    |-- [First 15s] Calibration phase: collect baseline eye openness
    |-- [After calibration] Active detection (no speed gate — always on):
    |       |-- Eye closure > 2s -> IMPAIRMENT alert (re-triggers every 5s)
    |       |-- Head drop (euler Z > 25° or Y > 30°) -> IMPAIRMENT alert
    |       |-- Erratic lateral motion (> 8 m/s², 3s sustained) -> IMPAIRMENT alert
    |       |-- Two different signals within 10s -> COMBINED (highest severity)
    |
    v
AlertOrchestrator
    |-- Forces audio volume to MAX (STREAM_ALARM)
    |-- Routes audio to Bluetooth SCO if connected, else phone speaker
    |-- Plays a LOOPING alarm via MediaPlayer (isLooping = true)
    |-- isAlertActive guard — a second detection cannot stack a second player
    |-- NO vibration (removed — testers found it more distracting than useful)
    |
    |-- Stops on either:
    |       |-- Eyes open >= 2s  -> analyzer emits Recovered -> service stops it
    |       |-- "Stop Alarm" tap -> service.manualStopAlarm()
    |
    v
MonitoringForegroundService
    |-- Writes ImpairmentEvent to Firestore (or Room if offline)
    |-- Increments trip counter (drowsyEventCount)
    |-- Escalation: 3+ events in 30 min -> HIGH_RISK status
    |-- Updates trip lastLocation every 30s via FusedLocationProvider
    |-- Holds PARTIAL_WAKE_LOCK for screen-off operation
    |-- Exposes Preview use case to UI via companion StateFlow
```

### Authentication Flow

```
Phone number input
    |
    v
Authorization Gate
    |-- Check if phone is Super Admin (BuildConfig) -> always allowed
    |-- Else: get authorized_phones/{phone without '+'}
    |       |-- Exists      -> send OTP
    |       |-- Not found   -> "This phone number is not authorized"
    |       |-- Lookup ERRORED -> send OTP anyway (fail open)
    |
    |   Why authorized_phones and not a users query?
    |   This runs BEFORE sign-in, so request.auth is null and /users is
    |   unreadable (it holds exitPin). authorized_phones is a zero-PII
    |   mirror: public `get`, `list` denied so it can't be enumerated.
    |
    |   Why fail open on error? The gate only saves SMS cost. The real
    |   boundary is below — a user with no Firestore doc is signed back
    |   out after OTP verification regardless.
    |
    v
Firebase Phone Auth (sends SMS OTP)
    |
    v
OTP verification
    |
    v
Resolve role:
    1. Lookup user by Firebase UID
    2. If not found: lookup by phone number (handles pending_ -> real UID migration)
    3. If found: copy exitPin and all fields to new UID doc
    4. Route to role-specific home screen
    |
    |-- DRIVER      -> DriverHomeScreen (permissions -> bluetooth -> live camera)
    |-- OWNER       -> OwnerDashboardScreen (stats, drivers, trips)
    |-- SUPER_ADMIN -> SuperAdminDashboardScreen (authorized owners list)
```

### Firestore Collections

```
superadmin/config
    - email, phone, uid          (seeded on first Super Admin login)

authorized_phones/{phoneNoPlus}  (pre-auth OTP gate — NO PII)
    - active, createdAt
    - doc ID is the E.164 number with '+' stripped: +919897831882 -> 919897831882
    - rules: allow get: if true / allow list: if false

users/{uid}
    - name, phone, email, role, companyId, exitPin, fcmToken
    - exitPin gates driver sign-out, so this collection is never public

companies/{id}
    - companyName, ownerUid, driverCount, createdAt

trips/{id}
    - driverId, ownerId, companyId, destination{name, lat, lng}
    - startTime, endTime, status (ACTIVE/COMPLETE/HIGH_RISK)
    - lastLocation{lat, lng, speed, updatedAt}
    - drowsyEventCount, closeAttemptCount

events/{id}
    - type (IMPAIRMENT/CLOSE_ATTEMPT), subtype (EYE_CLOSURE/HEAD_DROP/etc.)
    - driverId, tripId, companyId, timestamp, lat, lng, severity

otps/{tripId}
    - code, generatedAt, expiresAt, used
```

---

## Key Design Decisions

1. **Live camera preview with colored border** — Front camera feed displayed in a rounded box on the driver's screen. Border color reflects monitoring status: green (active), amber (calibrating), orange (face not detected), red (impairment alert). Uses CameraX Preview use case bound to the foreground service lifecycle, with surface provided by the UI's PreviewView.

2. **Continuous alerting** — Unlike one-shot alert systems, VigileX re-triggers the alarm every 5 seconds as long as the drowsiness condition persists (eyes closed, head dropped). This ensures the driver cannot sleep through a single alarm.

3. **No speed gate** — Monitoring runs at all speeds including stationary. This was changed from an earlier 20 km/h threshold because drivers can fall asleep even while stopped (at traffic lights, waiting areas). The accelerometer-based erratic motion detection uses a high threshold (8 m/s², 3s sustained) to avoid false alarms from normal turns and parking maneuvers.

4. **Phone authorization gate via a separate mirror collection** — OTP is only sent to numbers pre-registered by a Super Admin (owners) or Owner (drivers), blocking SMS spam and unauthorized access.

   The gate must run *before* sign-in, when `request.auth` is null. That makes `users` unreadable under production rules — and reopening it isn't an option, because those docs carry `exitPin`, the credential gating driver sign-out. So the app keeps **`authorized_phones`**: a zero-PII mirror with public `get` and `list` denied, so an attacker can at most confirm a number they already knew, one at a time.

   Writes happen alongside `addOwner` / `addDriver`, revocation happens before `deleteUser`, and users predating the collection are backfilled idempotently on dashboard load — no migration script.

   > **Historical note.** The gate originally queried `users` by phone. That worked only because Firestore was in test mode; the day production rules landed, every non-Super-Admin login broke with a misleading "not authorized" message, because `runCatching{}.getOrDefault(false)` swallowed the `PERMISSION_DENIED`. Hence the current design, and hence `isPhoneAuthorized()` throws instead of defaulting — so callers can distinguish "not registered" from "couldn't check" and fail open on the latter.

5. **WakeLock + foreground service** — A `PARTIAL_WAKE_LOCK` keeps the CPU running when the screen is off. Combined with `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_LOCATION` types, this ensures CameraX continues processing frames even when the user presses the power button.

6. **Offline resilience** — If Firestore write fails, events are queued in Room DB (`PendingEventEntity`) and synced later via WorkManager.

7. **Placeholder UID pattern** — Owners create drivers before they ever log in. A `pending_<phone>` doc is created in Firestore. On first OTP login, the auth flow migrates all fields (including `exitPin`) to the real Firebase Auth UID.

8. **Adaptive icon with vector drawables** — Launcher icons use proper vector drawables (not just color references) to prevent Samsung PackageInstaller crash during sideloaded APK install.

9. **PIN-locked sign-out** — Driver sign-out always requires a 6-digit exit PIN, regardless of whether a trip is active. This prevents drivers from disabling monitoring without owner knowledge.

10. **Stateful alarm with two stop paths** — Early builds fired three escalating bursts and had no way to stop early, so the alarm kept sounding after the driver was fully awake. It now loops a single `MediaPlayer` and stops on either an automatic trigger (`DrowsinessAnalyzer` tracks `eyesOpenSinceMs`; 2 seconds open emits `MonitoringStatus.Recovered`) or a manual **Stop Alarm** button. The UI reaches the service through a `@Volatile` companion `instance` set in `onCreate` and cleared in `onDestroy`. An `isAlertActive` flag makes `triggerAlert()` a no-op while an alarm is already playing, so repeat detections can't stack overlapping players.

11. **Collapsible camera preview** — Drivers reported the full-size preview was distracting. A `(−)` / `(+)` toggle shrinks it to a 120×90dp thumbnail and back. Detection is unaffected either way: the `Preview` use case is bound to the service, and the composable only attaches a surface to it.

12. **Production Firestore rules, not test mode** — Test-mode rules carry a 30-day expiry after which all client access is denied. [`firestore.rules`](../firestore.rules) implements per-collection, per-role access with a `match /{document=**} { allow read, write: if false; }` catch-all. Note that the `users` read rule matches the auth token against both E.164 and bare-10-digit stored phones, since docs created by hand in the Console may use either — without that, the `pending_` → real-UID migration silently fails.
