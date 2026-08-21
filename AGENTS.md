# AGENTS.md — VigileX developer & AI-agent handbook

Instructions for any AI coding agent (or human) picking up this codebase cold.

Read this **before** editing. It covers the things that aren't discoverable by
reading the code: why constants have the values they do, which invariants break
silently when violated, and which failures are environmental rather than bugs.

Structure and naming *are* discoverable — explore the tree for those.

---

## 1. What this app is

**VigileX** — Android driver alertness monitor. Detects drowsiness and
impairment using on-device ML (Google ML Kit Face Detection) plus accelerometer
analysis, alarms the driver, and reports to a fleet dashboard.

One APK, three roles resolved after login: **Super Admin → Owner → Driver**.

All detection is on-device. Firebase handles auth, fleet data, and event sync
only. **The app must keep working with no network** — that constraint drives the
Room offline queue and is not negotiable.

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3), no XML layouts |
| Architecture | MVVM + Repository, Hilt DI, Navigation Compose |
| Async | Coroutines + `StateFlow`. **Never `LiveData`.** |
| min / target SDK | 26 / 35 |

---

## 2. Build & run

**Use PowerShell, not Git Bash.** Git Bash fails with
`Unable to establish loopback connection`.

**Always pass `--no-daemon`.** The persistent Gradle daemon cannot bind its
localhost socket on the current dev machine (firewall/security software). This
is environmental — not a code problem, and not worth debugging.

```powershell
.\gradlew.bat assembleDebug   --no-daemon   # debug APK
.\gradlew.bat installDebug    --no-daemon   # build + push to device
.\gradlew.bat assembleRelease --no-daemon   # signed, minified APK (sharing)
.\gradlew.bat bundleRelease   --no-daemon   # signed AAB (Play upload)
```

Builds take **7–15 minutes** with `--no-daemon`. Don't assume a hang.

### Verifying a build actually succeeded

Gradle's exit code is masked if you pipe through `tail`/`head`/`Select-Object`
— the pipe's status wins. A failed build then looks successful.

```powershell
.\gradlew.bat assembleRelease --no-daemon; "EXIT=$LASTEXITCODE"
```

Grep the output for `BUILD SUCCESSFUL`, not just the process status.

### Required local config

`local.properties` (gitignored, never commit):

```properties
MAPS_API_KEY=...
SUPER_ADMIN_EMAIL=...
SUPER_ADMIN_PHONE=+91XXXXXXXXXX
KEYSTORE_PATH=vigilex-release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

`app/google-services.json` is also gitignored. A fresh clone will not build
without both.

---

## 3. Invariants — break these and things fail silently

### 3.1 CameraX binds to the **service** lifecycle, never the Activity

`MonitoringForegroundService` extends `LifecycleService` and calls
`bindToLifecycle(this, ...)`. Monitoring must survive screen-off and app
backgrounding, which is only possible because the Activity isn't the owner.

The UI receives the `Preview` use case through a companion `StateFlow` and
attaches a surface to it. **Never move camera binding into a composable or
ViewModel.** If you do, monitoring dies the moment the driver locks the screen —
and it will look fine in testing, because the screen is on while you test.

### 3.2 The pre-auth gate reads `authorized_phones`, never `users`

The OTP gate runs **before sign-in**, so `request.auth` is null. `users` is
unreadable then, and must stay that way — those documents contain `exitPin`, the
credential gating driver sign-out.

`authorized_phones` is a zero-PII mirror: `allow get: if true`,
`allow list: if false`. Keyed by E.164 with the `+` stripped
(`+919897831882` → doc ID `919897831882`).

> **History.** The gate originally queried `users` by phone. That only worked
> because Firestore was in test mode. The day production rules landed, every
> non-Super-Admin login broke — and reported *"This phone number is not
> authorized"* because `runCatching{}.getOrDefault(false)` swallowed the
> `PERMISSION_DENIED`. Hours were lost to a misleading message.
>
> That's why `isPhoneAuthorized()` **throws instead of defaulting**, and why the
> caller **fails open** on error. Do not "simplify" either back — you'd
> reintroduce the exact bug. The gate only saves SMS cost; the real boundary is
> post-auth in `signInWithCredential()`, which signs out anyone lacking a
> Firestore user doc.

Any new way to create a user **must** also write `authorized_phones`, and
deletion must revoke it *before* deleting the user doc.

### 3.3 Detection constants are field-tuned — don't "improve" them

In `DrowsinessAnalyzer.kt`. Every value below replaced something that produced
real false positives or misses:

| Constant | Value | Why |
|---|---|---|
| `CALIBRATION_WINDOW_MS` | 15 s | Was 60 s — drivers set off before it finished |
| eye threshold | `avg × 0.6`, clamped 0.20–0.45 | Was `× 0.5`; missed narrow-eyed drivers |
| `EYE_CLOSED_DURATION_MS` | 2 s | Below this, normal blinks trigger |
| `HEAD_EULER_Z/Y_DEG` | 25° / 30° | Was 20°/25°; fired on shoulder checks |
| `LATERAL_ACCEL_THRESHOLD_MS2` | 8 m/s² | Was 4 — **normal turns and parking hit 3–5** |
| `LATERAL_SPIKE_DURATION_MS` | 3 s | Was 2 s; brief spikes aren't swerving |
| `EVENT_DEBOUNCE_MS` | 5 s | Re-alerts while eyes stay shut |
| `RECOVERY_OPEN_MS` | 2 s | Eyes open this long ⇒ auto-stop alarm |

The accelerometer values matter most: at the original 4 m/s² / 2 s, users got
alarms while turning into their destination — reported as "alarm goes off near
the destination but not when I'm drowsy". Raising the threshold fixed it.

**There is deliberately no speed gate.** An earlier 20 km/h minimum was removed:
drivers fall asleep at lights and in waiting areas. Don't add one back.

### 3.4 The alarm loops and has exactly two stop paths

`AlertOrchestrator` plays **one looping `MediaPlayer`**, guarded by
`isAlertActive` so repeat detections can't stack overlapping players.

It stops only via:
1. **Auto** — eyes open ≥ 2 s ⇒ analyzer emits `MonitoringStatus.Recovered` ⇒
   service stops it
2. **Manual** — the **Stop Alarm** button ⇒
   `MonitoringForegroundService.instance?.manualStopAlarm()`

Earlier builds fired three fixed bursts with no early stop, so the alarm kept
sounding after the driver was fully awake. If you add a third trigger, wire it
through the same two methods.

**No vibration.** It was removed after testers found it more distracting than
the alarm. Don't re-add it.

### 3.5 Adding a `MonitoringStatus` variant touches the UI

`MonitoringStatus` (bottom of `DrowsinessAnalyzer.kt`) is a sealed class matched
exhaustively in `DriverHomeScreen.MonitoringScreen` for both `borderColor` and
`statusLabel`. Adding a variant without updating both is a compile error — which
is the intent. Don't add an `else` branch to silence it.

---

## 4. Firestore

### Collections

| Collection | Notes |
|---|---|
| `superadmin/config` | Seeded on first Super Admin login |
| `authorized_phones/{phoneNoPlus}` | Pre-auth gate. No PII. Public `get`, `list` denied. |
| `users/{uid}` | Holds **`exitPin`** — never publicly readable |
| `companies/{id}` | Auto-created when Super Admin adds an owner |
| `trips/{id}` | `status`: `active` / `complete` / `high_risk` |
| `events/{id}` | Immutable audit trail |
| `otps/{tripId}` | **Legacy.** Superseded by the exit PIN. `writeOtp`/`validateOtp` still exist in `FirestoreDataSource` but nothing in the UI calls them. Verify before removing. |

### Rules

`firestore.rules` at the repo root is the source of truth. **Editing it does not
deploy it** — push explicitly:

```bash
firebase deploy --only firestore:rules
```

Two subtleties worth preserving:

- The `users` read rule matches the auth token against **both** E.164 and bare
  10-digit stored phones (`'+91' + resource.data.phone`). Console-created docs
  use either format; without both comparisons the `pending_` → real-UID
  migration fails silently.
- There's a `match /{document=**} { allow read, write: if false; }` catch-all.
  New collections need an explicit `match` block or they're inaccessible.

**Never leave Firestore in test mode.** Those rules auto-expire after 30 days
and every client request then fails.

### The `pending_` UID pattern

Owners create drivers before the driver ever logs in, so a doc is written at
`pending_<phoneWithoutPlus>`. On first OTP login, `signInWithCredential()`
looks up by UID, falls back to phone, and copies all fields — **including
`exitPin`** — to the real Firebase Auth UID. Preserve that copy step or drivers
end up unable to sign out.

---

## 5. Environmental traps

These cost real debugging time. None are code bugs.

| Symptom | Cause |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Debug- and release-signed builds can't replace each other. Uninstall first. |
| OTP fails in release but works in debug | The **release** keystore's SHA-1 **and** SHA-256 must both be registered in Firebase Console. Different signing key ⇒ Phone Auth silently fails. |
| Firebase "Save" greyed out on Phone provider | No SHA-1 registered yet. Add fingerprints first, then re-download `google-services.json`. |
| `SMS unable to be sent until this region enabled` | Authentication → Settings → SMS region policy. |
| Browser flashes open before the OTP arrives | Expected — Firebase's invisible reCAPTCHA / Play Integrity check. Not removable. |
| Camera stops on screen-off despite all the wake-lock work | OEM battery killers (MIUI, ColorOS, FuntouchOS, One UI). No code fix; users must whitelist the app. |
| Gradle loopback error | Git Bash. Use PowerShell + `--no-daemon`. |

Also: **the emulator is useless for this app.** Face detection needs a real
front camera.

---

## 6. Deliberately not implemented

Don't treat these as gaps to fill without asking — they're decisions.

- **Gaze / "eyes on road" tracking.** ML Kit provides head pose, not gaze
  direction. `HEAD_DROP` only fires past the angle thresholds, so a driver
  looking down at a phone with open eyes is *not* reliably detected. Real
  attention monitoring needs MediaPipe FaceMesh or a custom model. This has
  already been assumed present more than once — be explicit about it.
- **Speed gate.** Removed on purpose (§3.3).
- **Vibration.** Removed on purpose (§3.4).
- **Yawn / distraction detection.** Not attempted.

---

## 7. Conventions

- Compose only. No XML layouts, no Fragments.
- `StateFlow` in ViewModels; **never `LiveData`**.
- Business logic stays out of composables.
- All Firestore access goes through `FirestoreDataSource` — don't call
  `FirebaseFirestore` directly from a ViewModel or service.
- Hilt for DI: `@Inject` constructors, modules in `core/di/`.
- User-facing strings belong in `res/values/strings.xml`.
- Coroutines use `viewModelScope` / `lifecycleScope` with an explicit
  dispatcher for IO.
- Secrets come from `local.properties` via `BuildConfig`. **Never hardcode a
  key or commit `google-services.json`.**
- Unit tests are expected for new Repository / UseCase logic.

---

## 8. Recipes

**Add a detection signal** — add an `ImpairmentSubtype`; detect it in
`DrowsinessAnalyzer` (guard with `canFireEvent()`); route through
`fireImpairment()` so COMBINED escalation and the alert-state machine still
apply. Don't call `onImpairmentDetected` directly.

**Add a Firestore collection** — add a `match` block to `firestore.rules` (the
catch-all denies it otherwise), add methods to `FirestoreDataSource`, **deploy
the rules**, then use it.

**Add a screen** — route constant in `navigation/Routes.kt`, composable in the
right `feature/` package, wire into `VigileXNavGraph.kt`, ViewModel via
`hiltViewModel()`.

**Change anything in the monitoring pipeline** — test with the screen off and
the app backgrounded. Screen-on testing hides the entire class of bugs this
architecture exists to prevent.

---

## 9. Release checklist

1. Bump `versionCode` **and** `versionName` in `app/build.gradle.kts`.
2. `.\gradlew.bat bundleRelease assembleRelease --no-daemon`
3. Confirm signing and version:
   `apksigner verify --print-certs <apk>` and `aapt2 dump badging <apk>`
4. **Archive `app/build/outputs/mapping/release/mapping.txt`.** Release builds
   are minified; without this file every Play Console crash report is
   unreadable. It changes on every build.
5. Smoke-test the *release* build on a real device — minification problems only
   appear at runtime, never at compile time.
6. Deploy `firestore.rules` if they changed.

See `docs/PLAY_STORE.md` for submission specifics and the outstanding
permission decisions.

---

## 10. Reference docs

| File | Contents |
|---|---|
| `README.md` | Setup from scratch, troubleshooting table |
| `docs/ARCHITECTURE.md` | Data flow, numbered design decisions with rationale |
| `docs/APP_FLOW.md` | Screen-by-screen behaviour per role |
| `docs/VigileX_Architecture_Diagrams.html` | Rendered diagrams — open in a browser |
| `docs/PLAY_STORE.md` | Play submission status, blockers, declarations |
| `firestore.rules` | Security rules (source of truth; deploy separately) |
