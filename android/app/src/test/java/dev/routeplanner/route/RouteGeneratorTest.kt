package dev.routeplanner.route

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val HOME = LatLon(59.3293, 18.0686)

class RouteGeneratorTest {
    @Test
    fun `finds a loop route from the start near the target distance`() = runTest {
        // Real round trips with our profile come out at about 5.7 times the radius.
        val engine = FakeRoutingEngine.answering { radiusM, _ -> radiusM * 57 / 10 }

        val outcome = RouteGenerator(engine).loopRoute(HOME, TargetDistance.ofMetres(10_000))

        val route = (outcome as RouteOutcome.Found).route
        assertTrue("length ${route.lengthM}", route.lengthM in 10_000..10_800)
        assertEquals(HOME, route.points.first())
    }

    @Test
    fun `tells when BRouter isn't installed`() = runTest {
        val outcome = RouteGenerator(FakeRoutingEngine.failingWith(RoutingProblem.NotInstalled))
            .loopRoute(HOME, TargetDistance.ofMetres(10_000))

        assertEquals(RouteOutcome.Failed(RoutingProblem.NotInstalled), outcome)
    }
}
