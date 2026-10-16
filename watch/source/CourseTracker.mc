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
    // After this many fixes in a row with no match ahead (a GPS gap, a cut corner), look further.
    private const LOST_FIXES = 5;

    private var _course as Course;
    private var _plane as CoursePlane;
    private var _xs as Array<Float>;
    private var _ys as Array<Float>;
    private var _alongM as Float = 0.0;
    private var _segment as Number = 0;
    private var _started as Boolean = false;
    private var _unmatched as Number = 0;

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
        if ((match[0] as Float) <= MATCH_M) {
            _unmatched = 0;
            moveTo(match);
        } else {
            _unmatched += 1;
            var caughtUp = _unmatched >= LOST_FIXES ? firstPassAhead(x, y) : null;
            if (caughtUp != null) {
                _unmatched = 0;
                moveTo(caughtUp);
            }
        }
        return [];
    }

    // Moves progress to [match], forward only.
    private function moveTo(match as Array) as Void {
        if ((match[2] as Float) > _alongM) {
            _segment = match[1] as Number;
            _alongM = match[2] as Float;
        }
    }

    // Where the runner is, searching the rest of the course: the closest point on the first stretch
    // that passes within reach. The first pass, not the closest, so an out-and-back section's way
    // back can't win over its way out. Null when no part of the rest of the course is within reach.
    private function firstPassAhead(x as Float, y as Float) as Array? {
        var n = _xs.size();
        var first = _segment;
        while (first < n - 1 && (closestMatch(x, y, first, first + 1)[0] as Float) > MATCH_M) {
            first++;
        }
        if (first >= n - 1) {
            return null;
        }
        var end = first + 1;
        while (end < n - 1 && (closestMatch(x, y, end, end + 1)[0] as Float) <= MATCH_M) {
            end++;
        }
        return closestMatch(x, y, first, end);
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
