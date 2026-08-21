# VigileX — Privacy Policy

**Last updated:** 21 August 2026

> **⚠️ Review before publishing.** This is a drafted starting point, not legal
> advice. It makes factual claims about data handling that you are accountable
> for. Verify every statement matches your actual deployment, replace the
> placeholders below, and have it reviewed if VigileX is used commercially or
> processes data of EU/UK residents (GDPR) or Indian residents (DPDP Act 2023).
>
> Placeholders to replace: `[COMPANY NAME]`, `[CONTACT EMAIL]`,
> `[JURISDICTION]`, `[RETENTION PERIOD]`.

---

## 1. Who we are

VigileX is a commercial fleet driver-safety application operated by
**[COMPANY NAME]**. It is provided to transport operators, who enrol their own
drivers. VigileX is not available for individual public sign-up.

Contact for privacy matters: **[CONTACT EMAIL]**

---

## 2. Summary

- The front camera analyses the driver's face **on the device only**. Camera
  images are **never recorded, stored, uploaded, or transmitted.**
- We collect the driver's name, phone number, trip locations, and safety events.
- Your employer (the fleet operator) can see your trips, locations, and safety
  events.
- We do not sell your data or use it for advertising.

---

## 3. Camera and face analysis

This is the most sensitive area, so we describe it precisely.

While a monitoring session is active, VigileX processes frames from the
front-facing camera using **Google ML Kit Face Detection**, which runs entirely
on the device with no network connection.

From each frame the app derives only:

- whether the eyes are open or closed (a probability value)
- the angle of the head

**What happens to the images:** each frame is analysed in memory and discarded
immediately. No frame is written to storage, and none is transmitted off the
device. There is no video recording capability in the app.

**What leaves the device:** only derived safety events — for example
*"eye closure detected, high severity, 14:32, at these coordinates."* Never an
image.

We do not create facial recognition templates, do not attempt to identify people
from faces, and do not use face data for any purpose besides real-time
alertness detection.

---

## 4. What we collect

| Data | Why | Source |
|---|---|---|
| Name | Identify the driver to their operator | Entered by the fleet operator |
| Phone number | Authentication (SMS OTP) and account identity | Entered by the operator; verified by you at login |
| Email (optional) | Account administration | Entered by the operator |
| Precise location | Record the trip route; detect arrival at the destination; tag where a safety event occurred | Device GPS during an active session |
| Motion / accelerometer readings | Detect erratic vehicle movement | Device sensors, processed in real time |
| Safety events | Alert the operator; produce trip safety history | Derived on-device |
| Device push token | Deliver notifications | Firebase Cloud Messaging |

**Location is sampled only while a monitoring session is active** — roughly every
30 seconds. It is not collected when you are signed out or when no session is
running.

We do **not** collect: contacts, messages, call logs, browsing history,
installed apps, audio recordings, photos, or files.

**The microphone is never used to record.** The app requests microphone
permission solely because Android requires it to route the safety alarm through
a Bluetooth car audio system. There is no audio capture in VigileX.

---

## 5. Who can see your data

**Your employer (the fleet operator)** can see your name, phone number, assigned
trips, live and historical location during trips, and all safety events. This is
the purpose of the product, and enrolment by your employer constitutes the basis
for it. If you are a driver with questions about how your employer uses this
information, contact them directly.

**Service providers.** Data is stored using **Google Firebase**
(Authentication, Cloud Firestore, Cloud Messaging) and **Google Maps Platform**
for maps and address search. Their handling is governed by Google's privacy
policy: https://policies.google.com/privacy

**We do not** sell your data, share it with data brokers, or use it for
advertising or profiling unrelated to driver safety.

We may disclose data where legally required, or where necessary to protect
someone's safety.

---

## 6. Storage, retention, security

Data is stored in Google Cloud infrastructure and encrypted in transit (TLS) and
at rest. Access is restricted by role: a fleet operator can only reach their own
company's records, enforced by server-side security rules.

Safety events and trip history are retained for **[RETENTION PERIOD]** to allow
operators to review patterns over time, then deleted. Account records are kept
while your enrolment is active.

If the device has no network, events are queued in encrypted local storage and
synced when connectivity returns.

No system is perfectly secure, and we cannot guarantee absolute security.

---

## 7. Your rights

Depending on where you live, you may have the right to access, correct, delete,
or export your data, to object to processing, or to withdraw consent.

Because drivers are enrolled by their employer, **the fastest route is usually
your fleet operator**, who administers your account and can delete it. You may
also contact us at **[CONTACT EMAIL]** and we will respond within 30 days.

Uninstalling the app stops all collection immediately. It does not delete
records already held by your fleet operator.

---

## 8. Permissions and why they exist

| Permission | Purpose |
|---|---|
| Camera | On-device drowsiness detection. No images stored or sent. |
| Location (incl. background) | Trip route and arrival detection while monitoring runs, including with the screen off |
| Notifications | Show the monitoring status and deliver safety alerts |
| Bluetooth | Play the alarm through the vehicle's audio system |
| Microphone | **Never recorded.** Required by Android only to route alarm audio over Bluetooth |
| Run at startup | Resume monitoring after a device restart mid-trip |

You can revoke any permission in Android settings. Revoking camera or location
will stop safety monitoring from working.

---

## 9. Children

VigileX is for licensed commercial drivers and is not directed at anyone under
18. We do not knowingly collect data from children.

---

## 10. Important safety limitation

**VigileX is a driver assistance aid, not a safety guarantee.** It detects
behavioural indicators of drowsiness and impairment. It can miss genuine
impairment — for example when a driver wears sunglasses, in poor lighting, or at
an unusual camera angle — and it can produce false alerts.

It is **not** a medical device, **not** a substitute for legal sobriety testing,
and **not** a licence to drive while tired. It does not detect where a driver is
looking, and cannot tell whether attention is on the road.

Drivers remain fully responsible for their own fitness to drive.

---

## 11. International transfers

Data may be processed on Google Cloud servers outside your country. Where
required, transfers rely on appropriate safeguards such as the European
Commission's Standard Contractual Clauses.

---

## 12. Changes

We will update the date at the top when this policy changes and, for material
changes, notify fleet operators. Continued use after an update constitutes
acceptance.

---

## 13. Contact

**[COMPANY NAME]**
**[CONTACT EMAIL]**

Governing law: **[JURISDICTION]**
