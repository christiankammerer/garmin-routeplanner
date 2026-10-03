# Smoke test: phone-to-watch course delivery

Throwaway verification for [Smoke-test phone-to-watch course delivery on Pixel 8 + Vivoactive 5](https://github.com/christiankammerer/garmin-routeplanner/issues/11). **This is not the start of the build.** Delete this directory once the numbers are recorded on the ticket.

- `watch/`: a Connect IQ device app ("Course Smoke") for the Vivoactive 5. It receives probe messages, checks them, tries to store them in `Storage` as the real app would store its one course, and sends an acknowledgement back.
- `android/`: a one-screen Android app ("Course Smoke") with buttons for each measurement and a log you can copy.

The Android app builds (`./gradlew assembleDebug`). The watch app has **not been compiled yet**, because the Vivoactive 5 device files need a Garmin login. Expect to fix a typo or two the first time you build it.

## 1. One-time setup (Windows)

Open the repo from Windows at `\\wsl$\<distro>\home\ckamm\projects\garmin-routeplanner` (Ubuntu is the usual distro name), or clone the `task/smoke-test` branch.

**Watch toolchain**
1. Install [VS Code](https://code.visualstudio.com/) and the **Monkey C** extension (by Garmin).
2. Install the [Connect IQ SDK Manager](https://developer.garmin.com/connect-iq/sdk/). Sign in with a Garmin account, download the latest SDK and set it active, then on the *Devices* tab download **vívoactive 5**.
3. In VS Code, run `Monkey C: Verify Installation` from the command palette. When asked, let it generate a **developer key**. Keep the key file: every sideloaded build is signed with it.

**Phone toolchain**
4. Install [Android Studio](https://developer.android.com/studio). Open `smoke-test/android`. Let it sync; it downloads Gradle and the SDK by itself.
5. On the Pixel 8, enable *Developer options* (tap *Build number* seven times), then turn on *USB debugging*. Connect it by USB and accept the prompt.

**Apps on the phone**
6. Install **Garmin Connect** and make sure the Vivoactive 5 is paired and connected.
7. Install **BRouter** (Google Play or F-Droid). Open it once, accept the default folder, and download the segment tile(s) that cover your running area (Stockholm is `E15_N55`; tiles are 5°×5°, named after the south-west corner).

## 2. Install both apps

**Watch:** in VS Code with `smoke-test/watch` open, run `Monkey C: Build for Device` and choose **vivoactive5**. This produces a `.prg` file. Connect the watch by USB. It shows up in Explorer as *vívoactive 5*. Copy the `.prg` into `GARMIN\APPS`, then unplug. "Course Smoke" appears in the watch's app list.

**Phone:** in Android Studio, press *Run* with the Pixel selected. The "Course Smoke" app opens.

In the phone app, tap **Connect**, then **App info**. It should say `installed`. Use the lat/lon fields to set a start point near home, inside the BRouter tile you downloaded.

## 3. Measurements

Run each test, then use **Copy log** and paste the log into the ticket. Each log line has a timestamp. `send=` is the result of the SDK's send callback. `ack=` is the full round trip until the watch's reply arrived.

### Q1: Message size

Open Course Smoke on the watch, so it shows *waiting for phone*.

1. Tap **Sweep int**. The app sends synthetic 10 km courses of 100, 250, 500, 750, 1000, 1500, 2000 and 3000 points, encoded as flat ints (`latE6, lonE6, cumulative metres`). It stops after two failures in a row. For each size, note `send=` status, ack time, `sum ok`, `stored=`, and the `mem=` line.
2. Tap **Sweep dbl**. Same sweep, but sent as doubles. `first sent=… watch=…` shows whether doubles survive or get cut to 32-bit floats.
3. If a size fails, check the watch screen: did the message arrive at all (the `msgs` counter), or did the app crash (it closes)?

### Q2: Delivery when the watch app is closed

Repeat each scenario about 5 times. Note what the watch does and what the log says.

- **a. Send while closed.** Leave the watch on its watch face and tap **Send 500**. Note `send=` and whether an ack arrives (it times out after 60 s). Then open Course Smoke on the watch by hand. Does the queued message arrive? It shows up as an *Unmatched message* line if it comes after the phone gave up waiting, and a small `watchMsSinceStart` means it was delivered from the queue.
- **b. Open, then send.** With the watch on its watch face, tap **Open app**. Note the status, for example `PROMPT_SHOWN_ON_DEVICE`, and the time. Accept the prompt on the watch, then tap **Send 500**.
- **c. Prompt declined or ignored.** Tap **Open app**, then decline, or ignore the prompt until it disappears. Note the status and what the watch shows.
- **d. Busy watch.** Start a built-in activity on the watch (any run), then tap **Open app**. What happens?

### Q3: Acknowledgement round trip

With Course Smoke open on the watch, tap **Ping x10** three times: once with the phone next to the watch, once with the phone in a pocket or a few metres away, and once right after **Send 500**. Note the min, median and max.

### Q4: BRouter calibration speed

1. Set km to `10` and leave the profile blank (BRouter's default foot profile). Tap **3 seq** twice: the first run is a cold start. Note the TOTAL line and when each CANDIDATE was ready (`ready at +… ms`).
2. Tap **3 par** once. Does running the three in parallel help, or does it fail?
3. Set the profile to `hiking-mountain` and repeat step 1. That profile is closer to the running profile we'll write.
4. Optionally repeat with 5 km and 15 km.
5. Tap **Send route**. It logs how many points the real route has, before and after simplification at 2, 5 and 10 m, and sends the 5 m version to the watch. This shows whether a real 10 km route fits in the ≤500-point budget.

## 4. Results (fill in, or paste the copied logs into the ticket)

| Question | Result |
|---|---|
| Q1 largest int payload that arrives intact (points / values / time) | |
| Q1 does it store in `Storage`? memory used at 500 points | |
| Q1 doubles: precision kept? largest size | |
| Q2a send while closed: status, queued and delivered on open? | |
| Q2b openApplication: status, time to prompt, delivered after accept? | |
| Q2c declined / ignored prompt | |
| Q2d during a built-in activity | |
| Q3 ack round trip min / median / max | |
| Q4 10 km, 3 candidates: total time (cold / warm), time to first | |
| Q4 tries per candidate, any outside ±5% | |
| Q4 real 10 km route: points raw → 5 m simplified | |
