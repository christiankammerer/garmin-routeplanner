import Toybox.Activity;
import Toybox.Lang;

// How the run's numbers read on screen.
module RunFormat {
    // "4.21 km"
    function km(metres as Number) as String {
        return (metres / 1000.0).format("%.2f") + " km";
    }

    // "5:32 /km"; "--:-- /km" when standing or too slow to mean anything.
    function pace(speedMps as Float?) as String {
        if (speedMps == null || speedMps < 0.5) {
            return "--:-- /km";
        }
        var secondsPerKm = (1000 / speedMps).toNumber();
        return (secondsPerKm / 60) + ":" + (secondsPerKm % 60).format("%02d") + " /km";
    }

    // "1:02:05" or "32:10"
    function duration(ms as Number) as String {
        var s = ms / 1000;
        var clock = (s / 60 % 60).format(s >= 3600 ? "%02d" : "%d") + ":" + (s % 60).format("%02d");
        return s >= 3600 ? (s / 3600) + ":" + clock : clock;
    }

    function currentSpeed() as Float? {
        var info = Activity.getActivityInfo();
        return info != null ? info.currentSpeed : null;
    }
}
