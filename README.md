# VigileX — Driver Alertness & Safety Monitor

Android app that continuously monitors drivers for drowsiness and impairment using on-device ML (Google ML Kit Face Detection) plus accelerometer analysis. One APK, three roles: **Super Admin**, **Fleet Owner**, **Driver**.

Monitoring is fully on-device — it works in tunnels, dead zones, and with no internet. Firebase is used only for authentication, fleet data, and syncing events after the fact.

---

## How it works in one paragraph

A driver logs in with their phone number. The app immediately starts a foreground service that binds the front camera, runs ML Kit face detection on every frame, and watches the accelerometer. After a 15-second calibration that learns the driver's normal eye openness, it alerts on three signals: eyes closed 2s+, head dropped 1.5s+, or sustained lateral swerving. The alarm loops until the driver opens their eyes for 2 seconds or taps **Stop Alarm**. Every alert is written to Firestore (queued locally if offline) so the fleet owner sees it live on a map. The driver can't sign out without a 6-digit PIN set by their owner.

---

## Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- A Firebase project (free **Spark** plan is enough)
- A Google Cloud project with Maps SDK + Places API enabled
- A physical Android device — the emulator has no usable front camera for face detection

---

## Step 1 — Firebase project

1. [console.firebase.google.com](https://console.firebase.google.com) → Create project → **VigileX**
2. Add an Android app with package name `com.vigilex`
3. Download `google-services.json` → place at `app/google-services.json` (gitignored)

### 1a. Register SHA fingerprints — **do this before enabling Phone Auth**

Phone Auth will not work without these, and the Firebase Console greys out the Save button until at least one SHA-1 is present.

```bash
./gradlew signingReport
```

Copy **both SHA-1 and SHA-256** for the `debug` and `release` variants (four values total) into Firebase Console → Project Settings → Your apps → Android app → **Add fingerprint**.

> When pasting SHA-256, select the **SHA256** radio button first — the Console rejects it against the SHA1 option with a confusing format error.

After adding fingerprints, **re-download `google-services.json`** — it changes.

### 1b. Enable Phone authentication

Firebase Console → **Authentication** → Sign-in method → **Phone** → Enable → Save.

Then Authentication → **Settings** → SMS region policy → allow **India** (or your target region). Without this you get `SMS unable to be sent until this region is enabled`.

### 1c. Firestore

**Firestore Database** → Create database → any location.

Deploy the security rules — **do not leave it in test mode**, Firebase auto-expires those after 30 days and all client access starts failing:

```bash
firebase deploy --only firestore:rules
```

Or paste [`firestore.rules`](firestore.rules) into Console → Firestore → Rules → Publish.

---

## Step 2 — Google Maps API key

1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials → Create **API Key**
2. Restrict it to Android apps with package `com.vigilex` + your SHA-1
3. Enable: **Maps SDK for Android**, **Places API**

---

## Step 3 — local.properties

Project root, never committed:

```properties
MAPS_API_KEY=YOUR_MAPS_API_KEY
SUPER_ADMIN_EMAIL=you@example.com
SUPER_ADMIN_PHONE=+918587089545
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

`SUPER_ADMIN_PHONE` is the one number that bypasses the authorization gate — it's how you bootstrap the very first login. Everyone else must be registered by someone above them.

---

## Step 4 — Alarm sound

`app/src/main/res/raw/alert_alarm.mp3` — replace with a real alarm MP3. It plays looped at max volume, so pick something that cuts through road noise. [freesound.org](https://freesound.org) has royalty-free options.

---

## Step 5 — Build & run

```bash
./gradlew installDebug
```

---

## Bootstrapping your first users

Roles are strictly hierarchical — **one phone number = one role**:

```
Super Admin  ──authorizes──▶  Owner  ──adds──▶  Driver
```

1. **Super Admin** logs in with `SUPER_ADMIN_PHONE`. On first login the app seeds its own Firestore user doc and `superadmin/config`.
2. Super Admin → **Add Owner** (name, email, phone). This creates the owner's user doc, auto-creates a company, and mirrors the phone into `authorized_phones` so they can receive an OTP.
3. **Owner** logs in with their phone → **Drivers** → **Add Driver** (name, phone, 6-digit exit PIN).
4. Owner → **Assign Trip** → search a destination via Places autocomplete.
5. **Driver** logs in → grants permissions → picks Bluetooth audio (or skips) → monitoring starts immediately.

To demo without three real phones, you can create a driver directly in the Firestore Console — see [Demo setup](#demo-setup-without-three-phones).

---

## The authorization gate

**Only pre-registered phone numbers can receive an OTP.** Unregistered numbers are rejected before any SMS is sent, which prevents both SMS-quota abuse and unauthorized access.

This check runs *before* sign-in, when `request.auth` is null and the `users` collection is therefore unreadable (it holds `exitPin`). So the app keeps a parallel **`authorized_phones`** collection:

- Keyed by phone with the `+` stripped — `+919897831882` → doc ID `919897831882`
- Holds no PII, just `{ active: true, createdAt }`
- Rules allow public `get` (needed logged-out) but deny `list`, so it can't be enumerated
- Written automatically when an owner or driver is added, removed when they're deleted
- Pre-existing users are backfilled the first time a Super Admin or Owner dashboard loads

If the lookup *errors* (offline, rules misconfigured) the gate **fails open** and sends the OTP anyway. It only saves SMS cost — the real boundary is post-authentication, where a user with no Firestore doc is signed straight back out.

> **If login says "not authorized" for a number that clearly exists**, check that its `authorized_phones` doc is present, and that the doc ID has no `+`.

---

## Impairment detection

| Signal | Source | Threshold | Duration |
|---|---|---|---|
| `EYE_CLOSURE` | ML Kit `left/rightEyeOpenProbability` | both < calibrated threshold | ≥ 2s |
| `HEAD_DROP` | ML Kit `headEulerAngleZ/Y` | \|Z\| > 25° or \|Y\| > 30° | ≥ 1.5s |
| `ERRATIC_MOTION` | `TYPE_LINEAR_ACCELERATION` X-axis | > 8 m/s² | ≥ 3s sustained |
| `COMBINED` | two *different* signals within 10s | — | — |

- **Calibration**: first 15 seconds capture the driver's baseline; threshold = `avg × 0.6`, clamped to 0.20–0.45. No alerts fire during calibration.
- **No speed gate.** Monitoring runs at every speed including stationary — drivers fall asleep at lights and in waiting areas too.
- **Re-alerts every 5s** while the condition persists, so nobody sleeps through one alarm.
- **Escalation**: 3+ events in 30 minutes marks the trip `HIGH_RISK` and notifies the owner.

### What it does *not* detect

It does **not** track gaze or "eyes on road" — ML Kit provides head pose, not gaze direction. A driver looking down at a phone with eyes open only trips `HEAD_DROP` if the tilt exceeds the angle thresholds. True attention monitoring would need MediaPipe FaceMesh or a custom gaze model.

> **Disclaimer**: VigileX detects behavioral impairment indicators only. It is not a substitute for legal sobriety testing or medical assessment.

### Stopping the alarm

| Trigger | Behaviour |
|---|---|
| Eyes open ≥ 2s | Auto-stops, status → `Recovered`, border turns green |
| **Stop Alarm** button | Stops immediately, clears alert state |

The alarm **loops** rather than firing a fixed number of bursts, and `AlertOrchestrator` guards against stacking so a second detection can't start a second overlapping MediaPlayer. There is **no vibration** — testers found it more distracting than useful.

---

## Screen-off behaviour

Monitoring must survive the driver pressing the power button. Five layers make that work:

| Layer | Mechanism |
|---|---|
| Service | Foreground service with ongoing notification |
| Service type | `foregroundServiceType="camera\|location"` (required on Android 14+) |
| CPU | `PARTIAL_WAKE_LOCK`, 8-hour cap |
| Camera | CameraX bound to the **service** lifecycle, not the Activity |
| Recovery | `START_STICKY` + `BootReceiver` for reboots |

**OEM caveat**: Xiaomi/MIUI, Oppo, Vivo and Samsung kill background services aggressively regardless of the above. On those devices, disable battery optimisation for VigileX and enable AutoStart (MIUI) / add to "Never sleeping apps" (Samsung).

---

## Project structure

```
app/src/main/java/com/vigilex/
├── core/
│   ├── data/local/          Room DB — offline event queue
│   ├── data/remote/         FirestoreDataSource — every Firestore call
│   ├── model/               User, Trip, Company, ImpairmentEvent, enums
│   ├── di/                  Hilt modules
│   └── worker/              SyncEventsWorker — flushes the queue every 15 min
├── feature/
│   ├── auth/                LoginScreen, AuthViewModel (gate + OTP + role resolution)
│   ├── driver/              DriverHomeScreen, DriverViewModel
│   │   └── service/         MonitoringForegroundService, DrowsinessAnalyzer,
│   │                        AlertOrchestrator, GeofenceReceiver, BootReceiver
│   ├── owner/               dashboard, drivers, trips, driverdetail, settings
│   └── superadmin/          dashboard, add owner, owner detail
├── navigation/              VigileXNavGraph, Routes
└── ui/                      theme (navy + amber), shared components
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the data-flow detail and [`docs/APP_FLOW.md`](docs/APP_FLOW.md) for screen-by-screen behaviour. [`docs/VigileX_Architecture_Diagrams.html`](docs/VigileX_Architecture_Diagrams.html) renders the whole system as diagrams — open it in a browser.

---

## Firestore collections

| Collection | Purpose |
|---|---|
| `superadmin/config` | Bootstrap record for the Super Admin |
| `authorized_phones/{phone}` | Pre-auth OTP gate — zero PII, public `get`, `list` denied |
| `users/{uid}` | name, phone, email, role, companyId, **exitPin**, fcmToken |
| `companies/{id}` | companyName, ownerUid, driverCount |
| `trips/{id}` | driver/owner/company IDs, destination, status, lastLocation, counters |
| `events/{id}` | type, subtype, severity, lat/lng, timestamp — immutable audit trail |
| `otps/{tripId}` | Legacy owner-generated exit OTP (superseded by the exit PIN) |

`users` docs hold `exitPin`, the credential that gates driver sign-out — which is why the collection is never publicly readable and why `authorized_phones` exists separately.

---

## Permissions

`CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `RECORD_AUDIO` (required by BT SCO), `WAKE_LOCK`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_LOCATION`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## Demo setup without three phones

Create the driver directly in Firestore Console:

**`users` → Add document**, ID `pending_919897831882`:

| Field | Type | Value |
|---|---|---|
| `name` | string | `Demo Driver` |
| `phone` | string | `+919897831882` |
| `role` | string | `driver` |
| `companyId` | string | *(an existing ID from `companies`)* |
| `exitPin` | string | `123456` |
| `email` | string | *(empty)* |

**`authorized_phones` → Add document**, ID `919897831882`, field `active` (boolean) `true`.

The `pending_` UID prefix is intentional — see design note 7 in `docs/ARCHITECTURE.md`.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "This phone number is not authorized" for a registered number | Missing `authorized_phones` doc, or the doc ID includes a `+`. Add it without the `+`. |
| "Missing a valid app identifier / Play Integrity failed" | SHA fingerprints not registered, or `google-services.json` is stale. Re-add fingerprints and re-download the file. |
| "This operation is not allowed" on send OTP | Phone provider not enabled in Firebase Console. |
| "SMS unable to be sent until this region enabled" | Authentication → Settings → SMS region policy. |
| A browser flashes open then closes before the OTP arrives | Expected. That's Firebase's invisible reCAPTCHA / Play Integrity check — it can't be removed, only made less frequent by keeping Play Services current. |
| Client access denied after ~30 days | Firestore was left in test mode. Deploy `firestore.rules`. |
| Camera stops when screen turns off | OEM battery optimisation — see [Screen-off behaviour](#screen-off-behaviour). |
| Gradle: `Unable to establish loopback connection` | JVM/network sandbox issue, not a code problem. Build from Android Studio. |

---

## Cost

Everything sits inside Google's free tiers for small fleets: ML Kit and CameraX are on-device ($0), Phone Auth allows 10K verifications/month, Firestore allows 50K reads / 20K writes per day, Maps SDK for Android is free, and Places stays within the $200 monthly credit.

**≈ $0/month** for a 5-vehicle fleet at 4 trips/day.
