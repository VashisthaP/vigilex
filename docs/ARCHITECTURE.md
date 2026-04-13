# VigileX - Architecture Overview

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Navigation | Jetpack Navigation Compose |
| Auth | Firebase Phone Auth (OTP) |
| Database | Cloud Firestore (real-time listeners) |
| Push | Firebase Cloud Messaging |
| Camera | CameraX (headless, no preview surface) |
| ML | ML Kit Face Detection (on-device) |
| Maps | Google Maps Compose + Google Places SDK |
| Location | Fused Location Provider |
| Local DB | Room (offline event queue) |
| Background | Foreground Service (camera + location types) |

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
|   |   |-- AuthViewModel.kt       # Firebase phone auth, role resolution, user migration
|   |
|   |-- driver/                    # Driver monitoring
|   |   |-- DriverHomeScreen.kt    # 3-step setup: Permissions -> Bluetooth -> Monitoring
|   |   |-- DriverViewModel.kt     # Trip observation, service lifecycle, PIN validation
|   |   |-- service/
|   |       |-- MonitoringForegroundService.kt  # Camera + location pipeline
|   |       |-- DrowsinessAnalyzer.kt           # ML Kit face analysis + alerting logic
|   |       |-- AlertOrchestrator.kt            # Sound/vibration alerts
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
|       |-- SuperAdminDashboardScreen.kt  # View all companies
|       |-- AddCompanyScreen.kt           # Create owner with phone + PIN
|       |-- SuperAdminViewModel.kt        # Company/owner CRUD
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
CameraX (front camera, headless)
    |
    v
DrowsinessAnalyzer (ImageAnalysis.Analyzer)
    |-- ML Kit FaceDetector -> eye openness, head euler angles
    |-- Accelerometer sensor -> lateral motion detection
    |
    |-- [First 60s] Calibration phase: collect baseline eye openness
    |-- [After calibration] Active detection:
    |       |-- Eye closure > 3s -> IMPAIRMENT alert
    |       |-- Head drop (euler Z) -> IMPAIRMENT alert
    |       |-- Erratic lateral motion -> IMPAIRMENT alert
    |       |-- Speed gate: alerts only when speed > 20 km/h
    |
    v
AlertOrchestrator
    |-- Plays alarm sound via MediaPlayer
    |-- Triggers device vibration
    |
    v
MonitoringForegroundService
    |-- Writes ImpairmentEvent to Firestore (or Room if offline)
    |-- Increments trip counter (drowsyEventCount)
    |-- Escalation: 3+ events in 30 min -> HIGH_RISK status
    |-- Updates trip lastLocation every 30s via FusedLocationProvider
```

### Authentication Flow

```
Phone number input
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
    |-- DRIVER      -> DriverHomeScreen (permissions -> bluetooth -> monitoring)
    |-- OWNER       -> OwnerDashboardScreen (stats, drivers, trips)
    |-- SUPER_ADMIN -> SuperAdminDashboardScreen (companies, create owners)
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

1. **Headless camera** - No preview surface needed; CameraX runs inside a foreground service with `FOREGROUND_SERVICE_CAMERA` type. Screen stays on via `FLAG_KEEP_SCREEN_ON` without dimming brightness.

2. **Speed gate** - Drowsiness alerts suppressed below 20 km/h to avoid false positives when parked or in traffic. Calibration still runs regardless of speed.

3. **Offline resilience** - If Firestore write fails, events are queued in Room DB (`PendingEventEntity`) and synced later via WorkManager.

4. **Placeholder UID pattern** - Owners create drivers before they ever log in. A `pending_<phone>` doc is created in Firestore. On first OTP login, the auth flow migrates all fields (including `exitPin`) to the real Firebase Auth UID.

5. **Adaptive icon with vector drawables** - Launcher icons use proper vector drawables (not just color references) to prevent Samsung PackageInstaller crash during sideloaded APK install.
