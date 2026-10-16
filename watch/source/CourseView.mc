import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;
import Toybox.WatchUi;

// The course page: the course line with where the runner is. Zoom step 0 fits the whole course,
// north up; the others follow the runner, heading up while moving. A run starts at RUN_ZOOM. Before the start it shows the
// course's total distance; during the run, distance remaining and pace.
class CourseView extends WatchUi.View {
    static const ZOOM_STEPS = 4;
    // The step a run starts at: following the runner, 1 km across.
    static const RUN_ZOOM = 2;
    // Metres across the line's box at each following zoom step (step 0 fits the course).
    private const ZOOM_WIDTHS_M = [0, 2000, 1000, 500];

    private var _plane as CoursePlane?;
    private var _planeOf as Course?;

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var app = routeRunner();
        var course = app.course as Course;
        var run = app.run as RunSession;
        if (_planeOf != course) {
            _plane = new CoursePlane(course);
            _planeOf = course;
        }
        var plane = _plane as CoursePlane;

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var w = dc.getWidth();
        var h = dc.getHeight();
        var here = runnerOnPlane(run, plane);
        var frame = app.zoom == 0 || here == null ? fitFrame(plane, w) : followFrame(here, run.heading(), app.zoom, w);
        drawCourse(dc, plane, frame, w / 2, h / 2);
        if (here != null) {
            var spot = toScreen(here[0], here[1], frame, w / 2, h / 2);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(spot[0], spot[1], 9);
            dc.setColor(Graphics.COLOR_ORANGE, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(spot[0], spot[1], 6);
        }

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        if (run.state == RunSession.READY) {
            var status = run.hasUsableFix() ? "Press start" : "Finding GPS…";
            dc.drawText(w / 2, h * 0.13, Graphics.FONT_XTINY, status, CENTRED);
            dc.drawText(w / 2, h * 0.85, Graphics.FONT_MEDIUM, RunFormat.km(course.totalM), CENTRED);
        } else {
            dc.drawText(w / 2, h * 0.15, Graphics.FONT_MEDIUM, RunFormat.km(run.distanceRemainingM()), CENTRED);
            dc.drawText(w / 2, h * 0.85, Graphics.FONT_SMALL, RunFormat.pace(run.speed()), CENTRED);
        }
        if (run.state == RunSession.PAUSED) {
            banner(dc, "Paused", Graphics.COLOR_DK_GRAY);
        } else if (run.showsNotAtStart()) {
            banner(dc, "Not at start", Graphics.COLOR_RED);
        }
    }

    // Where the runner is on the course's plane, once GPS has a position.
    private function runnerOnPlane(run as RunSession, plane as CoursePlane) as [Float, Float]? {
        var fix = run.lastFix;
        if (fix == null || fix.position == null) {
            return null;
        }
        var e6 = toE6(fix.position as Position.Location);
        return plane.toPlane(e6[0], e6[1]);
    }

    // [centre x, centre y, metres to pixels, rotation in radians].
    private function fitFrame(plane as CoursePlane, w as Number) as Array<Float> {
        var minX = plane.xs[0];
        var maxX = minX;
        var minY = plane.ys[0];
        var maxY = minY;
        for (var i = 1; i < plane.xs.size(); i++) {
            minX = plane.xs[i] < minX ? plane.xs[i] : minX;
            maxX = plane.xs[i] > maxX ? plane.xs[i] : maxX;
            minY = plane.ys[i] < minY ? plane.ys[i] : minY;
            maxY = plane.ys[i] > maxY ? plane.ys[i] : maxY;
        }
        var span = maxX - minX > maxY - minY ? maxX - minX : maxY - minY;
        var scale = span > 0 ? w * 0.6 / span : 1.0;
        return [(minX + maxX) / 2, (minY + maxY) / 2, scale, 0.0];
    }

    private function followFrame(here as [Float, Float], heading as Float?, zoom as Number, w as Number) as Array<Float> {
        var scale = w * 0.6 / (ZOOM_WIDTHS_M[zoom] as Number);
        return [here[0], here[1], scale.toFloat(), heading != null ? heading : 0.0];
    }

    private function drawCourse(dc as Dc, plane as CoursePlane, frame as Array<Float>, cx as Number, cy as Number) as Void {
        dc.setPenWidth(5);
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        var last = toScreen(plane.xs[0], plane.ys[0], frame, cx, cy);
        var start = last;
        for (var i = 1; i < plane.xs.size(); i++) {
            var next = toScreen(plane.xs[i], plane.ys[i], frame, cx, cy);
            dc.drawLine(last[0], last[1], next[0], next[1]);
            last = next;
        }
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(start[0], start[1], 7);
    }

    // Plane metres to screen pixels. Heading-up turns the plane so the runner's heading points up.
    private function toScreen(x as Float, y as Float, frame as Array<Float>, cx as Number, cy as Number) as [Number, Number] {
        var dx = x - frame[0];
        var dy = y - frame[1];
        var angle = frame[3];
        var rx = dx * Math.cos(angle) - dy * Math.sin(angle);
        var ry = dx * Math.sin(angle) + dy * Math.cos(angle);
        return [(cx + rx * frame[2]).toNumber(), (cy - ry * frame[2]).toNumber()];
    }

    private function banner(dc as Dc, text as String, colour as Graphics.ColorType) as Void {
        var w = dc.getWidth();
        var y = dc.getHeight() * 0.32;
        dc.setColor(colour, colour);
        dc.fillRectangle(0, y - 24, w, 48);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, y, Graphics.FONT_SMALL, text, CENTRED);
    }
}
