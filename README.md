# VigileX — Driver Alertness & Safety Monitor

Android app that continuously monitors drivers for drowsiness and impairment using on-device ML (Google ML Kit Face Detection) and accelerometer analysis. Supports three roles: Super Admin, Transport Company Owner, and Driver.

---

## Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- A Firebase project (Blaze plan not required — free Spark plan works)
- A Google Cloud project with Maps SDK + Places API enabled

---

## Step 1 — Firebase Setup

1. Go to [console.firebase.google.com](https://console.firebase.google.com) → Create project → **VigileX**
2. Add an Android app with package name `com.vigilex`
3. Download `google-services.json` and place it at:
   ```
   app/google-services.json
   ```
4. In Firebase Console:
   - **Authentication** → Sign-in method → Enable **Email/Password**
   - **Firestore Database** → Create database → Start in **test mode** (switch to production rules below)
   - **Cloud Messaging** → enabled by default

5. Deploy Firestore security rules:
   ```bash
   firebase deploy --only firestore:rules
   ```
   Or copy-paste `firestore.rules` into the Firebase Console → Firestore → Rules tab.

---

## Step 2 — Google Maps API Key

1. Go to [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. Create a new **API Key**, restrict it to Android apps with package `com.vigilex`
3. Enable these APIs:
   - **Maps SDK for Android**
   - **Places API**
   - **Directions API** (optional, for route replay)

---

## Step 3 — local.properties

Open `local.properties` (project root — never commit this file) and fill in:

```properties
MAPS_API_KEY=YOUR_ACTUAL_MAPS_API_KEY
SUPER_ADMIN_EMAIL=pravs.x@gmail.com
SUPER_ADMIN_PHONE=+918587089545
sdk.dir=C\:\\Users\\v-vashisthap\\AppData\\Local\\Android\\Sdk
```

---

## Step 4 — Alarm Sound

Replace the placeholder at `app/src/main/res/raw/alert_alarm.mp3` with a real MP3 alarm file.
Suggested source: [freesound.org](https://freesound.org) — search "alarm" and download a royalty-free sound.

---

## Step 5 — Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Or press **Run ▶** in Android Studio.

---

## First Launch

1. Open the app — you'll see the Login screen
2. Sign in with the Super Admin email (`pravs.x@gmail.com`) and **any password you choose**
   - On first login, VigileX seeds the Super Admin Firestore doc automatically
   - The password you use on first login becomes the Super Admin password (Firebase Auth)
3. You'll land on the Super Admin Dashboard
4. Tap **+** → Add your first company and owner

---

## Role Flow

```
Super Admin
  → Add Company (creates owner account, initial password = owner's phone number)
  
Owner (logs in with their email + phone-as-password, should change password)
  → Drivers → Add Driver (initial password = driver's phone number)
  → Drivers → Assign Trip → enter destination
  
Driver (logs in with their email + phone-as-password)
  → DriverHomeScreen auto-starts monitoring when a trip is assigned
  → Monitoring begins after 60-second calibration
  → Cannot exit without OTP or geofence arrival
```

---

## Architecture

```
app/
├── core/                    Shared: models, Room DB, Firestore, DI, WorkManager
├── feature/
│   ├── auth/                Login screen + AuthViewModel
│   ├── driver/              DriverHomeScreen + MonitoringForegroundService
│   │   └── service/         DrowsinessAnalyzer, AlertOrchestrator, BootReceiver
│   ├── owner/               Dashboard, DriverDetail, TripHistory, DriversManagement
│   └── superadmin/          Dashboard, AddCompany, CompanyDetail
├── navigation/              NavGraph + Routes
└── ui/                      Theme (navy + amber), shared components
```

**Key design decisions:**
- Single APK — role detected post-login, different nav graph served
- `foregroundServiceType="camera|location"` in manifest allows headless operation on Android 14+
- Screen-on-at-1%-brightness workaround for OEM camera restrictions when screen is "off"
- Room offline queue → WorkManager sync on network restore — no data loss if internet drops
- ML Kit face detection is 100% on-device — works in tunnels, dead zones, anywhere

---

## Impairment Detection

| Signal | Source | Threshold |
|---|---|---|
| Eye closure | ML Kit `leftEyeOpenProbability` + `rightEyeOpenProbability` | < calibrated threshold (default 0.25) for ≥ 2s |
| Head drop | ML Kit `headEulerAngleZ/Y` | |eulerZ| > 20° or |eulerY| > 25° for ≥ 1.5s |
| Erratic lateral motion | `TYPE_LINEAR_ACCELERATION` X-axis | > 4 m/s² sustained ≥ 2s (drunk swerving indicator) |

Events are tagged with a **subtype**: `eye_closure`, `head_drop`, `erratic_motion`, or `combined`.
Speed gate: detection suspended below 20 km/h.
Calibration: first 60 seconds capture driver baseline, dynamic threshold = `avg × 0.5`.

> **Disclaimer**: VigileX detects behavioral impairment indicators only. It is not a substitute for legal sobriety testing or medical assessment.

---

## Permissions Required

The app will request all permissions at runtime with clear rationale dialogs:

- `CAMERA` — face monitoring
- `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` — GPS tracking
- `POST_NOTIFICATIONS` — Android 13+ alerts
- `BLUETOOTH_CONNECT` + `RECORD_AUDIO` — BT headset alerts
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — prevent OS killing the service

---

## FCM Push (Owner Notifications)

Owner receives FCM notifications on impairment events. To also trigger WhatsApp deep links:

Add a **Firestore Cloud Function** (not included here — requires Firebase Blaze plan) watching `events/` collection writes and sending FCM to `trips/{tripId}.ownerId`'s FCM token. The WhatsApp deep link can be opened via:

```kotlin
Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/<owner_phone>?text=..."))
```

Alternatively, owners will receive FCM push notifications without the Cloud Function — the app's `MonitoringForegroundService` calls FCM directly via Firestore token lookup if you add an FCM HTTP v1 API call. For full FCM send capability from device, use a backend function.
