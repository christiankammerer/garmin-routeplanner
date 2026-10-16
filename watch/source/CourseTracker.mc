import Toybox.Lang;
import Toybox.Math;

// Follows the runner along a course from successive position fixes. Pure: no UI or sensor calls.
// Progress only moves forward, and a fix only matches the course a short way ahead of the last
// match, so a later pass over the same ground (out and back, a figure 8) can't make it jump.
class CourseTracker {
    // Far enough ahead for a fix every few seconds, short of the next pass over the same ground.
    private const WINDOW_M = 250;
    // A fix further than this from the course doesn't move progress.
    private const MATCH_M = 50;
    // A first fix further than this from the course start gets a "Not at start" warning.
    private const NOT_AT_START_M = 100;

    private var _course as Course;
    private var _plane as CoursePlane;
    private var _xs as Array<Float>;
    private var _ys as Array<Float>;
    private var _alongM as Float = 0.0;
    private var _segment as Number = 0;
    private var _started as Boolean = false;

    function initialize(course as Course) {
        _course = course;
        _plane = new CoursePlane(course);
        _xs = _plane.xs;
        _ys = _plane.ys;
    }

    // Takes one position fix and returns its events: :notAtStart for a first fix far from the start.
    function addFix(latE6 as Number, lonE6 as Number) as Array<Symbol> {
        var xy = _plane.toPlane(latE6, lonE6);
        var x = xy[0];
        var y = xy[1];
        if (!_started) {
            _started = true;
            // Near the start, progress starts at 0, even on a loop whose end is just as near.
            if (Math.sqrt(x * x + y * y) <= NOT_AT_START_M) {
                return [];
            }
            var nearest = closestMatch(x, y, 0, _xs.size() - 1);
            _segment = nearest[1] as Number;
            _alongM = nearest[2] as Float;
            return [:notAtStart];
        }
        var last = _segment;
        while (last < _xs.size() - 1 && _course.cumM(last) <= _alongM + WINDOW_M) {
            last++;
        }
        var match = closestMatch(x, y, _segment, last);
        if ((match[0] as Float) <= MATCH_M && (match[2] as Float) > _alongM) {
            _segment = match[1] as Number;
            _alongM = match[2] as Float;
        }
        return [];
    }

    // How far the runner still has to go along the course to its end.
    function distanceRemainingM() as Number {
        var remaining = _course.totalM - Math.round(_alongM).toNumber();
        return remaining > 0 ? remaining : 0;
    }

    // The point on segments [from, to) closest to (x, y): [distance m, segment, metres along the course].
    private function closestMatch(x as Float, y as Float, from as Number, to as Number) as Array {
        var best = [1e9, from, _alongM];
        for (var i = from; i < to; i++) {
            var ax = _xs[i];
            var ay = _ys[i];
            var dx = _xs[i + 1] - ax;
            var dy = _ys[i + 1] - ay;
            var len2 = dx * dx + dy * dy;
            var t = len2 > 0 ? ((x - ax) * dx + (y - ay) * dy) / len2 : 0.0;
            t = t < 0 ? 0.0 : (t > 1 ? 1.0 : t);
            var ex = ax + t * dx - x;
            var ey = ay + t * dy - y;
            var d = Math.sqrt(ex * ex + ey * ey);
            if (d < best[0]) {
                var along = _course.cumM(i) + t * (_course.cumM(i + 1) - _course.cumM(i));
                best = [d, i, along.toFloat()];
            }
        }
        return best;
    }
}
