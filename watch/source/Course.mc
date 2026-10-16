import Toybox.Lang;

// A course as the phone sends it: whole numbers only. points is flat [latE6, lonE6, cumulative metres, ...].
class Course {
    var id as String;
    var sequence as Number;
    var totalM as Number;
    var points as Array<Number>;

    function initialize(id as String, sequence as Number, totalM as Number, points as Array<Number>) {
        self.id = id;
        self.sequence = sequence;
        self.totalM = totalM;
        self.points = points;
    }

    function pointCount() as Number {
        return points.size() / 3;
    }

    function latE6(i as Number) as Number {
        return points[i * 3];
    }

    function lonE6(i as Number) as Number {
        return points[i * 3 + 1];
    }

    // Reads a course message, or a course as stored; null when it isn't a whole, well-formed course.
    static function fromDictionary(data as Dictionary) as Course? {
        var id = data["id"];
        var sequence = data["seq"];
        var totalM = data["dist"];
        var points = data["pts"];
        if (!(id instanceof String) || !(sequence instanceof Number) || !(totalM instanceof Number)) {
            return null;
        }
        if (!(points instanceof Array) || points.size() < 6 || points.size() % 3 != 0) {
            return null;
        }
        for (var i = 0; i < points.size(); i++) {
            if (!(points[i] instanceof Number)) {
                return null;
            }
        }
        return new Course(id, sequence, totalM, points as Array<Number>);
    }

    function toDictionary() as Dictionary {
        return {"id" => id, "seq" => sequence, "dist" => totalM, "pts" => points};
    }
}
