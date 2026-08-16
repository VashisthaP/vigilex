# VigileX - Role-Based App Flow

## Roles

| Role | Created by | Purpose |
|------|-----------|---------|
| **Super Admin** | Auto-seeded on first app launch | Authorizes fleet owners |
| **Owner** | Super Admin | Manages drivers, assigns trips, monitors fleet |
| **Driver** | Owner | Drives with real-time drowsiness/impairment monitoring |

---

## Authorization Chain

```
Super Admin  --(authorizes)-->  Owner  --(adds)-->  Driver
```

**Only phone numbers pre-registered in Firestore can receive OTP and log in.**
Unauthorized numbers are blocked at the OTP send step itself — no SMS is sent.

---

## Super Admin Flow

```
Login (phone OTP — Super Admin phone is hardcoded in BuildConfig)
    |
    v
Super Admin Dashboard
    |-- Stats: Owners count, Active Trips, Total Alerts
    |
    |-- [Authorized Owners] list
    |       |-- Each owner shows: Name, Email, Phone
    |       |-- [Delete] (trash icon) -> removes owner's access
    |
    |-- [+ Add Owner] ->
    |       |-- Enter: Owner Name, Owner Email (optional), Owner Phone
    |       |-- Creates: Owner user doc (role=OWNER) + auto-generated company
    |       |-- Owner can now login with their phone number via OTP
    |
    |-- Sign Out
```

### What Super Admin CAN do:
- Authorize new owners (name, email, phone)
- View all authorized owners
- Remove owner access (delete from Firestore)
- View global stats (active trips, alerts)

### What Super Admin CANNOT do:
- Create drivers (that's the owner's job)
- View trip details or driver monitoring data
- Access the camera feed

---

## Owner Flow

```
Login (phone OTP — only works if authorized by Super Admin)
    |
    v
Owner Dashboard
    |-- Fleet statistics overview (total drivers, active trips, alerts)
    |
    |-- [Drivers] -> Drivers Management Screen
    |       |-- View all drivers (with live status: idle / on active trip)
    |       |-- [+ Add Driver] ->
    |       |       |-- Enter: Name, Phone, 6-digit Exit PIN
    |       |       |-- Creates: Driver user doc with pending_{phone} UID
    |       |       |-- Driver can now login with their phone number
    |       |
    |       |-- [Assign Trip] (only shown when driver has no active trip) ->
    |       |       |-- Search destination via Google Places autocomplete
    |       |       |       (inline scrollable list — keyboard stays open)
    |       |       |-- Selects address -> resolves lat/lng
    |       |       |-- Creates: Trip doc (status=ACTIVE, destination set)
    |       |       |-- Driver's monitoring service starts writing location to this trip
    |       |
    |       |-- [Delete Driver] (trash icon) ->
    |               |-- Confirmation dialog -> removes driver from Firestore
    |
    |-- [Trip History] -> Trip History Screen
    |       |-- Filter by: All / Active / Complete / High Risk
    |       |-- Tap trip -> Trip Detail Screen
    |       |       |-- Trip summary (destination, start time, alert counts, status badge)
    |       |       |-- Event timeline (impairment events with severity + timestamp)
    |       |
    |       |-- [Delete Trip] (trash icon) ->
    |               |-- Confirmation dialog -> removes trip from Firestore
    |
    |-- [Driver Detail] (from dashboard) -> Driver Detail Screen
    |       |-- Live Google Map showing driver's last known location
    |       |-- Route polyline drawn from event locations
    |       |-- Impairment alert count + exit attempt count
    |       |-- Event timeline
    |
    |-- [Settings] -> Sign Out
```

### What Owner CAN do:
- Add/delete drivers with phone number and exit PIN
- Assign trips with real Google Maps destinations (Places autocomplete)
- Monitor driver locations on map in real-time
- View trip history with event timelines
- Delete old trips to keep the list clean
- See which drivers have active trips vs idle

### What Owner CANNOT do:
- Access the driver's camera feed
- Override driver's monitoring (it runs automatically)
- Create other owners (Super Admin only)

---

## Driver Flow

```
Login (phone OTP — only works if added by Owner)
    |
    v
Step 1: Permissions
    |-- Camera, Location, Notifications, Bluetooth
    |-- Auto-advances if already granted
    |
    v
Step 2: Bluetooth Setup
    |-- Shows paired Bluetooth audio devices
    |-- Select device for alert audio output
    |-- "Skip — Use Phone Speaker" option
    |
    v
Step 3: Monitoring Screen (MAIN SCREEN)
    |
    |-- Monitoring starts IMMEDIATELY after permissions granted
    |-- No need to wait for trip assignment
    |
    |-- [LIVE CAMERA PREVIEW]
    |       |-- Front camera feed shown in rounded box
    |       |-- (−) collapses to a 120x90dp thumbnail, (+) restores full size
    |       |       (detection is unaffected — only the surface shrinks)
    |       |
    |       |-- Colored border indicates status:
    |       |       GREEN  = Face detected, monitoring normally (or Recovered)
    |       |       AMBER  = Calibrating (with progress %)
    |       |       ORANGE = Face not detected — adjust camera
    |       |       RED    = Impairment detected — ALARM ACTIVE
    |       |
    |       |-- Status label below preview:
    |               "Monitoring Active" / "Calibrating (45%)" /
    |               "Face Not Detected — Adjust Camera" / "⚠ Impairment Detected" /
    |               "Driver Awake — Alarm Stopped"
    |
    |-- Calibration phase (first 15 seconds)
    |       |-- Collects baseline eye openness from face detection
    |       |-- Amber border with "Calibrating (X%)"
    |       |-- No alerts during calibration
    |
    |-- Active monitoring (NO speed gate — always active)
    |       |-- Eyes closed > 2s -> ALARM (looping sound, NO vibration)
    |       |       ** CONTINUOUS: re-alerts every 5 seconds while eyes remain closed **
    |       |-- Head drop detected -> ALARM
    |       |-- Erratic lateral motion (8+ m/s², 3s sustained) -> ALARM
    |       |-- Two different signals within 10s -> COMBINED (highest severity)
    |       |-- 3+ alerts in 30 min -> Trip escalated to HIGH_RISK
    |       |
    |       |-- Stopping the alarm — two paths:
    |       |       |-- AUTO:   eyes open >= 2s -> status Recovered, alarm stops
    |       |       |-- MANUAL: [Stop Alarm] button (visible only during an alert)
    |       |       Alarm loops until one of these fires — it never self-terminates.
    |       |
    |       |-- If trip assigned by owner:
    |       |       Shows "Destination: [name]" at top
    |       |       Location written to Firestore every 30s
    |       |       Geofence: auto-completes trip on arrival (200m radius)
    |       |
    |       |-- If no trip assigned:
    |       |       Shows "VigileX Monitoring" at top
    |       |       Camera + face detection still runs
    |
    |-- Screen-off behavior:
    |       |-- WakeLock keeps CPU running
    |       |-- Foreground service keeps camera active
    |       |-- FLAG_KEEP_SCREEN_ON prevents auto-off
    |       |-- Monitoring continues even if user presses power button
    |
    |-- Sign Out flow (PIN ALWAYS REQUIRED):
            |-- Tap "Sign Out" or press Back
            |-- "Monitoring is running. Enter your 6-digit exit PIN."
            |-- [Enter PIN] -> 6-digit PIN dialog
            |       |-- Correct PIN -> stops camera + service, signs out
            |       |-- Wrong PIN -> "Invalid PIN. Contact your owner."
            |-- [Cancel] -> back to monitoring
```

### What Driver CAN do:
- Login with registered phone number
- Connect Bluetooth audio device for alerts
- View assigned destination (read-only)
- See live camera preview with status overlay
- Sign out with exit PIN

### What Driver CANNOT do:
- Assign themselves trips
- Change destination
- Disable monitoring while signed in
- Sign out without the exit PIN
- Access owner dashboard or any management features

---

## Security Model

```
+------------------+   authorizes   +------------------+     adds        +------------------+
|   SUPER ADMIN    | ------------> |      OWNER       | ------------> |      DRIVER      |
|                  |  (name+phone) |                  |  (name+phone  |                  |
| - Auth owners    |               | - Manage drivers |   + exit PIN) | - Monitoring only|
| - Revoke access  |               | - Assign trips   |               | - PIN to exit    |
+------------------+               | - View live data |               +------------------+
                                   | - Delete drivers |
                                   | - Delete trips   |
                                   +------------------+
```

### Authorization Gate:
- **Before OTP is sent**, the app looks up `authorized_phones/{phone without '+'}`
- Only Super Admin phone (from BuildConfig) bypasses this check
- Unauthorized numbers see: "This phone number is not authorized. Contact your administrator."
- No SMS is sent, no Firebase Auth session is created
- The gate reads `authorized_phones` — **not** `users` — because it runs while
  logged out, and `users` holds `exitPin` so it can't be publicly readable.
  The mirror carries no PII and denies `list`, so it can't be enumerated.
- Entries are written on Add Owner / Add Driver, revoked on delete, and
  backfilled for pre-existing users on dashboard load
- If the lookup **errors**, the gate fails open and sends the OTP — it only
  saves SMS cost. Post-auth, a user with no Firestore doc is signed straight
  back out, so access stays closed either way.

### PIN System:
- **Exit PIN** is set by the Owner when adding a driver
- PIN is stored in Firestore `users/{uid}/exitPin`
- PIN is **always required** to sign out (whether or not a trip is active)
- PIN is NOT the OTP used for login (OTP is auto-generated by Firebase)
- PIN lookup falls back to phone-based search if UID lookup fails (handles pending_ migration)

### Developer Quick Controls:
- **Force stop**: `adb shell am force-stop com.vigilex` (kills everything instantly)
- **Check service**: `adb shell dumpsys activity services com.vigilex`
- **View logs**: `adb logcat | findstr vigilex`

---

## API Usage & Costs

| Service | When called | Estimated monthly cost (5 trucks, 4 trips/day) |
|---------|-----------|-----------------------------------------------|
| Firebase Phone Auth | Driver/Owner login (authorized only) | Free tier (10K verifications/month) |
| Cloud Firestore | Real-time listeners, event writes | Free tier (50K reads, 20K writes/day) |
| Google Places API | Owner assigns trip (autocomplete + fetch) | ~$0 (within $200/month free credit) |
| Google Maps SDK | Owner views driver location | Free (Maps SDK for Android is free) |
| ML Kit Face Detection | Every camera frame (on-device) | $0 (runs entirely on-device) |
| CameraX | Driver monitoring session | $0 (on-device) |

**Total estimated cost: $0/month** for small fleet operations (within Google Cloud free tiers).
