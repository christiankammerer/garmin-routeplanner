import Toybox.Activity;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.WatchUi;

// The stats page: time, distance, pace, heart rate and distance remaining.
class StatsView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var run = routeRunner().run as RunSession;
        var info = Activity.getActivityInfo();
        var timerMs = info != null && info.timerTime != null ? info.timerTime as Number : 0;
        var distanceM = info != null && info.elapsedDistance != null ? (info.elapsedDistance as Float).toNumber() : 0;
        var heartRate = info != null ? info.currentHeartRate : null;

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        var rows = [
            ["Time", RunFormat.duration(timerMs)],
            ["Distance", RunFormat.km(distanceM)],
            ["Pace", RunFormat.pace(run.speed())],
            ["Heart rate", heartRate != null ? heartRate.toString() : "--"],
            ["Remaining", RunFormat.km(run.distanceRemainingM())]
        ];
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        for (var i = 0; i < rows.size(); i++) {
            var y = h * (0.24 + i * 0.13);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx - 6, y, Graphics.FONT_XTINY, rows[i][0], Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx + 6, y, fontToFit(dc, rows[i][1], y), rows[i][1], Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        if (run.state == RunSession.PAUSED) {
            dc.setColor(Graphics.COLOR_ORANGE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.07, Graphics.FONT_XTINY, "Paused", CENTRED);
        }
    }

    // The value's font: small, or tiny where the round screen is too narrow at this height.
    private function fontToFit(dc as Dc, text as String, y as Float) as Graphics.FontType {
        var r = dc.getWidth() / 2;
        var dy = y - dc.getHeight() / 2;
        var room = Math.sqrt(r * r - dy * dy) - 6 - 10;
        return dc.getTextWidthInPixels(text, Graphics.FONT_SMALL) <= room ? Graphics.FONT_SMALL : Graphics.FONT_TINY;
    }
}
