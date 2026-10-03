package dev.routeplanner.smoketest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import btools.routingapp.IBRouterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs

private const val TOLERANCE = 0.05
private const val MAX_TRIES = 6

data class Candidate(val points: List<LatLon>, val lengthM: Double, val ms: Long)

/** Times BRouter round trips, calibrating the radius until the loop is within ±5% of the target. */
class BRouterTimer(private val context: Context, private val log: (String) -> Unit) {
    private var service: IBRouterService? = null

    private suspend fun bind(): IBRouterService = service ?: suspendCancellableCoroutine { cont ->
        val intent = Intent().setClassName("btools.routingapp", "btools.routingapp.BRouterService")
        val ok = context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val s = IBRouterService.Stub.asInterface(binder)
                service = s
                if (cont.isActive) cont.resume(s)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }, Context.BIND_AUTO_CREATE)
        if (!ok) cont.cancel(IllegalStateException("BRouter not installed?"))
    }

    suspend fun candidates(start: LatLon, targetM: Double, profile: String, parallel: Boolean): List<Candidate?> {
        val svc = bind()
        val t0 = SystemClock.elapsedRealtime()
        val directions = listOf(0, 120, 240)
        val results = if (parallel) {
            coroutineScope {
                directions.map { dir -> async(Dispatchers.IO) { calibrate(svc, start, targetM, dir, profile, t0) } }.awaitAll()
            }
        } else {
            directions.map { dir -> withContext(Dispatchers.IO) { calibrate(svc, start, targetM, dir, profile, t0) } }
        }
        log("TOTAL for 3 candidates (${if (parallel) "parallel" else "sequential"}): ${SystemClock.elapsedRealtime() - t0} ms")
        return results
    }

    private fun calibrate(svc: IBRouterService, start: LatLon, targetM: Double, direction: Int, profile: String, t0: Long): Candidate? {
        val c0 = SystemClock.elapsedRealtime()
        var radius = targetM / 6
        var best: Pair<List<LatLon>, Double>? = null
        for (attempt in 1..MAX_TRIES) {
            val callStart = SystemClock.elapsedRealtime()
            val route = roundTrip(svc, start, radius.toInt(), direction, profile)
            val callMs = SystemClock.elapsedRealtime() - callStart
            if (route == null) return null
            val (points, length) = route
            val err = (length - targetM) / targetM
            log("  dir=$direction try=$attempt r=${radius.toInt()} m -> ${"%.2f".format(length / 1000)} km (${"%+.1f".format(err * 100)}%), ${points.size} pts, $callMs ms")
            if (best == null || abs(length - targetM) < abs(best.second - targetM)) best = route
            if (abs(err) <= TOLERANCE) break
            radius *= targetM / length
        }
        val (points, length) = best!!
        val ms = SystemClock.elapsedRealtime() - c0
        log("CANDIDATE dir=$direction: ${"%.2f".format(length / 1000)} km in $ms ms (ready at +${SystemClock.elapsedRealtime() - t0} ms)")
        return Candidate(points, length, ms)
    }

    private fun roundTrip(svc: IBRouterService, start: LatLon, radiusM: Int, direction: Int, profile: String): Pair<List<LatLon>, Double>? {
        val params = Bundle().apply {
            putDoubleArray("lats", doubleArrayOf(start.lat))
            putDoubleArray("lons", doubleArrayOf(start.lon))
            putInt("engineMode", 4)
            putString("roundTripDistance", radiusM.toString())
            putString("direction", direction.toString())
            putString("trackFormat", "json")
            putString("maxRunningTime", "60")
            if (profile.isBlank()) {
                putString("v", "foot")
                putString("fast", "0")
            } else {
                putString("profile", profile)
            }
        }
        val result = svc.getTrackFromParams(params)
        if (result == null || !result.trimStart().startsWith("{")) {
            log("  BRouter error: $result")
            return null
        }
        val feature = JSONObject(result).getJSONArray("features").getJSONObject(0)
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val points = List(coords.length()) { i ->
            val c = coords.getJSONArray(i)
            LatLon(c.getDouble(1), c.getDouble(0))
        }
        val reported = feature.optJSONObject("properties")?.optString("track-length")?.toDoubleOrNull()
        return points to (reported ?: lengthM(points))
    }
}
