# Play Store submission — status, blockers, and draft declarations

Last updated for **versionCode 11 / versionName 1.1.1**.

---

## Status

| | |
|---|---|
| ✅ Signed AAB produced | `app/build/outputs/bundle/release/app-release.aab` |
| ✅ Minified + resource-shrunk | R8 enabled, rules in `app/proguard-rules.pro` |
| ✅ `targetSdk 35` | Meets Play's current requirement |
| ✅ 64-bit support | `arm64-v8a` included |
| ✅ Removed `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Was declared but never used in code |
| ✅ Removed `VIBRATE` | Outlived the vibration feature it existed for |
| ✅ `BLUETOOTH_SCAN` marked `neverForLocation` | Avoids extra location-policy review |
| ⚠️ Release build not yet smoke-tested | R8 failures surface only at runtime — **do this before submitting** |
| ❌ Privacy policy not hosted | **Blocker** — see §2 |
| ❌ Data safety form not filled | **Blocker** — draft answers in §4 |
| ❌ Sensitive-permission declarations not submitted | **Blocker** — draft text in §3 |
| ⚠️ Two open permission decisions | See §5 — these are yours to make |

**Recommendation: submit to a closed testing track first** (up to 100 testers).
Review is far lighter than production, and it exercises the whole pipeline
before you commit to a production submission that can be rejected.

---

## 1. Your action checklist

Nothing below can be produced from the codebase. Tick these off as you go — this
is the live list, so update it in place.

### Accounts & hosting

- [ ] **Play Console account** — $25 one-time. Register as an **Organisation** if
      commercial. New *personal* accounts must run a 12-tester / 14-day closed
      test before production is unlocked; Organisation accounts skip that.
- [ ] **Host the privacy policy** → note the public URL here: `________`
      - Source: `docs/PRIVACY_POLICY.md`. GitHub Pages is sufficient.
      - Replace first: `[COMPANY NAME]`, `[CONTACT EMAIL]`, `[JURISDICTION]`,
        `[RETENTION PERIOD]`.
      - Then **read it through** — it asserts facts about your data handling that
        you are accountable for.
- [ ] **Decide the retention period** for trip and event history → `________`
      (needed by both the policy and the data-safety form)
- [ ] **Public contact email** for the listing → `________`

### Store assets

- [ ] App icon — 512×512 PNG, 32-bit
- [ ] Feature graphic — 1024×500 PNG/JPEG
- [ ] Screenshots — ≥ 2, 16:9 or 9:16, min 320px.
      Suggested: driver monitoring screen (green border, mid-session) and the
      owner live map.
- [ ] Short description — ≤ 80 chars
- [ ] Full description — ≤ 4000 chars.
      **Mirror the in-app safety disclaimer here** — "driver assistance aid, not
      a safety guarantee, not a medical device". Impairment detection edges into
      health claims, and stating the limitation up front pre-empts the question.

### Console forms

- [ ] Content rating questionnaire
- [ ] Data safety form — draft answers in §4, copy them across
- [ ] Sensitive permission declarations — paste-ready text in §3
- [ ] **Background-location demo video** — mandatory while
      `ACCESS_BACKGROUND_LOCATION` is declared. Unlisted YouTube is fine. Must
      show the feature working *and* the in-app disclosure.
      → **Possibly avoidable entirely — settle §5.1 before recording this.**

### Decisions to settle (see §5)

- [ ] §5.1 — drop `ACCESS_BACKGROUND_LOCATION` by replacing the Geofencing API
      with a distance check? *(recommended — removes the highest-rejection-rate
      permission and the video requirement)*
- [ ] §5.2 — keep, drop, or rework Bluetooth SCO to shed `RECORD_AUDIO`?
- [ ] Default the camera preview to collapsed? *(see §7 — one-line change,
      strengthens the driver-distraction position)*

### Before each upload

- [ ] Bump `versionCode` **and** `versionName`
- [ ] Smoke-test the **release** build on a real device (minification failures
      only appear at runtime)
- [ ] Confirm the release keystore SHA-1 **and** SHA-256 are registered in
      Firebase, or Phone Auth fails silently
- [ ] Upload `mapping.txt` alongside the AAB

---

## 2. Privacy policy

Mandatory — the app handles camera imagery of faces, precise location, and phone
numbers.

`docs/PRIVACY_POLICY.md` is drafted and ready. **Review it before publishing** —
it makes factual claims about your data handling that you are accountable for.

The strongest compliance point, which must be stated prominently and is
genuinely true here: **camera frames are analysed on-device by ML Kit and are
never stored, uploaded, or transmitted.** Only derived events (type, severity,
timestamp, coordinates) reach Firestore.

Host it and put the URL in Console → Policy → App content → Privacy policy.

---

## 3. Sensitive permission declarations (draft text)

Paste into Console → Policy → App content → Sensitive app permissions. Edit to
match reality if any of it drifts.

### `ACCESS_BACKGROUND_LOCATION`

> VigileX is a commercial fleet driver-safety application. Location access is
> required while the app is in the background because monitoring must continue
> for the entire duration of a driving trip, including when the driver's screen
> is off.
>
> Background location is used for two features, both core to the product:
>
> 1. **Trip route recording** — the fleet operator must see where an impairment
>    event occurred in order to investigate it. Each detected event is tagged
>    with coordinates.
> 2. **Geofenced arrival detection** — the trip is automatically completed and
>    monitoring stopped when the driver reaches the assigned destination
>    (200 m radius). Without background location the trip would continue
>    recording after arrival.
>
> Location is sampled every 30 seconds only while an active monitoring session
> is running, and never when the driver is signed out. Drivers are added to the
> system by their employer and see an in-app disclosure explaining that trip
> location is recorded before monitoring begins.

### `FOREGROUND_SERVICE_CAMERA`

> The front camera is used to detect driver drowsiness via on-device face
> analysis (Google ML Kit). This must run in a foreground service because
> monitoring has to continue when the driver's screen is off — a drowsy driver
> may lock the screen, and detection cannot lapse at that moment.
>
> A persistent notification is shown for the entire monitoring session. Camera
> frames are processed entirely on-device and are never recorded, stored, or
> transmitted. No image data leaves the device.

### `RECORD_AUDIO`

> The microphone is never recorded from. This permission is required only
> because `AudioManager.startBluetoothSco()` — used to route the safety alarm
> through the vehicle's Bluetooth audio system so the driver hears it over road
> noise — requires it on Android.
>
> The app contains no audio capture code path. See §5 for an alternative under
> consideration.

### `FOREGROUND_SERVICE_LOCATION`

> Required to sample location during an active trip for route recording and
> geofenced arrival detection, as described in the background location
> declaration above.

---

## 4. Data safety form (draft answers)

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Yes** |
| Is data encrypted in transit? | **Yes** (Firebase TLS) |
| Can users request deletion? | **Yes** — via their fleet operator; state the contact route |

**Data types to declare:**

| Type | Collected | Shared | Purpose | Required? |
|---|---|---|---|---|
| Phone number | Yes | No | Account management, authentication | Required |
| Name | Yes | No | Account management | Required |
| Email (optional) | Yes | No | Account management | Optional |
| Precise location | Yes | No | App functionality (trip tracking, arrival detection) | Required |
| Other app activity (impairment events) | Yes | No | App functionality, safety monitoring | Required |

**Camera / photos: declare as NOT collected.** Frames are processed on-device in
real time and never stored or transmitted. Play's definition of "collected" is
transmission off-device, which does not happen here. Be ready to explain this if
challenged — it's a defensible and accurate position, and the on-device
architecture is what makes it true.

⚠️ Do **not** declare face data as collected biometrics — but do **not** omit
mentioning face analysis in the privacy policy either. The policy must describe
what the camera does even though nothing is retained.

---

## 5. Open decisions — yours to make

### 5.1 `ACCESS_BACKGROUND_LOCATION` — could be removed entirely

This is the **highest-rejection-rate permission on Play** and requires the video
demo. It may not be necessary.

Android grants background location implicitly to a foreground service with the
`location` type — which VigileX already has. The permission is needed only
because of the **Geofencing API**, which requires it on API 29+.

Arrival detection could instead be computed in `MonitoringForegroundService`,
which already receives location every 30 seconds — a simple distance check
against the destination.

| | Geofencing API (current) | Distance check in FGS |
|---|---|---|
| Play review burden | Declaration **+ video demo** | **None** |
| Trigger latency | Near-instant | Up to 30 s |
| Battery | OS-optimised | Marginally worse |
| Works when app killed | Yes | No — but the FGS runs all trip anyway |

**My recommendation: switch to the distance check.** Up to 30 seconds of extra
recording after arrival is immaterial for this product, and it removes the single
largest submission risk. Say the word and I'll implement it.

### 5.2 `RECORD_AUDIO` — justify or drop Bluetooth SCO

A microphone permission in a driving app invites scrutiny, and reviewers
sometimes reject rather than read the justification.

Options:
1. **Keep SCO + declare** (current). Honest, but a rejection risk.
2. **Drop SCO**, play the alarm on `STREAM_ALARM` only. Removes `RECORD_AUDIO`
   entirely. Risk: `STREAM_ALARM` deliberately favours the phone speaker over
   Bluetooth A2DP on many devices, so the "alarm through car speakers" feature
   may regress — which was the reason SCO exists.
3. **Use `STREAM_MUSIC` when Bluetooth is connected** (routes to A2DP, no mic
   permission), falling back to `STREAM_ALARM` on the speaker. Keeps the
   feature, drops the permission — but needs real-device testing across
   headunits, and `STREAM_MUSIC` respects the media volume the user set, which
   could mean a quieter alarm.

I'd try option 3, but it needs testing in an actual vehicle. **Not something to
change blind before a submission.**

---

## 6. Submission steps

1. Host the privacy policy; note the URL.
2. Create the app in Play Console (Organisation account if commercial).
3. Upload `app/build/outputs/bundle/release/app-release.aab`.
4. **Upload `app/build/outputs/mapping/release/mapping.txt`** — without it every
   crash report is unreadable, because release builds are minified.
5. Complete: store listing, content rating, data safety (§4), privacy policy URL.
6. Submit the sensitive-permission declarations (§3) and the background-location
   video.
7. Start with **closed testing**. Promote to production once stable.

---

## 7. Realistic expectations

- Closed testing review: **a few days**.
- Production review with background location: **1–3 weeks**, and a first-attempt
  rejection is common. Rejections state a policy section; they're usually
  fixable rather than fatal.
- If §5.1 is implemented first, the timeline shortens considerably.

**One more thing worth thinking about beyond policy:** the app shows a live
camera preview and holds the screen on while the vehicle is moving. A reviewer
may raise driver distraction. The collapsible preview helps — consider
defaulting it to **collapsed** so the app is unobtrusive unless the driver opts
in. That's a one-line change in `DriverHomeScreen` (`isPreviewExpanded` initial
value) and it strengthens the submission.
