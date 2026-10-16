import Toybox.Application.Storage;
import Toybox.Lang;

// The one course the watch holds, kept in Storage so it survives restarting the app.
module CourseStore {
    const KEY = "course";

    function load() as Course? {
        var stored = Storage.getValue(KEY);
        return stored instanceof Dictionary ? Course.fromDictionary(stored) : null;
    }

    function save(course as Course) as Void {
        Storage.setValue(KEY, course.toDictionary());
    }

    function clear() as Void {
        Storage.deleteValue(KEY);
    }
}
