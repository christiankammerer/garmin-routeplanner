package dev.routeplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import dev.routeplanner.route.RouteGenerator
import dev.routeplanner.route.RouteOutcome
import dev.routeplanner.route.RoutingProblem
import dev.routeplanner.route.TargetDistance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RouteState {
    data object None : RouteState

    data object Finding : RouteState

    data class Shown(val route: Route) : RouteState

    data class Problem(val problem: RoutingProblem) : RouteState
}

data class RequestUiState(
    val targetDistance: TargetDistance = TargetDistance.Default,
    /** The runner's current location, the start of the route; unknown until the first fix. */
    val start: LatLon? = null,
    val routeState: RouteState = RouteState.None,
) {
    val canFindRoutes get() = start != null && routeState != RouteState.Finding
}

/** State holder for the request screen: the route request and the route found for it. */
class RequestViewModel(private val generator: RouteGenerator) : ViewModel() {
    private val _state = MutableStateFlow(RequestUiState())
    val state: StateFlow<RequestUiState> = _state.asStateFlow()

    val quickPicks = TargetDistance.QuickPicks

    fun pick(distance: TargetDistance) = setTarget { distance }

    fun longer() = setTarget { it.longer() }

    fun shorter() = setTarget { it.shorter() }

    /** A route found for the old target would read as the result for the new one, so it goes. */
    private fun setTarget(change: (TargetDistance) -> TargetDistance) = _state.update {
        val routeState = if (it.routeState == RouteState.Finding) it.routeState else RouteState.None
        it.copy(targetDistance = change(it.targetDistance), routeState = routeState)
    }

    fun onLocation(location: LatLon) = _state.update { it.copy(start = location) }

    fun findRoutes() {
        val request = _state.value
        if (!request.canFindRoutes) return
        val start = checkNotNull(request.start)
        _state.update { it.copy(routeState = RouteState.Finding) }
        viewModelScope.launch {
            val routeState = when (val outcome = generator.loopRoute(start, request.targetDistance)) {
                is RouteOutcome.Found -> RouteState.Shown(outcome.route)
                is RouteOutcome.Failed -> RouteState.Problem(outcome.problem)
            }
            _state.update { it.copy(routeState = routeState) }
        }
    }
}
