import Toybox.Lang;
import Toybox.WatchUi;

// The buttons and screen on the course and stats pages, as on the runner's own Garmin:
// the action button starts and stops; while paused, it resumes and Back opens Save / Discard.
// Swiping up or down changes page; tapping the course page steps its zoom.
// An InputDelegate, not a BehaviorDelegate: on the Vivoactive 5 a BehaviorDelegate turns a screen
// tap into the same onSelect as the action button, so a stray tap would stop the run.
class RunDelegate extends WatchUi.InputDelegate {
    private var _onStats as Boolean;

    function initialize() {
        InputDelegate.initialize();
        _onStats = false;
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();
        if (key == WatchUi.KEY_ENTER) {
            return onAction();
        }
        if (key == WatchUi.KEY_ESC) {
            return onBack();
        }
        return false;
    }

    function onSwipe(swipeEvent as WatchUi.SwipeEvent) as Boolean {
        var direction = swipeEvent.getDirection();
        if (direction == WatchUi.SWIPE_UP) {
            return showPage(!_onStats, WatchUi.SLIDE_UP);
        }
        if (direction == WatchUi.SWIPE_DOWN) {
            return showPage(!_onStats, WatchUi.SLIDE_DOWN);
        }
        return false;
    }

    function onTap(clickEvent as WatchUi.ClickEvent) as Boolean {
        if (!_onStats) {
            var app = routeRunner();
            app.zoom = (app.zoom + 1) % CourseView.ZOOM_STEPS;
            WatchUi.requestUpdate();
        }
        return true;
    }

    private function onAction() as Boolean {
        var run = routeRunner().run as RunSession;
        if (run.state == RunSession.READY) {
            routeRunner().zoom = CourseView.RUN_ZOOM;
            run.start();
        } else if (run.state == RunSession.RECORDING) {
            run.stop();
            pushStopMenu();
        } else {
            run.resume();
        }
        WatchUi.requestUpdate();
        return true;
    }

    private function onBack() as Boolean {
        var run = routeRunner().run as RunSession;
        if (run.state == RunSession.PAUSED) {
            pushStopMenu();
            return true;
        }
        // Back mustn't leave the app mid-run.
        return run.state == RunSession.RECORDING;
    }

    private function showPage(stats as Boolean, transition as WatchUi.SlideType) as Boolean {
        _onStats = stats;
        WatchUi.switchToView(stats ? new StatsView() : new CourseView(), self, transition);
        return true;
    }

    private function pushStopMenu() as Void {
        var menu = new WatchUi.Menu2({:title => "Paused"});
        menu.addItem(new WatchUi.MenuItem("Resume", null, :resume, null));
        menu.addItem(new WatchUi.MenuItem("Save", null, :save, null));
        menu.addItem(new WatchUi.MenuItem("Discard", null, :discard, null));
        WatchUi.pushView(menu, new StopMenuDelegate(), WatchUi.SLIDE_UP);
    }
}

class StopMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var app = routeRunner();
        var run = app.run as RunSession;
        var id = item.getId();
        if (id == :resume) {
            run.resume();
            WatchUi.popView(WatchUi.SLIDE_DOWN);
        } else if (id == :save) {
            run.save();
            app.finishRun();
            WatchUi.popView(WatchUi.SLIDE_DOWN);
            if (WatchUi has :showToast) {
                WatchUi.showToast("Run saved", null);
            }
        } else if (id == :discard) {
            var confirm = new WatchUi.Menu2({:title => "Discard run?"});
            confirm.addItem(new WatchUi.MenuItem("Discard", null, :yes, null));
            confirm.addItem(new WatchUi.MenuItem("Keep", null, :no, null));
            WatchUi.pushView(confirm, new DiscardMenuDelegate(), WatchUi.SLIDE_LEFT);
        }
    }
}

class DiscardMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        if (item.getId() == :yes) {
            var app = routeRunner();
            (app.run as RunSession).discard();
            app.finishRun();
            // Both menus go: back to the course page, ready for the next run.
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            WatchUi.popView(WatchUi.SLIDE_DOWN);
        } else {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        }
    }
}
