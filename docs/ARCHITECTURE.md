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
|   |       |-- AlertOrchestrator.kt            # Sound/vibration alerts (escalating)
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
    |-- Forces audio volume to MAX
    |-- Plays 3 escalating alarm bursts via MediaPlayer
    |-- Routes audio to Bluetooth SCO if connected, else phone speaker
    |-- Triggers urgent vibration pattern
    |-- ** CONTINUOUS ** — re-alerts every 5 seconds while condition persists
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
Authorization Gate (NEW)
    |-- Check if phone is Super Admin -> always allowed
    |-- Check Firestore for user doc with matching phone
    |-- If not found -> "This phone number is not authorized"
    |-- If found -> proceed to send OTP
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
users/{uid}
    - name, phone, email, role, companyId, exitPin, fcmToken

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

4. **Phone authorization gate** — OTP is only sent to phone numbers pre-registered in Firestore by a Super Admin (for owners) or Owner (for drivers). Unauthorized numbers are blocked before any SMS is sent, preventing SMS spam and unauthorized access.

5. **WakeLock + foreground service** — A `PARTIAL_WAKE_LOCK` keeps the CPU running when the screen is off. Combined with `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_LOCATION` types, this ensures CameraX continues processing frames even when the user presses the power button.

6. **Offline resilience** — If Firestore write fails, events are queued in Room DB (`PendingEventEntity`) and synced later via WorkManager.

7. **Placeholder UID pattern** — Owners create drivers before they ever log in. A `pending_<phone>` doc is created in Firestore. On first OTP login, the auth flow migrates all fields (including `exitPin`) to the real Firebase Auth UID.

8. **Adaptive icon with vector drawables** — Launcher icons use proper vector drawables (not just color references) to prevent Samsung PackageInstaller crash during sideloaded APK install.

9. **PIN-locked sign-out** — Driver sign-out always requires a 6-digit exit PIN, regardless of whether a trip is active. This prevents drivers from disabling monitoring without owner knowledge.
