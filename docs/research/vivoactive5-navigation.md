# What can the Vivoactive 5 do for course navigation?

Research for [issue #2](https://github.com/christiankammerer/garmin-routeplanner/issues/2), part of the map in [issue #1](https://github.com/christiankammerer/garmin-routeplanner/issues/1). Researched 2026-10-03. Terms follow `GLOSSARY.md` (route, course, off-route alert).

## Answer in brief

- **There are no built-in Courses on the Vivoactive 5.** The current owner's manual (Sept 2025) has no Courses feature. Its navigation chapter covers only saved locations and Back to Start, each with a compass arrow. It has no route line, no map, no off-route alert, no turn prompts and no distance remaining on a course. A Garmin staff member confirmed that the watch "does not support running/cycling courses". None of the later firmware notes we checked (up to 17.01) add the feature. The Vivoactive **6** does have Courses, so the gap is specific to this model.
- **Connect IQ is the only way to get a course onto this watch.** The device runs API level 5.2 and has a 390×390 round AMOLED screen. The APIs we need are all listed as supported on vívoactive 5: `Position`, `ActivityRecording`, `Communications` (phone messages and web requests), `Attention.vibrate`, and `Dc` drawing with anti-aliasing. A device app can draw the route line itself, compute off-route and distance remaining, vibrate, and record a normal running activity that syncs to Garmin Connect. Third-party route apps such as dwMap already take this approach on Vivoactive-class watches.
- **What's not available:** `WatchUi.MapView` (Garmin's map widget) is not supported on vívoactive 5, so there are no basemap tiles. The course is drawn as a polyline on a plain background. `Attention.playTone` is also not supported, so alerts can only vibrate.

## 1. Built-in navigation (native firmware)

| Capability | Vivoactive 5 | Status |
|---|---|---|
| Load a course (GPX/FIT, Garmin Connect "send to device") | **No** | Verified: missing from manual [M]; staff statement [F1] |
| Route line / map display | **No** | Verified: no map or course screens in manual [M] |
| Off-route alert | **No** | Verified: missing from manual [M]. The only alerts listed are training alerts. |
| Turn prompts | **No** | Verified: missing from manual [M] |
| Distance remaining to the end of a course | **No** | Verified: missing from manual [M] |
| Navigate to a saved location | Yes, compass arrow only | Verified [M p.51–52] |
| Back to Start | Yes, compass arrow only, "not available for all activities" | Verified [M p.52] |
| Record a normal GPS run that syncs to Garmin Connect | Yes | Verified [M p.8 "Going for a Run"; syncing section] |
| Add Connect IQ data fields to built-in activities | Yes | Verified [M p.64: "You can add Connect IQ data fields to built-in features and pages"] |

"Courses" in the manual (pp. 9 and 15) refers only to **golf** courses [M].

Firmware: the latest release notes on Garmin's forum (17.01: Messenger pairing and EasyCard icon fixes; 15.05: golf, breathwork and swim fixes) mention no course or navigation features [F2][F3]. The Vivoactive 6 manual does document Navigate > Courses, with a map, an elevation plot and "Do Course in Reverse" [M6]. Garmin added Courses to the 6 and did not backport them to the 5. *Inference:* with v17 out and no change, we should not expect Courses to arrive in a future update.

## 2. Connect IQ fallback

### Device profile

| Item | Value | Source |
|---|---|---|
| Connect IQ API level | 5.2 | Verified [CIQ-dev] |
| Screen | 390×390, round, AMOLED | Verified [CIQ-dev] |
| Memory, device app (`watchApp`) | 786,432 B (768 KB) | Secondary: SDK device-file aggregation [MEM] |
| Memory, data field | 262,144 B (256 KB) | Secondary [MEM] |
| Memory, background process | 65,536 B (64 KB) | Secondary [MEM] |

The memory figures come from a community tool that reads the SDK's device definitions. The authoritative source is the SDK `compiler.json` and the simulator's memory view, which we could not open without the SDK installed. Check these numbers in the simulator.

### APIs relevant to course navigation (vívoactive 5 support per API docs)

| API | What it gives us | VA5 | App types |
|---|---|---|---|
| `Toybox.Position.enableLocationEvents(LOCATION_CONTINUOUS, …)` | Continuous GPS fixes. Needs the Positioning permission. API 1.0.0 | Yes [API-Pos] | Device apps and widgets only |
| `Activity.Info.currentLocation`, `elapsedDistance`, `currentHeading` | Position, distance and heading inside an activity, including in data fields via `compute(info)` | Yes [API-Info] | All, incl. data fields |
| `Activity.Info.offCourseDistance`, `distanceToDestination`, `distanceToNextPoint` | Filled by the native course engine. *Inference:* always null on VA5 because there are no native courses, so the app must compute these itself. | n/a | |
| `Toybox.ActivityRecording.createSession({:sport, :subSport, :name})` → `start/stop/save` | Writes a real FIT activity (e.g. sport running) that the watch syncs to Garmin Connect like a native run. API 1.0.0. | Yes [API-AR] | Device apps (not data fields) |
| `Graphics.Dc.drawLine`, `fillPolygon` (max 64 points), `setAntiAlias` (API 3.2) | Drawing the course as a polyline: segment by segment with `drawLine`, no point limit | Yes [API-Dc] | All |
| `WatchUi.MapView` / `MapTrackView` (API 3.0) | Garmin's map rendering with a polyline overlay | **No**: VA5 not listed [API-Map] | |
| `Attention.vibrate` | Vibration for off-route and turn alerts | Yes [API-Att] | Incl. data fields |
| `Attention.playTone` | Beep | **No**: VA5 not listed [API-Att] | |
| `Communications.registerForPhoneAppMessages` / `transmit` | Receive the course from the companion Android app over Bluetooth (via the Connect IQ Android SDK + Garmin Connect Mobile) | Yes [API-Comm][ANDROID] | Many, incl. data fields |
| `Communications.makeWebRequest` (JSON) | Fetch the course from an online URL | Yes [API-Comm] | |
| `makeWebRequest` with `HTTP_RESPONSE_CONTENT_TYPE_FIT/GPX` | Asks the system to "download and parse a FIT or GPX file and store the contained data in the device" | *Unverified on VA5.* With no native course store this probably does not give a usable course. Not something to rely on. | |
| `Toybox.PersistedContent` (getCourses/getRoutes) | Read courses stored on the device | VA5 listed [API-PC]. *Inference:* the list is empty because the firmware has no courses. | |

### What the two Connect IQ app types can do

**Device app (watch app). This is the strongest option.**
- Starts GPS with `Position`, records a running session with `ActivityRecording` (synced to Garmin Connect as a run), and draws its own screens: a course line plus the current position and heading, and data pages (time, pace, HR, distance, distance remaining).
- Off-route alert: the app computes the distance from the current fix to the nearest course segment and vibrates past a threshold. Turn prompts work the same way, with turn points computed on the phone and sent along with the course. Distance remaining = course length − distance along the course to the nearest matched point. Garmin provides none of this logic. We write it in Monkey C.
- Gets the course from the Android app through the Connect IQ Android SDK (`com.garmin.connectiq:ciq-companion-app-sdk`), which needs the Garmin Connect app installed on the phone [ANDROID].
- Budget: about 768 KB of heap. A ~10 km course simplified to a few hundred points fits easily. *Inference:* store points as compact numeric arrays, not objects.
- Prior art: dwMap is marketed as route maps "for Venu, Vivoactive, Fenix or Forerunner" and says it records through the watch's built-in activity recorder with Garmin Connect sync. Breadcrumb is a similar app [DW][BC][F4]. We could not read the store pages' compatible-device lists, so whether they support the VA5 *specifically* is unverified. They only show that the approach is established on comparable watches.
- Cost: the run is recorded by our app, not the built-in Run activity. Native run features such as the built-in data screens and Auto Pause have to be rebuilt or done without. *Inference*, to be checked against the `createSession` options.

**Data field inside the built-in Run activity. Possible but tighter.**
- Keeps the native Run recording and Garmin Connect sync as they are, and can draw a full-screen course line if set as a single-field page. It reads position through `Activity.Info.currentLocation` and can vibrate.
- Limits: 256 KB of memory, no `Position` events (it relies on `compute()` roughly once per second), and no `ActivityRecording`. The course still has to arrive through phone messages, app settings or a web request. *Inference:* message delivery to a data field is listed in the API, but in practice it is less reliable than to a device app. Validate in a smoke test.

## Implications for the map

- "Built-in navigation vs custom Connect IQ app" is settled by the hardware: on the Vivoactive 5 it **has to be Connect IQ**. Our course delivery cannot use Garmin Connect's course sync.
- Everything the map wants (route line, off-route alert, cheap turn prompts, standard stats + distance remaining, a normal run in Garmin Connect) is possible with a Connect IQ **device app** plus a companion channel from the Android app. Alerts can only vibrate, and there is no basemap.
- Choosing between a device app and a data field, and how the phone delivers the course, are open decisions for a later ticket. A sideload smoke test (simulator + real watch) is recommended before the spec is final.

## Sources

- [M] Garmin, *vívoactive 5 Owner's Manual* (EN-US, PDF dated 2025-09-16): https://www8.garmin.com/manuals/webhelp/GUID-5D183A14-BB43-4A9B-B441-5F824214CE40/EN-US/vivoactive_5_OM_EN-US.pdf (Navigation pp. 51–52; Connect IQ p. 64; Golf p. 9)
- [M6] Garmin, *vívoactive 6 Owner's Manual: Following a Course on Your Device*: https://www8.garmin.com/manuals-apac/webhelp/vivoactive6/EN-SG/GUID-8967F8E5-0A0B-459B-AA38-B2BBF247DBF0-736.html
- [CIQ-dev] Garmin Developers, Connect IQ compatible devices: https://developer.garmin.com/connect-iq/compatible-devices/
- [API-Pos] https://developer.garmin.com/connect-iq/api-docs/Toybox/Position.html
- [API-Info] https://developer.garmin.com/connect-iq/api-docs/Toybox/Activity/Info.html
- [API-AR] https://developer.garmin.com/connect-iq/api-docs/Toybox/ActivityRecording.html
- [API-Dc] https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics/Dc.html
- [API-Map] https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/MapView.html
- [API-Att] https://developer.garmin.com/connect-iq/api-docs/Toybox/Attention.html
- [API-Comm] https://developer.garmin.com/connect-iq/api-docs/Toybox/Communications.html
- [API-PC] https://developer.garmin.com/connect-iq/api-docs/Toybox/PersistedContent.html
- [ANDROID] Garmin, Connect IQ Android SDK: https://github.com/garmin/connectiq-android-sdk
- [MEM] (secondary) flocsy/garmin-dev-tools, memory limits per device extracted from SDK device files: https://github.com/flocsy/garmin-dev-tools/tree/main/csv
- [F1] (secondary) Garmin Forums, "navigating with Vivoactive 5" (Garmin staff reply): https://forums.garmin.com/sports-fitness/healthandwellness/f/vivoactive-5-series/361989/navigating-with-vivoactive-5
- [F2] (secondary) Garmin Forums, vivoactive 5 Software Version 17.01: https://forums.garmin.com/sports-fitness/healthandwellness/f/vivoactive-5-series/425991/vivoactive-5-software-version---17-01
- [F3] (secondary) Garmin Forums, vivoactive 5 Software Version 15.05: https://forums.garmin.com/sports-fitness/healthandwellness/f/vivoactive-5-series/419039/vivoactive-5-software-version---15-05
- [F4] (secondary) Garmin Forums showcase, "dwMap - Route Maps for Vivoactive and other watches": https://forums.garmin.com/developer/connect-iq/f/showcase/1578/application-dwmap---route-maps-for-vivoactive-and-other-watches/11944
- [DW] (secondary) dwMap on the Connect IQ Store: https://apps.garmin.com/en-US/apps/2750f280-82f4-4f21-a32c-57acc7ce4870
- [BC] (secondary) Breadcrumb Trail Navigation App: https://apps.garmin.com/apps/40e128d2-db98-41d1-b5a9-624a725e6e68
