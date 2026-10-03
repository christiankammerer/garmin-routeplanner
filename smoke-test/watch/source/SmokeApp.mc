// Throwaway smoke-test watch app: receives probe messages from the phone,
// checks them, tries to store them, and acknowledges each one. Not the real app.
import Toybox.Application;
import Toybox.Application.Storage;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class SmokeApp extends Application.AppBase {
    var startedAt = 0;
    var received = 0;
    var lastLine = "waiting for phone";
    var storeLine = "";
    var memLine = "";
    var txOk = 0;
    var txErr = 0;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        startedAt = System.getTimer();
        // Messages that queued while the app was closed are delivered right here.
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function getInitialView() {
        return [new SmokeView()];
    }

    function onPhoneMessage(msg) {
        var data = msg.data;
        var sinceStart = System.getTimer() - startedAt;
        received++;
        if (!(data instanceof Dictionary)) {
            lastLine = "bad msg";
            WatchUi.requestUpdate();
            return;
        }
        var seq = data["seq"];
        var ack = {
            "t" => "ack",
            "seq" => seq,
            "msSinceStart" => sinceStart
        };
        if ("probe".equals(data["t"])) {
            var pts = data["pts"];
            var n = pts.size();
            ack["n"] = n;
            if ("int".equals(data["enc"])) {
                var sum = 0l;
                for (var i = 0; i < n; i++) {
                    sum += pts[i];
                }
                ack["sum"] = sum.toString();
            } else if (n > 0) {
                // Shows whether doubles arrive as 64-bit or get squeezed to 32-bit floats.
                ack["first"] = pts[0].format("%.6f");
            }
            var stats = System.getSystemStats();
            ack["usedMem"] = stats.usedMemory;
            ack["totalMem"] = stats.totalMemory;
            memLine = "mem " + (stats.usedMemory / 1024) + "/" + (stats.totalMemory / 1024) + " KB";
            ack["stored"] = tryStore(pts);
            lastLine = "#" + seq + ": " + n + " values";
        } else {
            lastLine = "ping #" + seq;
        }
        Communications.transmit(ack, null, new AckListener());
        WatchUi.requestUpdate();
    }

    // The real app keeps one course in Storage; check that a payload of this size fits.
    function tryStore(pts) {
        try {
            Storage.deleteValue("course");
            Storage.setValue("course", pts);
            storeLine = "stored ok";
            return true;
        } catch (e) {
            storeLine = "store FAILED";
            return false;
        }
    }
}

function smokeApp() as SmokeApp {
    return Application.getApp() as SmokeApp;
}

class AckListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() {
        smokeApp().txOk++;
        WatchUi.requestUpdate();
    }

    function onError() {
        smokeApp().txErr++;
        WatchUi.requestUpdate();
    }
}

class SmokeView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc) {
        var app = smokeApp();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var cx = dc.getWidth() / 2;
        var lines = [
            "Course Smoke",
            "msgs " + app.received,
            app.lastLine,
            app.storeLine,
            app.memLine,
            "acks ok " + app.txOk + " err " + app.txErr
        ];
        for (var i = 0; i < lines.size(); i++) {
            dc.drawText(cx, 80 + i * 40, Graphics.FONT_SMALL, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
        }
    }
}
