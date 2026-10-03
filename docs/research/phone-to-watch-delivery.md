# How can an Android app get a course onto the Vivoactive 5 without a server?

Research for [issue #3](https://github.com/christiankammerer/garmin-routeplanner/issues/3), part of the map in [issue #1](https://github.com/christiankammerer/garmin-routeplanner/issues/1). Researched 2026-10-03. Terms follow `GLOSSARY.md`: the phone holds a **route**, and the watch holds a **course**.

Each claim is marked **Verified** (read in a primary source, cited) or **Inference** (my reasoning from verified facts, not confirmed).

## Answer in brief

- **Every path that relies on the native course store fails on the Vivoactive 5, because the watch has no native courses.** The owner's manual's Navigation chapter covers only saved locations, Back to Start and the compass [M]. In the Connect IQ API docs, `PersistedContent.getCourses()` / `getAppCourses()` list vívoactive 6 as a supported device but **not vívoactive 5** [API-PC]. This rules out GPX/FIT import through the Garmin Connect app, the Garmin Connect Courses API, and a Connect IQ app that downloads a FIT/GPX course (the gimporter approach). None of them can produce a navigable native course on this watch.
- **The Courses API is closed to us in any case.** The Garmin Connect Developer Program is "only for business use" [GCDP-FAQ].
- **The one workable path is our own Connect IQ device app on the watch, paired with the Android app through the Connect IQ Mobile SDK.** The phone sends the route's points with `sendMessage()`. The watch app receives them with `registerForPhoneAppMessages`, keeps them as its own course, draws the line, computes off-route and distance remaining, and records the run with `ActivityRecording`. Every API this needs lists vívoactive 5 as supported [ANDROID][API-Comm][API-AR]. The path is free, needs no server, needs no approval for personal sideloading, and can later be published on Play Store plus the Connect IQ Store (free apps, review usually within 72 h) [PUB][AGR].
- **Runner's manual steps on that path:** install the watch app once (sideload over USB, or install from the store later) and keep Garmin Connect Mobile installed and paired. To send a course, pick a candidate route on the phone and tap Send. The watch app must be installed. It probably needs to be open, or opened from the phone with `openApplication()`, for the message to arrive promptly (Inference).

## Paths compared

| Path | Hobbyist access | Approval / cost | Runner's manual steps | Reliability | Publishable later | Navigable course on VA5? |
|---|---|---|---|---|---|---|
| A. Share GPX/FIT to the Garmin Connect app, then sync | Yes | None | Share file, then "send to device" in Garmin Connect | Forum reports describe it working for supported watches [F-GPX] | Yes | **No.** VA5 has no native courses [M][API-PC][DCR] |
| B. Garmin Connect Courses API (push to account, then auto-sync) | **No**, "only for business use" [GCDP-FAQ] | Application plus approval; free for business [GCDP-FAQ] | None after OAuth | n/a | Business only | **No.** Same native-course gap (Inference from [M][API-PC]) |
| C. Connect IQ app downloads FIT/GPX via `makeWebRequest(..., HTTP_RESPONSE_CONTENT_TYPE_FIT)` from a localhost server on the phone (gimporter/gexporter) or a free online host | Yes | None | Open watch app, pick file | Known Android bug: FIT over localhost fails, "Acknowledged" since 2022 [BUG-LH] | Yes | **No.** The system stores it as a native course, and `getCourses()` is not supported on VA5 [API-PC]. gimporter's manifest lists vivoactive6 but not vivoactive5 [GIMP] |
| **D. Own Connect IQ device app plus Mobile SDK `sendMessage`** | **Yes** | **None.** SDK free; free store listing [AGR][PUB] | Install watch app once; tap Send on the phone | Official, documented channel. Size and timing limits are undocumented and must be tested (see Open questions) | **Yes.** Play Store plus Connect IQ Store | **Yes, as an app-owned course** that our app draws and navigates. It is not a native course. |
| E. Own Connect IQ app fetches the route as JSON from a free online service with `makeWebRequest` | Yes | Depends on the service | As D | Depends on a third-party service and phone internet. HTTPS required except localhost [API-Comm] | Yes | Yes (as D), but adds a dependency for no gain over D (Inference) |

## Details per path

### A. GPX/FIT import via the Garmin Connect app

- Forum users report that a `.gpx` opened with or shared to Garmin Connect on Android is imported as a Course [F-GPX]. I found no Garmin support article that confirms this, so it rests on community evidence.
- Irrelevant for VA5 either way. **Verified:** the VA5 manual has no course feature; "Courses" in it refers only to golf [M]. DC Rainmaker's VA5 review: "the Vivoactive/Venu series doesn't support preplanned routes/courses" [DCR]. A Garmin forum thread asking for course upload on VA5 got no Garmin plan [F-VA5]. The Vivoactive 6 manual does have "Following a Course" [M6].

### B. Garmin Connect Developer Program, Courses API

- **Verified:** "Courses API allows you to publish courses to Garmin Connect. Once published, these courses will be available for users to sync with their compatible Garmin devices." [CAPI]
- **Verified:** "There are no licensing or maintenance fees for access to the Garmin Connect Developer Program, but it is only for business use." The program "is available for enterprise use". Applications are answered within two business days [GCDP-FAQ].
- Also conflicts with the no-server constraint: the program uses OAuth 2.0 [GCDP-FAQ], and a confidential client secret would normally sit on a server (Inference). It also depends on "compatible Garmin devices", which excludes VA5.

### C. Connect IQ app imports a FIT/GPX file as a native course

- **Verified:** with `:responseType => HTTP_RESPONSE_CONTENT_TYPE_FIT` or `_GPX`, "the system will attempt to download and parse a FIT or GPX file and store the contained data in the device" [API-Comm]. The content is then reached through `PersistedContent` and launched into a native app with `System.exitTo(content.toIntent())` [DL].
- **Verified:** the `PersistedContent` module lists vívoactive 5, but the **function-level** lists for `getCourses()` and `getAppCourses()` include vívoactive 6 and **not** vívoactive 5. `getWaypoints()` and `getWorkouts()` do include VA5 [API-PC]. So a downloaded course has nowhere to land on VA5.
- **Verified:** the open-source gimporter (watch) + gexporter (Android) pair does exactly path C. It serves files from an HTTP server on the phone's localhost and imports them as native courses [GIMP]. Its manifest includes `vivoactive6` but not `vivoactive5` [GIMP].
- **Verified:** Connect IQ allows plain HTTP only to localhost (otherwise `SECURE_CONNECTION_REQUIRED`, -1001) [API-Comm]. A Connect IQ bug report says Garmin Connect Mobile on Android fails FIT downloads from 127.0.0.1 while JSON works. It has been "Acknowledged" since July 2022, and the suggested workaround is to have Wi‑Fi or mobile data on [BUG-LH]. gimporter's own guide tells users the opposite, to switch Wi‑Fi off [GIMP]. Either way, this channel is fragile.

### D. Own Connect IQ device app + Connect IQ Mobile SDK for Android (recommended)

Phone side (Kotlin):
- **Verified:** the SDK is on Maven Central as `com.garmin.connectiq:ciq-companion-app-sdk` (2.2.0 at time of writing). The sample repo is Apache-2.0 [ANDROID][GH-SDK].
- **Verified:** "the user must also install Garmin Connect Mobile onto their phone. All communication … goes through a Garmin Connect Mobile service". Initialisation fails without it, and the SDK can show an install or upgrade prompt itself [ANDROID].
- **Verified:** the API covers `getKnownDevices()`/`getConnectedDevices()`, device status events, `getApplicationInfo()` (installed or not, version), `openApplication()` (prompts the user on the watch to open the app), `openStore()`, and `sendMessage(device, app, message, listener)`, which delivers to the watch app's mailbox with a status callback. Payloads are `List`/`Map` of ints, floats, doubles, booleans, Strings [ANDROID].
- **Verified:** a `TETHERED`/ADB mode lets the phone app talk to the Connect IQ simulator for testing [ANDROID][COMM].

Watch side (Monkey C):
- **Verified:** VA5 runs Connect IQ API level 5.2 [CIQ-DEV]. `Communications.registerForPhoneAppMessages` lists vívoactive 5. The callback "will immediately be called once for each waiting message" when registered, so messages queue while the app is closed [API-Comm]. `transmit` (watch to phone, e.g. an acknowledgement) also lists VA5 [API-Comm].
- **Verified:** `ActivityRecording` lists vívoactive 5 [API-AR]. A recorded FIT session "will sync with Garmin Connect" [ARC]. So the run saves as a normal run.
- Drawing the course, off-route alerts and distance remaining all have to be computed by our app. Issue #2's research covers the device-side APIs.
- **Verified:** sideloading for personal use: "Build for Device", then copy the PRG to `GARMIN/APPS` over USB [YFA]. The SDK licence is free to use "for the sole purpose of developing and testing Applications". Fees apply only to the Merchant Service for paid apps [AGR].
- **Verified, for later publishing:** upload the `.iq` to the Connect IQ Store. Reviews "are completed within 72 hours". While review is pending you can download your own app for testing [PUB]. GDPR obligations apply if personal data is collected [PUB].
- Inference: the course lives inside the watch app's storage, not the firmware's course list. Resending or saving routes is our app's job, which fits the "saved route, resend" flow on the phone.

### E. Watch app fetches JSON from a free online service

Technically possible: `makeWebRequest` JSON is proxied through the phone, and HTTPS is required [API-Comm]. Inference: it adds an internet dependency and a third-party data store compared with D, and gains nothing, since the phone already holds the route.

## Open questions (to settle with a sideload smoke test)

- **Message size and speed.** Neither the Mobile SDK docs nor the Communications docs give a maximum `sendMessage` payload or a BLE throughput figure. A 10 km route can be thousands of points. Expect to simplify the route (e.g. Douglas–Peucker to a few hundred points) and/or split it into several messages with sequence numbers. This needs measuring.
- **Delivery when the watch app is closed.** The docs say waiting messages are delivered on registration [API-Comm]. Whether Garmin Connect Mobile reliably queues messages for a closed app on current firmware is untested. `openApplication()` is the documented way to prompt the runner to open it [ANDROID].
- **Watch app memory** (secondary source in issue #2: about 768 KB for a device app). It caps how many course points can be held, and needs checking in the simulator.

## Sources

- [M] vívoactive 5 Owner's Manual (Garmin): https://www8.garmin.com/manuals/webhelp/GUID-5D183A14-BB43-4A9B-B441-5F824214CE40/EN-US/vivoactive_5_OM_EN-US.pdf and the Navigation page https://www8.garmin.com/manuals-apac/webhelp/vivoactive5/EN-SG/GUID-D79732F0-3A6D-4A69-B576-A68B11212DBC-9188.html
- [M6] vívoactive 6 Owner's Manual, "Following a Course on Your Device": https://www8.garmin.com/manuals/webhelp/GUID-8C2C402F-55AC-431F-9CF2-1442B89CE149/EN-US/GUID-BB11B4D8-4EB8-4D8E-97EC-AA88529B0747.html
- [API-PC] Connect IQ API, Toybox.PersistedContent (per-function supported devices): https://developer.garmin.com/connect-iq/api-docs/Toybox/PersistedContent.html
- [API-Comm] Connect IQ API, Toybox.Communications: https://developer.garmin.com/connect-iq/api-docs/Toybox/Communications.html
- [API-AR] Connect IQ API, Toybox.ActivityRecording: https://developer.garmin.com/connect-iq/api-docs/Toybox/ActivityRecording.html
- [ARC] Connect IQ Core Topics, Activity Recording: https://developer.garmin.com/connect-iq/core-topics/activity-recording/
- [DL] Connect IQ Core Topics, Downloading Content: https://developer.garmin.com/connect-iq/core-topics/downloading-content/
- [COMM] Connect IQ Core Topics, Communicating with Mobile Apps: https://developer.garmin.com/connect-iq/core-topics/communicating-with-mobile-apps/
- [ANDROID] Connect IQ Core Topics, Mobile SDK for Android: https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/
- [GH-SDK] garmin/connectiq-android-sdk (official samples, Apache-2.0): https://github.com/garmin/connectiq-android-sdk
- [CIQ-DEV] Connect IQ Compatible Devices (vívoactive 5: 390×390, API 5.2): https://developer.garmin.com/connect-iq/compatible-devices/
- [YFA] Connect IQ Basics, Your First App, "Side Loading an App": https://developer.garmin.com/connect-iq/connect-iq-basics/your-first-app/
- [PUB] Connect IQ Core Topics, Publishing to the Store: https://developer.garmin.com/connect-iq/core-topics/publishing-to-the-store/
- [AGR] Connect IQ SDK License Agreement: https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html
- [CAPI] Garmin Connect Developer Program, Courses API: https://developer.garmin.com/gc-developer-program/courses-api/
- [GCDP-FAQ] Garmin Connect Developer Program FAQ: https://developer.garmin.com/gc-developer-program/program-faq/
- [GIMP] gimporter (watch) and gexporter (Android), open source: https://github.com/gimportexportdevs/gimporter (manifest-app.xml, docs/USER_GUIDE.md), https://github.com/gimportexportdevs/gexporter
- [BUG-LH] Connect IQ bug report, FIT download from localhost on Android: https://forums.garmin.com/developer/connect-iq/i/bug-reports/garmin-connect-mobile-on-android-unable-to-handle-makewebrequest-comm-http_response_content_type_fit-on-127-0-01-localhost
- [DCR] DC Rainmaker, Vivoactive 5 review (secondary, corroborating): https://www.dcrainmaker.com/2023/09/garmin-vivoactive-display.html
- [F-VA5] Garmin forum, "Is there any plan to make uploading courses to the vivoactive 5 possible?" (community): https://forums.garmin.com/sports-fitness/healthandwellness/f/vivoactive-5-series/379814/is-there-any-plan-to-make-uploading-courses-to-the-vivoactive-5-possible
- [F-GPX] Garmin forum, "Importing GPX files into Garmin Connect" (community): https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/254284/importing-gpx-files-into-garmin-connect
