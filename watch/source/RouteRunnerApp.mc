import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

class RouteRunnerApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        // Messages that queued while the app was closed are delivered right after this.
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [viewFor(CourseStore.load())];
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var reply = MessageHandler.handle(msg.data);
        var course = reply.course;
        if (course != null) {
            // Stored before the ack goes out, so "stored" is true when the phone hears it.
            CourseStore.save(course);
        }
        if (reply.message != null) {
            Communications.transmit(reply.message, null, new TransmitListener());
        }
        if (course != null) {
            if (Attention has :vibrate) {
                Attention.vibrate([new Attention.VibeProfile(50, 300)]);
            }
            WatchUi.switchToView(viewFor(course), null, WatchUi.SLIDE_IMMEDIATE);
        }
    }

    // The course page, or the no-course screen, from which nothing can be started.
    function viewFor(course as Course?) as WatchUi.View {
        return course != null ? new CourseView(course) : new NoCourseView();
    }
}

// Transmit needs a listener; a lost reply shows on the phone as a missing ack.
class TransmitListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {}

    function onError() as Void {}
}
