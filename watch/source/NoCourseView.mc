import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Text centred on its point, both ways.
const CENTRED = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;

// Shown while the watch holds no course. It has no start: a run without a course isn't offered.
class NoCourseView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var cx = dc.getWidth() / 2;
        var cy = dc.getHeight() / 2;
        dc.drawText(cx, cy - 30, Graphics.FONT_MEDIUM, "No course", CENTRED);
        dc.drawText(cx, cy + 20, Graphics.FONT_SMALL, "Send one from", CENTRED);
        dc.drawText(cx, cy + 55, Graphics.FONT_SMALL, "your phone", CENTRED);
    }
}
