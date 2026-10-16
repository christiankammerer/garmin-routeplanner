import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Attention;
import Toybox.Lang;
import Toybox.Position;
import Toybox.Sensor;
import Toybox.System;

// One run along the course: recorded as a normal running activity, with the course tracker
// following each GPS fix. Ready until started; paused after a stop, until saved or discarded.
class RunSession {
    enum State { READY, RECORDING, PAUSED }

    private const LAP_M = 1000;
    private const NOT_AT_START_SHOWN_MS = 5000;

    var state as State = READY;
    var tracker as CourseTracker;
    // The latest GPS fix, also before the start, for drawing where the runner is.
    var lastFix as Position.Info?;
    private var _session as ActivityRecording.Session?;
    private var _nextLapM as Number = LAP_M;
    private var _notAtStartUntil as Number = 0;

    function initialize(course as Course) {
        tracker = new CourseTracker(course);
    }

    // Recording or paused: the run isn't over, so the course can't change.
    function isActive() as Boolean {
        return state != READY;
    }

    function start() as Void {
        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
        _session = ActivityRecording.createSession({
            :name => "Run",
            :sport => Activity.SPORT_RUNNING,
            :subSport => Activity.SUB_SPORT_GENERIC
        });
        _session.start();
        state = RECORDING;
        buzz(200);
    }

    function stop() as Void {
        _session.stop();
        state = PAUSED;
        buzz(200);
    }

    function resume() as Void {
        _session.start();
        state = RECORDING;
        buzz(200);
    }

    function save() as Void {
        _session.save();
        _session = null;
    }

    function discard() as Void {
        _session.discard();
        _session = null;
    }

    function onPosition(info as Position.Info) as Void {
        lastFix = info;
        var position = info.position;
        if (state != RECORDING || position == null || info.accuracy < Position.QUALITY_USABLE) {
            return;
        }
        var degrees = position.toDegrees();
        var events = tracker.addFix((degrees[0] * 1e6).toNumber(), (degrees[1] * 1e6).toNumber());
        if (events.indexOf(:notAtStart) >= 0) {
            _notAtStartUntil = System.getTimer() + NOT_AT_START_SHOWN_MS;
            buzz(500);
        }
    }

    // Called every second: a lap every kilometre, as the runner's own Garmin would.
    function onTick() as Void {
        if (state != RECORDING) {
            return;
        }
        var info = Activity.getActivityInfo();
        var distance = info != null ? info.elapsedDistance : null;
        if (distance != null && distance >= _nextLapM) {
            _session.addLap();
            _nextLapM += LAP_M;
            buzz(300);
        }
    }

    function showsNotAtStart() as Boolean {
        return System.getTimer() < _notAtStartUntil;
    }

    // Heading in radians, while moving fast enough for it to mean anything; null when standing.
    function heading() as Float? {
        var fix = lastFix;
        if (fix == null || fix.speed == null || fix.speed < 1.0 || fix.heading == null) {
            return null;
        }
        return fix.heading;
    }

    private function buzz(ms as Number) as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(75, ms)]);
        }
    }
}
