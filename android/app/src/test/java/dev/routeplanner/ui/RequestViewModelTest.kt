package dev.routeplanner.ui

import dev.routeplanner.route.FakeRoutingEngine
import dev.routeplanner.route.LatLon
import dev.routeplanner.route.RouteGenerator
import dev.routeplanner.route.RoutingProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val HOME = LatLon(59.3293, 18.0686)

@OptIn(ExperimentalCoroutinesApi::class)
class RequestViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val engine = FakeRoutingEngine.answering { radiusM, _ -> radiusM * 57 / 10 }

    private fun viewModel(engine: FakeRoutingEngine = this.engine) = RequestViewModel(RouteGenerator(engine))

    private val RequestViewModel.targetMetres get() = state.value.targetDistance.metres

    @Test
    fun `plus and minus change the target distance in half-kilometre steps`() {
        val vm = viewModel()
        assertEquals(10_000, vm.targetMetres)

        vm.longer()
        vm.longer()
        vm.shorter()

        assertEquals(10_500, vm.targetMetres)
    }

    @Test
    fun `target distance stays between 1 and 50 km`() {
        val vm = viewModel()

        repeat(100) { vm.longer() }
        assertEquals(50_000, vm.targetMetres)

        repeat(100) { vm.shorter() }
        assertEquals(1_000, vm.targetMetres)
    }

    @Test
    fun `quick picks offer the common distances and set the target in one tap`() {
        val vm = viewModel()
        assertEquals(listOf(5_000, 8_000, 10_000, 15_000, 21_100), vm.quickPicks.map { it.metres })

        vm.pick(vm.quickPicks.last())
        assertEquals(21_100, vm.targetMetres)

        vm.longer()
        assertEquals(21_600, vm.targetMetres)
    }

    @Test
    fun `finds a loop route from the runner's location and shows it`() {
        val vm = viewModel()

        vm.onLocation(HOME)
        vm.findRoutes()

        val route = (vm.state.value.routeState as RouteState.Shown).route
        assertEquals(HOME, route.points.first())
        assertTrue("length ${route.lengthM}", route.lengthM >= 10_000)
    }

    @Test
    fun `shows that it is finding routes until the route arrives`() {
        val vm = viewModel()
        vm.onLocation(HOME)
        engine.holdAnswers()

        vm.findRoutes()
        assertEquals(RouteState.Finding, vm.state.value.routeState)

        engine.releaseAnswers()
        assertTrue(vm.state.value.routeState is RouteState.Shown)
    }

    @Test
    fun `explains each routing problem instead of a route`() {
        val problems = listOf(
            RoutingProblem.NotInstalled,
            RoutingProblem.SegmentsMissing,
            RoutingProblem.EngineError("Profile error"),
        )
        for (problem in problems) {
            val vm = viewModel(FakeRoutingEngine.failingWith(problem))
            vm.onLocation(HOME)

            vm.findRoutes()

            assertEquals(RouteState.Problem(problem), vm.state.value.routeState)
        }
    }

    @Test
    fun `routes can be found once the location is known and not while finding`() {
        val vm = viewModel()
        assertFalse(vm.state.value.canFindRoutes)

        vm.onLocation(HOME)
        assertTrue(vm.state.value.canFindRoutes)

        engine.holdAnswers()
        vm.findRoutes()
        assertFalse(vm.state.value.canFindRoutes)

        engine.releaseAnswers()
        assertTrue(vm.state.value.canFindRoutes)
    }
}
