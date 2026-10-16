import Toybox.Lang;
import Toybox.Math;
import Toybox.Test;

// The course tracker, driven by made-up position fixes around a made-up course.
// Positions are in metres east and north of central Stockholm.

const ORIGIN_LAT_E6 = 59329300;
const ORIGIN_LON_E6 = 18068600;

// A course through [x, y] corners in metres, with a point every 20 m as a real course has,
// and its cumulative metres measured along them.
function courseThrough(corners as Array<[Float, Float]>) as Course {
    var start = fixAt(corners[0][0], corners[0][1]);
    var points = [start[0], start[1], 0] as Array<Number>;
    var cum = 0.0;
    for (var i = 1; i < corners.size(); i++) {
        var dx = corners[i][0] - corners[i - 1][0];
        var dy = corners[i][1] - corners[i - 1][1];
        var length = Math.sqrt(dx * dx + dy * dy);
        var steps = Math.ceil(length / 20).toNumber();
        for (var k = 1; k <= steps; k++) {
            var f = k.toFloat() / steps;
            var fix = fixAt(corners[i - 1][0] + dx * f, corners[i - 1][1] + dy * f);
            points.addAll([fix[0], fix[1], Math.round(cum + length * f).toNumber()]);
        }
        cum += length;
    }
    return new Course("test", 1, points[points.size() - 1], points);
}

// [latE6, lonE6] for a point [x, y] metres from the origin.
function fixAt(x as Float, y as Float) as [Number, Number] {
    var lonScale = Math.cos(Math.toRadians(ORIGIN_LAT_E6 / 1e6));
    return [
        ORIGIN_LAT_E6 + Math.round(y / 0.111195).toNumber(),
        ORIGIN_LON_E6 + Math.round(x / (0.111195 * lonScale)).toNumber()
    ];
}

// Feeds a fix at [x, y] and returns the tracker's events for it.
function runTo(tracker as CourseTracker, x as Float, y as Float) as Array<Symbol> {
    var fix = fixAt(x, y);
    return tracker.addFix(fix[0], fix[1]);
}

function assertNear(expected as Numeric, actual as Numeric, tolerance as Numeric, what as String) as Void {
    var off = (actual - expected).abs();
    Test.assertMessage(off <= tolerance, what + ": expected " + expected + ", got " + actual);
}

(:test)
function distanceRemainingCountsDownAlongTheCourse(logger as Logger) as Boolean {
    // An L: 1 km east, then 1 km north.
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [1000.0, 1000.0]]));

    Test.assertEqual(0, runTo(tracker, 0.0, 0.0).size());
    assertNear(2000, tracker.distanceRemainingM(), 2, "at the start");
    for (var x = 10.0; x <= 1000.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }
    assertNear(1000, tracker.distanceRemainingM(), 2, "at the corner");
    for (var y = 10.0; y <= 600.0; y += 10.0) {
        runTo(tracker, 1000.0, y);
    }
    assertNear(400, tracker.distanceRemainingM(), 2, "600 m up the second leg");
    return true;
}

(:test)
function anOutAndBackSectionDoesNotJumpAhead(logger as Logger) as Boolean {
    // 1 km east and back the same way: every place on the way out is also on the way back.
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [0.0, 0.0]]));

    for (var x = 0.0; x <= 500.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }
    assertNear(1500, tracker.distanceRemainingM(), 2, "half way out");

    for (var x = 510.0; x <= 1000.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }
    for (var x = 990.0; x >= 500.0; x -= 10.0) {
        runTo(tracker, x, 0.0);
    }
    assertNear(500, tracker.distanceRemainingM(), 2, "half way back");
    return true;
}

(:test)
function aLateStartWarnsAndPicksUpAtTheNearestPoint(logger as Logger) as Boolean {
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [1000.0, 1000.0]]));

    // 300 m from the course start, 20 m beside the course 300 m along it.
    var events = runTo(tracker, 300.0, 20.0);

    Test.assertEqual(1, events.size());
    Test.assertEqual(:notAtStart, events[0]);
    assertNear(1700, tracker.distanceRemainingM(), 2, "picked up 300 m along");
    for (var x = 310.0; x <= 400.0; x += 10.0) {
        Test.assertEqual(0, runTo(tracker, x, 0.0).size());
    }
    assertNear(1600, tracker.distanceRemainingM(), 2, "and carries on from there");
    return true;
}

(:test)
function aStartNearTheCourseStartIsNotLate(logger as Logger) as Boolean {
    // A loop: its end is its start, so a start beside it must not read as finished.
    var loop = courseThrough([[0.0, 0.0], [500.0, 0.0], [500.0, 500.0], [0.0, 500.0], [0.0, 0.0]]);
    var tracker = new CourseTracker(loop);

    Test.assertEqual(0, runTo(tracker, 3.0, -60.0).size());
    assertNear(2000, tracker.distanceRemainingM(), 2, "a loop from its start");
    return true;
}

(:test)
function gpsNoiseNeitherJumpsNorRunsBackwards(logger as Logger) as Boolean {
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [1000.0, 1000.0]]));
    // Fixes scattered up to 15 m around the true position, some of them behind it.
    var wobble = [[0.0, 12.0], [-8.0, -15.0], [6.0, 4.0], [-14.0, 9.0], [11.0, -12.0], [-5.0, 0.0]];

    var last = tracker.distanceRemainingM();
    for (var i = 0; i < 180; i++) {
        var along = i * 10.0;
        var x = along <= 1000 ? along : 1000.0;
        var y = along <= 1000 ? 0.0 : along - 1000;
        var w = wobble[i % wobble.size()];
        runTo(tracker, x + w[0], y + w[1]);

        var remaining = tracker.distanceRemainingM();
        Test.assertMessage(remaining <= last, "ran backwards at " + along + " m: " + last + " then " + remaining);
        assertNear(2000 - along, remaining, 20, "at " + along + " m");
        last = remaining;
    }
    return true;
}

(:test)
function catchesUpAfterAGpsGap(logger as Logger) as Boolean {
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [1000.0, 1000.0]]));
    for (var x = 0.0; x <= 200.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }

    // No fixes for 500 m, then the runner is 700 m along: beyond the window ahead of the last match.
    for (var x = 700.0; x <= 800.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }

    assertNear(1200, tracker.distanceRemainingM(), 2, "caught up 800 m along");
    return true;
}

(:test)
function catchingUpOnAnOutAndBackPicksTheWayOut(logger as Logger) as Boolean {
    var tracker = new CourseTracker(courseThrough([[0.0, 0.0], [1000.0, 0.0], [0.0, 0.0]]));
    for (var x = 0.0; x <= 100.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }

    // A gap on the way out: 600 m east lies on both the way out and the way back.
    for (var x = 600.0; x <= 700.0; x += 10.0) {
        runTo(tracker, x, 0.0);
    }

    assertNear(1300, tracker.distanceRemainingM(), 2, "700 m out, not on the way back");
    return true;
}
