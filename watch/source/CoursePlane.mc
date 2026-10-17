import Toybox.Lang;
import Toybox.Math;

// A course's points on a flat plane: metres east (x) and north (y) of its first point.
// Flat-earth is fine at the few kilometres a course spans.
class CoursePlane {
    var xs as Array<Float>;
    var ys as Array<Float>;
    private var _originLatE6 as Number;
    private var _originLonE6 as Number;
    private var _mPerLonE6 as Float;

    // Metres per microdegree of latitude.
    private const M_PER_LAT_E6 = 0.111195;

    function initialize(course as Course) {
        _originLatE6 = course.latE6(0);
        _originLonE6 = course.lonE6(0);
        _mPerLonE6 = (M_PER_LAT_E6 * Math.cos(Math.toRadians(_originLatE6 / 1e6))).toFloat();
        var n = course.pointCount();
        xs = new Array<Float>[n];
        ys = new Array<Float>[n];
        for (var i = 0; i < n; i++) {
            var xy = toPlane(course.latE6(i), course.lonE6(i));
            xs[i] = xy[0];
            ys[i] = xy[1];
        }
    }

    function toPlane(latE6 as Number, lonE6 as Number) as [Float, Float] {
        return [((lonE6 - _originLonE6) * _mPerLonE6).toFloat(), ((latE6 - _originLatE6) * M_PER_LAT_E6).toFloat()];
    }
}
