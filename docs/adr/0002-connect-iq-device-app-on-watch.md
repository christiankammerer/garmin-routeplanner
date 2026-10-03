# The watch side is our own Connect IQ device app, not Garmin courses or a data field

Courses reach the Vivoactive 5 as messages from our Android app (Connect IQ Mobile SDK, via Garmin Connect Mobile) to our own Connect IQ device app. The device app stores one course, draws the route line, and handles off-route alerts, turn prompts and distance remaining itself. It records the run with `ActivityRecording`, so the run lands in Garmin Connect as a normal run. The Vivoactive 5 has no built-in Courses (the Vivoactive 6 does), so every path that relies on Garmin's native course store is closed to us.

## Considered Options

- **GPX/FIT import into Garmin Connect, synced as a course**: the obvious route, but the Vivoactive 5 cannot load or navigate courses at all.
- **Garmin Connect Courses API**: business-only developer programme. It also targets the same native course store the watch lacks.
- **Connect IQ data field inside the built-in Run activity**: Garmin would keep handling stats, laps and recording. But the route line is cramped in a field slot, memory is 256 KB, and a data field can't receive phone messages in the foreground, so delivery would depend on an unverified 64 KB background service.

## Consequences

- We build and maintain the run screens (course page, stats page, laps, start/stop/save) that the built-in activity would otherwise give us.
- The runner installs our watch app once (sideload now, Connect IQ Store if published) and needs Garmin Connect Mobile on the phone.
- The phone prepares each course (simplified polyline, cumulative distances, turn points from BRouter's turn hints), so the watch app stays small. Message size limits are unverified until the smoke test.
