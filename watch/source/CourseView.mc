import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.WatchUi;

// The course page: the course line, fitted to the screen, north up, and its total distance.
class CourseView extends WatchUi.View {
    private var _course as Course;
    private var _xs as Array<Number> = [];
    private var _ys as Array<Number> = [];

    function initialize(course as Course) {
        View.initialize();
        _course = course;
    }

    // Projects the line once: flat-earth around the course's middle, scaled into a centred box
    // that leaves room for the distance below it on the round screen.
    function onLayout(dc as Dc) as Void {
        var n = _course.pointCount();
        var minLat = _course.latE6(0);
        var maxLat = minLat;
        var minLon = _course.lonE6(0);
        var maxLon = minLon;
        for (var i = 1; i < n; i++) {
            var lat = _course.latE6(i);
            var lon = _course.lonE6(i);
            minLat = lat < minLat ? lat : minLat;
            maxLat = lat > maxLat ? lat : maxLat;
            minLon = lon < minLon ? lon : minLon;
            maxLon = lon > maxLon ? lon : maxLon;
        }
        var midLat = (minLat + maxLat) / 2;
        var midLon = (minLon + maxLon) / 2;
        var lonScale = Math.cos(Math.toRadians(midLat / 1e6));
        var spanX = (maxLon - minLon) * lonScale;
        var spanY = (maxLat - minLat).toFloat();
        var span = spanX > spanY ? spanX : spanY;
        var box = dc.getWidth() * 0.6;
        var scale = span > 0 ? box / span : 0;
        var cx = dc.getWidth() / 2;
        var cy = dc.getHeight() * 0.45;

        _xs = new Array<Number>[n];
        _ys = new Array<Number>[n];
        for (var i = 0; i < n; i++) {
            _xs[i] = (cx + (_course.lonE6(i) - midLon) * lonScale * scale).toNumber();
            _ys[i] = (cy - (_course.latE6(i) - midLat) * scale).toNumber();
        }
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setPenWidth(4);
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        for (var i = 1; i < _xs.size(); i++) {
            dc.drawLine(_xs[i - 1], _ys[i - 1], _xs[i], _ys[i]);
        }
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(_xs[0], _ys[0], 7);

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var km = (_course.totalM / 1000.0).format("%.1f") + " km";
        dc.drawText(dc.getWidth() / 2, dc.getHeight() * 0.8, Graphics.FONT_MEDIUM, km, CENTRED);
    }
}
