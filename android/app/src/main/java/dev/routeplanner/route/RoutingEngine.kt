package dev.routeplanner.route

/** One route as the routing engine returned it. */
data class Route(val points: List<LatLon>, val lengthM: Int)

/** Why the routing engine couldn't return a route. */
sealed interface RoutingProblem {
    /** The BRouter app isn't installed. */
    data object NotInstalled : RoutingProblem

    /** BRouter has no segments (map data) for the area. */
    data object SegmentsMissing : RoutingProblem

    data class EngineError(val message: String) : RoutingProblem
}

sealed interface EngineResult {
    data class Success(val route: Route) : EngineResult

    data class Failure(val problem: RoutingProblem) : EngineResult
}

/** The routing engine (BRouter) as the route generator sees it. */
interface RoutingEngine {
    /** A loop route from [start] through points on a circle of [radiusM], starting towards [directionDeg]. */
    suspend fun loop(start: LatLon, radiusM: Int, directionDeg: Int): EngineResult
}
