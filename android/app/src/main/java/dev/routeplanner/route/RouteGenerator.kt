package dev.routeplanner.route

import kotlin.math.roundToInt

/**
 * BRouter round trips with running.brf come out at about 5.7 times their radius (median of 36 round
 * trips around Stockholm, radius 0.8–3 km; most fell between 4.7 and 6.5).
 */
private const val LOOP_LENGTH_PER_RADIUS = 5.7

/** Calibration aims this far above the target distance, inside the −0% / +8% band. */
private const val AIM_ABOVE_TARGET = 1.03

sealed interface RouteOutcome {
    data class Found(val route: Route) : RouteOutcome

    data class Failed(val problem: RoutingProblem) : RouteOutcome
}

/** Generates routes for a route request with the routing engine. */
class RouteGenerator(private val engine: RoutingEngine) {
    /** One loop route from [start], at a radius estimated from [target]; no length tuning yet. */
    suspend fun loopRoute(start: LatLon, target: TargetDistance): RouteOutcome {
        val radiusM = (target.metres * AIM_ABOVE_TARGET / LOOP_LENGTH_PER_RADIUS).roundToInt()
        return when (val result = engine.roundTrip(start, radiusM, directionDeg = 0)) {
            is EngineResult.Success -> RouteOutcome.Found(result.route)
            is EngineResult.Failure -> RouteOutcome.Failed(result.problem)
        }
    }
}
