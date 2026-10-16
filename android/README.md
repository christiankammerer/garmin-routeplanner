# Route Planner (Android)

Kotlin + Jetpack Compose. The map is MapLibre Compose with the OpenFreeMap `liberty` style; routes come from the separate [BRouter](https://github.com/abrensch/brouter) app over AIDL (ADR 0001). Courses go to the Route Runner watch app (`../watch`) through the Connect IQ Mobile SDK, which needs Garmin Connect on the phone (ADR 0002).

## Build and test

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # route generator, course builder, delivery and request screen, against fakes
```

`connectedDebugAndroidTest` runs `BRouterEngineDeviceTest` against the real BRouter app on a connected phone. It needs BRouter installed with the segment for central Stockholm (`E15_N55`), or another start passed with `-Pandroid.testInstrumentationRunnerArguments.start=<lat>,<lon>`. It checks that BRouter accepts our profile (`app/src/main/assets/running.brf`, sent as `remoteProfile`) and that a parameter sent through `extraParams` changes the result.

## BRouter notes

- `extraParams` must be a URL-style string (`low_incline=1&…`), not the Bundle that BRouter's AIDL comment describes, and each value must be a number.
- `engineMode` must be put as an int; a round trip's single start goes in `lonlats`.
- A missing segment answers `datafile E10_N55.rd5 not found`.
