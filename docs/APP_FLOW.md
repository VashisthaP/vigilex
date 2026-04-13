# VigileX - Role-Based App Flow

## Roles

| Role | Created by | Purpose |
|------|-----------|---------|
| **Super Admin** | Auto-seeded on first app launch | Creates and manages fleet owners |
| **Owner** | Super Admin | Manages drivers, assigns trips, monitors fleet |
| **Driver** | Owner | Drives with real-time drowsiness/impairment monitoring |

---

## Super Admin Flow

```
Login (phone OTP)
    |
    v
Super Admin Dashboard
    |-- View all registered companies and their owners
    |-- [+ Add Company] ->
    |       |-- Enter: Company Name, Owner Name, Owner Phone, 6-digit Exit PIN
    |       |-- Creates: Company doc + Owner user doc (role=OWNER, exitPin set)
    |       |-- Owner can now login with their phone number
    |
    |-- Sign Out
```

### What Super Admin CAN do:
- Create new companies with owners
- View all companies in the system
- Set owner's exit PIN (used for owner's own sign-out security)

### What Super Admin CANNOT do:
- Create drivers (that's the owner's job)
- View trip details or driver monitoring data
- Delete companies (future feature)

---

## Owner Flow

```
Login (phone OTP)
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
Login (phone OTP - SMS sent to registered phone)
    |
    v
Step 1: Permissions
    |-- Camera, Location, Notifications, Bluetooth
    |-- Auto-advances if already granted
    |
    v
Step 2: Bluetooth Setup
    |-- Scan for nearby Bluetooth speakers/earbuds
    |-- Connect for alert audio output
    |-- "Skip" option if not needed
    |
    v
Step 3: Monitoring Screen (MAIN SCREEN)
    |
    |-- Camera starts (front-facing, headless - no preview)
    |-- Calibration phase (60 seconds)
    |       |-- Collects baseline eye openness from face detection
    |       |-- Shows "Calibrating (X%)" with amber dot
    |       |-- No alerts during calibration
    |
    |-- Active monitoring
    |       |-- Status dot:
    |       |       GREEN  = "Monitoring Active"
    |       |       AMBER  = "Calibrating" or "Stationary"
    |       |       RED    = "Alert! Impairment Detected"
    |       |
    |       |-- If trip assigned by owner:
    |       |       Shows "Destination: [name]" as read-only info
    |       |       Location written to Firestore every 30s
    |       |
    |       |-- If no trip assigned:
    |       |       Shows "Monitoring Active"
    |       |       Camera + face detection still runs
    |       |       (monitoring works regardless of trip assignment)
    |       |
    |       |-- Speed gate:
    |       |       Below 20 km/h -> "Stationary" (alerts suppressed)
    |       |       Above 20 km/h -> full detection active
    |       |
    |       |-- Drowsiness/impairment detection:
    |       |       Eyes closed > 3s -> ALARM + vibration + event logged
    |       |       Head drop detected -> ALARM + event logged
    |       |       Erratic motion -> ALARM + event logged
    |       |       3+ alerts in 30 min -> Trip escalated to HIGH_RISK
    |
    |-- Sign Out flow:
            |
            |-- [No active trip] -> Simple confirmation dialog
            |       |-- "Sign Out" -> stops camera, stops service, returns to login
            |
            |-- [Active trip] -> Warning dialog
                    |-- "You haven't reached your destination"
                    |-- "Ask your owner to delete the trip, or enter your exit PIN"
                    |-- [Enter PIN] -> 6-digit PIN dialog
                    |       |-- Correct PIN -> stops everything, signs out
                    |       |-- Wrong PIN -> "Invalid PIN. Contact your owner."
                    |-- [Cancel] -> back to monitoring
```

### What Driver CAN do:
- Login with registered phone number
- Connect Bluetooth audio device for alerts
- View assigned destination (read-only)
- Sign out freely when no trip is assigned
- Force sign out with exit PIN during active trip

### What Driver CANNOT do:
- Assign themselves trips
- Change destination
- Disable monitoring while signed in
- Sign out during active trip without the exit PIN
- Access owner dashboard or any management features

---

## Security Model

```
+------------------+     creates      +------------------+     creates      +------------------+
|   SUPER ADMIN    | --------------> |      OWNER       | --------------> |      DRIVER      |
|                  |   (phone + PIN) |                  |   (phone + PIN) |                  |
| - View companies |                 | - Manage drivers |                 | - Monitoring only|
| - Create owners  |                 | - Assign trips   |                 | - PIN to exit    |
+------------------+                 | - View live data |                 +------------------+
                                     | - Delete drivers |
                                     | - Delete trips   |
                                     +------------------+
```

### PIN System:
- **Exit PIN** is set by the creator (Super Admin sets owner PIN, Owner sets driver PIN)
- PIN is stored in Firestore `users/{uid}/exitPin`
- PIN is required to sign out during an active trip
- PIN is NOT the OTP used for login (OTP is auto-generated by Firebase)

### Developer Quick Controls:
- **Force stop**: `adb shell am force-stop com.vigilex` (kills everything instantly)
- **Check service**: `adb shell dumpsys activity services com.vigilex`
- **View logs**: `adb logcat | findstr vigilex`

---

## API Usage & Costs

| Service | When called | Estimated monthly cost (5 trucks, 4 trips/day) |
|---------|-----------|-----------------------------------------------|
| Firebase Phone Auth | Driver/Owner login | Free tier (10K/month) |
| Cloud Firestore | Real-time listeners, event writes | Free tier (50K reads, 20K writes/day) |
| Google Places API | Owner assigns trip (autocomplete + fetch) | ~$0 (within $200/month free credit) |
| Google Maps SDK | Owner views driver location | Free (Maps SDK for Android is free) |
| ML Kit Face Detection | Every camera frame (on-device) | $0 (runs entirely on-device) |
| CameraX | Driver monitoring session | $0 (on-device) |

**Total estimated cost: $0/month** for small fleet operations (within Google Cloud free tiers).
