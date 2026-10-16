import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Position;
import Toybox.Timer;
import Toybox.WatchUi;

class RouteRunnerApp extends Application.AppBase {
    var course as Course?;
    var run as RunSession?;
    // The course page's zoom step (see CourseView); kept here so it survives switching pages.
    var zoom as Number = 0;
    private var _ticks as Timer.Timer?;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        // Messages that queued while the app was closed are delivered right after this.
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        var stored = CourseStore.load();
        if (stored != null) {
            useCourse(stored);
        }
    }

    function onStop(state as Dictionary?) as Void {
        // Leaving mid-run keeps the run rather than losing it.
        var current = run;
        if (current != null && current.isActive()) {
            if (current.state == RunSession.RECORDING) {
                current.stop();
            }
            current.save();
        }
        Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
        var ticks = _ticks;
        if (ticks != null) {
            ticks.stop();
        }
    }

    // The course page, or the no-course screen, from which nothing can be started.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return course != null ? [new CourseView(), new RunDelegate()] : [new NoCourseView()];
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var current = run;
        var reply = MessageHandler.handle(msg.data, current != null && current.isActive());
        var received = reply.course;
        if (received != null) {
            // Stored before the ack goes out, so "stored" is true when the phone hears it.
            CourseStore.save(received);
        }
        if (reply.message != null) {
            Communications.transmit(reply.message, null, new TransmitListener());
        }
        if (received != null) {
            useCourse(received);
            buzz(300);
            WatchUi.switchToView(new CourseView(), new RunDelegate(), WatchUi.SLIDE_IMMEDIATE);
        }
    }

    // Ready to run [newCourse]. GPS goes on now, so it has a fix by the time the runner presses start.
    function useCourse(newCourse as Course) as Void {
        var first = course == null;
        course = newCourse;
        run = new RunSession(newCourse);
        zoom = 0;
        if (first) {
            Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
            var ticks = new Timer.Timer();
            ticks.start(method(:onTick), 1000, true);
            _ticks = ticks;
        }
    }

    // After a save or discard: the same course, ready for the next run.
    function finishRun() as Void {
        run = (run as RunSession).next(course as Course);
    }

    function onPosition(info as Position.Info) as Void {
        var current = run;
        if (current != null) {
            current.onPosition(info);
        }
        WatchUi.requestUpdate();
    }

    function onTick() as Void {
        var current = run;
        if (current != null) {
            current.onTick();
        }
        WatchUi.requestUpdate();
    }
}

function buzz(ms as Number) as Void {
    if (Attention has :vibrate) {
        Attention.vibrate([new Attention.VibeProfile(75, ms)]);
    }
}

function routeRunner() as RouteRunnerApp {
    return Application.getApp() as RouteRunnerApp;
}

// Transmit needs a listener; a lost reply shows on the phone as a missing ack.
class TransmitListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {}

    function onError() as Void {}
}
