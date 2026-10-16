import Toybox.Lang;
import Toybox.Test;
import Toybox.WatchUi;

// The message handler's decisions, driven by the sample course messages the phone's tests produce.

(:test)
function storesTheNormalSampleCourseAndAcknowledgesIt(logger as Logger) as Boolean {
    var sample = WatchUi.loadResource(Rez.JsonData.CourseNormal) as Dictionary;

    var reply = MessageHandler.handle(sample, false);

    assertStoredAck(sample, reply);
    var course = reply.course as Course;
    Test.assertEqual((sample["pts"] as Array).size() / 3, course.pointCount());
    Test.assertEqual(sample["dist"], course.totalM);
    return true;
}

(:test)
function storesTheFull500PointSampleCourse(logger as Logger) as Boolean {
    var sample = WatchUi.loadResource(Rez.JsonData.Course500) as Dictionary;

    var reply = MessageHandler.handle(sample, false);

    assertStoredAck(sample, reply);
    Test.assertEqual(500, (reply.course as Course).pointCount());
    return true;
}

(:test)
function refusesACourseWhileRecordingARun(logger as Logger) as Boolean {
    var sample = WatchUi.loadResource(Rez.JsonData.CourseNormal) as Dictionary;

    var reply = MessageHandler.handle(sample, true);

    var ack = reply.message as Dictionary;
    Test.assertEqual("busy", ack["outcome"]);
    Test.assertEqual(sample["id"], ack["id"]);
    Test.assertEqual(sample["seq"], ack["seq"]);
    Test.assert(reply.course == null);
    return true;
}

(:test)
function answersAPingWhileRecording(logger as Logger) as Boolean {
    Test.assertEqual("pong", (MessageHandler.handle({"t" => "ping"}, true).message as Dictionary)["t"]);
    return true;
}

(:test)
function answersAPingWithAPong(logger as Logger) as Boolean {
    var reply = MessageHandler.handle({"t" => "ping"}, false);

    Test.assertEqual("pong", (reply.message as Dictionary)["t"]);
    Test.assert(reply.course == null);
    return true;
}

(:test)
function ignoresMessagesItCantRead(logger as Logger) as Boolean {
    var broken = [
        "hello",
        {"t" => "bogus"},
        {"t" => "course", "id" => "1c88e4cd", "seq" => 1, "dist" => 100},
        {"t" => "course", "id" => "1c88e4cd", "seq" => 1, "dist" => 100, "pts" => [59329205, 18068517, 0, 59329365]},
        {"t" => "course", "id" => "1c88e4cd", "seq" => 1, "dist" => 100, "pts" => [59329205, 18068517, 0]},
        {"t" => "course", "id" => "1c88e4cd", "seq" => 1, "dist" => 100, "pts" => [59.329205, 18.068517, 0, 59.329365, 18.068271, 24]}
    ];
    for (var i = 0; i < broken.size(); i++) {
        var reply = MessageHandler.handle(broken[i], false);
        Test.assertMessage(reply.message == null && reply.course == null, "answered message " + i);
    }
    return true;
}

function assertStoredAck(sample as Dictionary, reply as Reply) as Void {
    var ack = reply.message as Dictionary;
    Test.assertEqual("ack", ack["t"]);
    Test.assertEqual("stored", ack["outcome"]);
    Test.assertEqual(sample["id"], ack["id"]);
    Test.assertEqual(sample["seq"], ack["seq"]);
    Test.assert(reply.course != null);
}
