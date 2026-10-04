package dev.routeplanner.route

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real BRouter app. Needs BRouter installed with the segment for [start]
 * (E15_N55 for the default, central Stockholm). Pass `-e start lat,lon` to start elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class BRouterEngineDeviceTest {
    private val engine = BRouterEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    private val start = InstrumentationRegistry.getArguments().getString("start")
        ?.split(",")?.let { (lat, lon) -> LatLon(lat.toDouble(), lon.toDouble()) }
        ?: LatLon(59.3293, 18.0686)

    @Test
    fun returnsALoopRouteFromTheStart() = runTest {
        val result = engine.loop(start, radiusM = 1_500, directionDeg = 0)

        val route = (result as EngineResult.Success).route
        assertTrue("length ${route.lengthM}", route.lengthM > 3_000)
        assertEquals(route.points.first(), route.points.last())
    }

    /**
     * Turning low_incline on adds uphill cost to every way, so the cheapest route can only get
     * dearer. A higher cost proves the parameter reached the profile BRouter ran.
     */
    @Test
    fun profileParameterReachesBRouter() = runTest {
        // A hilly loop from central Stockholm (cost 16850 off vs 17227 on, desktop BRouter 1.7.10).
        val off = cost(engine.request(engine.roundTripParams(start, 2_500, 120, mapOf("low_incline" to 0))))
        val on = cost(engine.request(engine.roundTripParams(start, 2_500, 120, mapOf("low_incline" to 1))))

        assertTrue("cost with low_incline off $off, on $on", on > off)
    }

    private fun cost(call: BRouterEngine.Call): Int {
        val answer = (call as BRouterEngine.Call.Answered).answer
        assertTrue("BRouter answered: $answer", answer?.trimStart()?.startsWith("{") == true)
        val properties = JSONObject(answer!!).getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        return properties.getString("cost").toInt()
    }
}
