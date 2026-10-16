import Toybox.Lang;

// What to do about one message from the phone: the message to send back, and a course to store.
class Reply {
    var message as Dictionary?;
    var course as Course?;

    function initialize(message as Dictionary?, course as Course?) {
        self.message = message;
        self.course = course;
    }
}

// The phone ↔ watch message protocol, watch side (the phone side is Protocol.kt). Every message is a
// Dictionary with its type under "t"; values are whole numbers and short strings only.
// Pure: it decides, and RouteRunnerApp stores, vibrates and sends.
module MessageHandler {
    // While [recording] a run, a course is refused with "busy", so a phone tap can't change the route mid-run.
    function handle(data as Object?, recording as Boolean) as Reply {
        if (!(data instanceof Dictionary)) {
            return new Reply(null, null);
        }
        var type = data["t"];
        if ("ping".equals(type)) {
            return new Reply({"t" => "pong"}, null);
        }
        if ("course".equals(type)) {
            var course = Course.fromDictionary(data);
            if (course != null) {
                return recording ? new Reply(ack(course, "busy"), null) : new Reply(ack(course, "stored"), course);
            }
        }
        return new Reply(null, null);
    }

    function ack(course as Course, outcome as String) as Dictionary {
        return {"t" => "ack", "id" => course.id, "seq" => course.sequence, "outcome" => outcome};
    }
}
