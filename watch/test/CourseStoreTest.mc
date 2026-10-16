import Toybox.Lang;
import Toybox.Test;
import Toybox.WatchUi;

(:test)
function anEmptyStoreHoldsNoCourse(logger as Logger) as Boolean {
    CourseStore.clear();

    Test.assert(CourseStore.load() == null);
    return true;
}

(:test)
function aSavedCourseLoadsBackTheSame(logger as Logger) as Boolean {
    var sample = WatchUi.loadResource(Rez.JsonData.CourseNormal) as Dictionary;
    var course = Course.fromMessage(sample) as Course;

    CourseStore.save(course);
    var loaded = CourseStore.load() as Course;

    Test.assertEqual(course.id, loaded.id);
    Test.assertEqual(course.sequence, loaded.sequence);
    Test.assertEqual(course.totalM, loaded.totalM);
    Test.assertEqual(course.points.size(), loaded.points.size());
    for (var i = 0; i < course.points.size(); i++) {
        Test.assertEqual(course.points[i], loaded.points[i]);
    }
    return true;
}

(:test)
function aNewCourseReplacesTheStoredOne(logger as Logger) as Boolean {
    var normal = Course.fromMessage(WatchUi.loadResource(Rez.JsonData.CourseNormal) as Dictionary) as Course;
    var full = Course.fromMessage(WatchUi.loadResource(Rez.JsonData.Course500) as Dictionary) as Course;

    CourseStore.save(normal);
    CourseStore.save(full);

    Test.assertEqual(full.id, (CourseStore.load() as Course).id);
    return true;
}
