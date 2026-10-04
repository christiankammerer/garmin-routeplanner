package dev.routeplanner.route

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import btools.routingapp.IBRouterService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

const val BROUTER_PACKAGE = "btools.routingapp"
private const val BROUTER_SERVICE = "btools.routingapp.BRouterService"
private const val PROFILE_ASSET = "running.brf"
private const val BIND_TIMEOUT_MS = 10_000L

/** Route preferences as running.brf parameters; all off until the request screen offers them. */
private val DEFAULT_PROFILE_PARAMS = mapOf("low_incline" to 0)

/**
 * The real routing engine: the separate BRouter app, called over its AIDL service (ADR 0001).
 * Every call carries our running profile as `remoteProfile` and its parameters as `extraParams`.
 */
class BRouterEngine(context: Context) : RoutingEngine {
    private val context = context.applicationContext
    private val profile by lazy { this.context.assets.open(PROFILE_ASSET).bufferedReader().use { it.readText() } }
    private val bindLock = Mutex()
    private var service: IBRouterService? = null

    override suspend fun loop(start: LatLon, radiusM: Int, directionDeg: Int): EngineResult {
        val answer = when (val call = request(roundTripParams(start, radiusM, directionDeg, DEFAULT_PROFILE_PARAMS))) {
            Call.NotInstalled -> return EngineResult.Failure(RoutingProblem.NotInstalled)
            is Call.Broken -> return EngineResult.Failure(RoutingProblem.EngineError(call.message))
            is Call.Answered -> call.answer
        }
        return when {
            answer == null -> EngineResult.Failure(RoutingProblem.EngineError("BRouter returned no route"))
            answer.trimStart().startsWith("{") -> EngineResult.Success(parseRoute(answer))
            isMissingSegments(answer) -> EngineResult.Failure(RoutingProblem.SegmentsMissing)
            else -> EngineResult.Failure(RoutingProblem.EngineError(answer))
        }
    }

    /** The parameters for one round trip, as BRouter's AIDL service reads them. */
    internal fun roundTripParams(start: LatLon, radiusM: Int, directionDeg: Int, profileParams: Map<String, Int>) =
        Bundle().apply {
            // The lats/lons arrays need two points; a round trip has one start, which only lonlats accepts.
            putString("lonlats", "${start.lon},${start.lat}")
            putInt("engineMode", 4)
            putString("roundTripDistance", radiusM.toString())
            putString("direction", directionDeg.toString())
            putString("trackFormat", "json")
            putString("maxRunningTime", "60")
            putString("remoteProfile", profile)
            // BRouter reads extraParams as a URL-style string, not the Bundle its AIDL comment describes.
            putString("extraParams", profileParams.entries.joinToString("&") { (k, v) -> "$k=$v" })
        }

    internal sealed interface Call {
        data object NotInstalled : Call

        data class Broken(val message: String) : Call

        /** BRouter's answer: a route as JSON, an error message, or null. */
        data class Answered(val answer: String?) : Call
    }

    /** Sends one request to BRouter. The call blocks inside BRouter, so it runs on the IO dispatcher. */
    internal suspend fun request(params: Bundle): Call {
        if (!isInstalled()) return Call.NotInstalled
        val svc = bind() ?: return Call.Broken("Couldn't connect to the BRouter service")
        return withContext(Dispatchers.IO) {
            try {
                Call.Answered(svc.getTrackFromParams(params))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead binder means BRouter went away; bind afresh next time.
                service = null
                Call.Broken(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun bind(): IBRouterService? = bindLock.withLock {
        service?.let { return it }
        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        val s = IBRouterService.Stub.asInterface(binder)
                        service = s
                        if (cont.isActive) cont.resume(s)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        service = null
                    }

                    override fun onNullBinding(name: ComponentName) {
                        context.unbindService(this)
                        if (cont.isActive) cont.resume(null)
                    }
                }
                val intent = Intent().setClassName(BROUTER_PACKAGE, BROUTER_SERVICE)
                if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    context.unbindService(connection)
                    cont.resume(null)
                }
            }
        }
    }

    private fun isInstalled() = try {
        context.packageManager.getPackageInfo(BROUTER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * BRouter answers "datafile E10_N55.rd5 not found" when the segment for an area isn't downloaded.
 * It also fails this way before BRouter has been opened once to choose its folder.
 */
private fun isMissingSegments(answer: String) =
    Regex("""datafile \S+ not found""").containsMatchIn(answer) || "not mapped in existing datafile" in answer

/** Reads a route from BRouter's JSON track format: a GeoJSON feature with [lon, lat, elevation] points. */
internal fun parseRoute(json: String): Route {
    val feature = JSONObject(json).getJSONArray("features").getJSONObject(0)
    val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
    val points = List(coords.length()) { i ->
        val c = coords.getJSONArray(i)
        LatLon(lat = c.getDouble(1), lon = c.getDouble(0))
    }
    val lengthM = feature.getJSONObject("properties").getString("track-length").toInt()
    return Route(points, lengthM)
}
