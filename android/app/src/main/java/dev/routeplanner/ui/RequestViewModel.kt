package dev.routeplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.routeplanner.course.buildCourse
import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import dev.routeplanner.route.RouteGenerator
import dev.routeplanner.route.RouteOutcome
import dev.routeplanner.route.RoutingProblem
import dev.routeplanner.route.TargetDistance
import dev.routeplanner.watch.Delivery
import dev.routeplanner.watch.DeliveryOutcome
import dev.routeplanner.watch.DeliveryProblem
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

/** Where sending the shown route to the watch stands. */
sealed interface SendState {
    data object None : SendState

    data object Sending : SendState

    /** The watch confirmed it holds the course, [totalM] long. */
    data class OnWatch(val totalM: Int) : SendState

    data class Failed(val problem: DeliveryProblem) : SendState
}

data class RequestUiState(
    val targetDistance: TargetDistance = TargetDistance.Default,
    /** The runner's current location, the start of the route; unknown until the first fix. */
    val start: LatLon? = null,
    val routeState: RouteState = RouteState.None,
    val sendState: SendState = SendState.None,
) {
    val canFindRoutes get() = start != null && routeState != RouteState.Finding && sendState != SendState.Sending

    val canRunThisOne get() = routeState is RouteState.Shown && sendState != SendState.Sending
}

/** The phone's clock in seconds: it fits the watch's 32-bit numbers, and a later send gets a higher one. */
private fun epochSeconds() = (System.currentTimeMillis() / 1000).toInt()

/** State holder for the request screen: the route request, the route found for it, and sending it to the watch. */
class RequestViewModel(
    private val generator: RouteGenerator,
    private val delivery: Delivery,
    private val sequenceNumber: () -> Int = ::epochSeconds,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestUiState())
    val state: StateFlow<RequestUiState> = _state.asStateFlow()

    val quickPicks = TargetDistance.QuickPicks

    fun pick(distance: TargetDistance) = setTarget { distance }

    fun longer() = setTarget { it.longer() }

    fun shorter() = setTarget { it.shorter() }

    /** A route found for the old target would read as the result for the new one, so it goes. */
    private fun setTarget(change: (TargetDistance) -> TargetDistance) = _state.update {
        val routeState = if (it.routeState == RouteState.Finding) it.routeState else RouteState.None
        it.copy(targetDistance = change(it.targetDistance), routeState = routeState, sendState = it.sendState.forNewRoute())
    }

    fun onLocation(location: LatLon) = _state.update { it.copy(start = location) }

    fun findRoutes() {
        val request = _state.value
        if (!request.canFindRoutes) return
        val start = checkNotNull(request.start)
        _state.update { it.copy(routeState = RouteState.Finding, sendState = SendState.None) }
        viewModelScope.launch {
            val routeState = when (val outcome = generator.loopRoute(start, request.targetDistance)) {
                is RouteOutcome.Found -> RouteState.Shown(outcome.route)
                is RouteOutcome.Failed -> RouteState.Problem(outcome.problem)
            }
            _state.update { it.copy(routeState = routeState) }
        }
    }

    /** Sends the shown route to the watch; also Retry after a failed send, with a fresh sequence number. */
    fun runThisOne() {
        if (!_state.value.canRunThisOne) return
        val route = (_state.value.routeState as RouteState.Shown).route
        val course = buildCourse(route, sequenceNumber())
        _state.update { it.copy(sendState = SendState.Sending) }
        viewModelScope.launch {
            val sendState = when (val outcome = delivery.deliver(course)) {
                is DeliveryOutcome.Delivered -> SendState.OnWatch(outcome.course.totalM)
                is DeliveryOutcome.Failed -> SendState.Failed(outcome.problem)
            }
            _state.update { it.copy(sendState = sendState) }
        }
    }

    fun closeConfirmation() = _state.update { it.copy(sendState = SendState.None) }
}

/** A send's result belongs to the route it sent; one still running carries on. */
private fun SendState.forNewRoute() = if (this == SendState.Sending) this else SendState.None
