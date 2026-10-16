# Route Runner (watch)

The Connect IQ device app for the Vivoactive 5 (ADR 0002). It receives a course from the phone app, keeps it in `Storage`, and shows it on the course page.

## Build and sideload

```
monkeyc -f monkey.jungle -d vivoactive5 -y <developer_key> -o RouteRunner.prg -r
```

Copy `RouteRunner.prg` to `GARMIN\APPS` on the watch (connected by USB). Route Runner then appears in the watch's app list. The phone app finds it by the id in `manifest.xml`, which must match `WATCH_APP_ID` in `android/…/watch/ConnectIqWatchLink.kt`.

## Unit tests

The tests in `test/` run in the simulator. `test.jungle` adds them, along with the sample course messages from `../contract/`, on top of `monkey.jungle`:

```
monkeyc -f "monkey.jungle;test.jungle" -d vivoactive5 -y <developer_key> -o RouteRunner-test.prg -t
connectiq                       # start the simulator
monkeydo RouteRunner-test.prg vivoactive5 /t
```
