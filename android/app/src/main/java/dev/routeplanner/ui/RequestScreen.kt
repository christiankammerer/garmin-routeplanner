package dev.routeplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import dev.routeplanner.route.RoutingProblem
import dev.routeplanner.route.TargetDistance
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import java.util.Locale

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Where the map starts before the first location fix: central Stockholm. */
private val BEFORE_FIRST_FIX = Position(longitude = 18.0686, latitude = 59.3293)

private val RouteColor = Color(0xFF1A73E8)

/** The request screen: a full-screen map with the target distance in a sheet at the bottom. */
@Composable
fun RequestScreen(
    viewModel: RequestViewModel,
    onGetBRouter: () -> Unit,
    onOpenBRouter: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shownRoute = (state.routeState as? RouteState.Shown)?.route
    val locationState = rememberLocationState()

    LaunchedEffect(locationState) {
        if (locationState.permission !is LocationPermission.Granted) locationState.requestPermission()
    }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE),
        initialCameraPosition = CameraPosition(target = BEFORE_FIRST_FIX, zoom = 12.0),
    ) {
        LocationIndicatorLayer(id = "runner", locationState = locationState)
        if (shownRoute != null) RouteLine(shownRoute)
    }

    LocationTrackingEffect(locationState, trackBearing = false) {
        val here = currentLocation.position
        viewModel.onLocation(LatLon(lat = here.latitude, lon = here.longitude))
        if (previousLocation == null) mapState.animateCamera(CameraUpdate(target = here, zoom = 14.0))
    }

    LaunchedEffect(shownRoute) {
        shownRoute?.let {
            mapState.animateCameraToBounds(
                boundingBox = it.bounds(),
                fitPadding = DpPadding(left = 32.dp, top = 64.dp, right = 32.dp, bottom = 320.dp),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The attribution is drawn by us, above the sheet, so it is always visible.
        MaplibreMap(state = mapState, overlay = {})
        Column(Modifier.align(Alignment.BottomCenter)) {
            Attribution(Modifier.padding(start = 8.dp, bottom = 4.dp))
            RequestSheet(
                state = state,
                quickPicks = viewModel.quickPicks,
                locationDenied = locationState.permission is LocationPermission.NotGranted,
                onShorter = viewModel::shorter,
                onLonger = viewModel::longer,
                onPick = viewModel::pick,
                onFindRoutes = viewModel::findRoutes,
                onAllowLocation = locationState::requestPermission,
                onGetBRouter = onGetBRouter,
                onOpenBRouter = onOpenBRouter,
            )
        }
    }
}

@Composable
private fun RouteLine(route: Route) {
    // A source's data is fixed when it is created, so each route gets its own source and layer.
    key(route) {
        val source = rememberGeoJsonSource(
            GeoJsonData.Features(LineString(route.points.map { Position(longitude = it.lon, latitude = it.lat) })),
        )
        LineLayer(
            id = "route-${route.hashCode()}",
            source = source,
            color = const(RouteColor),
            width = const(5.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )
    }
}

@Composable
private fun Attribution(modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withLink(LinkAnnotation.Url("https://openfreemap.org")) { append("OpenFreeMap") }
        append(" ")
        withLink(LinkAnnotation.Url("https://www.openmaptiles.org/")) { append("© OpenMapTiles") }
        append(" Data from ")
        withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) { append("OpenStreetMap") }
    }
    Surface(modifier, shape = RoundedCornerShape(4.dp), color = Color.White.copy(alpha = 0.8f)) {
        Text(text, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RequestSheet(
    state: RequestUiState,
    quickPicks: List<TargetDistance>,
    locationDenied: Boolean,
    onShorter: () -> Unit,
    onLonger: () -> Unit,
    onPick: (TargetDistance) -> Unit,
    onFindRoutes: () -> Unit,
    onAllowLocation: () -> Unit,
    onGetBRouter: () -> Unit,
    onOpenBRouter: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onShorter) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(formatKm(state.targetDistance.metres), style = MaterialTheme.typography.headlineMedium)
                FilledTonalIconButton(onClick = onLonger) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (pick in quickPicks) {
                    FilterChip(
                        selected = pick == state.targetDistance,
                        onClick = { onPick(pick) },
                        label = { Text(formatKm(pick.metres, unit = false)) },
                    )
                }
            }
            RouteStatus(state, locationDenied, onAllowLocation, onGetBRouter, onOpenBRouter)
            Button(onClick = onFindRoutes, enabled = state.canFindRoutes, modifier = Modifier.fillMaxWidth()) {
                Text("Find routes")
            }
        }
    }
}

@Composable
private fun RouteStatus(
    state: RequestUiState,
    locationDenied: Boolean,
    onAllowLocation: () -> Unit,
    onGetBRouter: () -> Unit,
    onOpenBRouter: () -> Unit,
) {
    when (val routeState = state.routeState) {
        RouteState.None -> when {
            state.start != null -> {}
            locationDenied -> Message(
                "Route Planner starts routes where you are, so it needs your location.",
                action = "Allow location" to onAllowLocation,
            )
            else -> Message("Finding your location…")
        }
        RouteState.Finding -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Finding a route…")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is RouteState.Shown -> Text(
            "Route: ${formatKm(routeState.route.lengthM)}",
            style = MaterialTheme.typography.titleMedium,
        )
        is RouteState.Problem -> when (routeState.problem) {
            RoutingProblem.NotInstalled -> Message(
                "Route Planner finds routes with the free BRouter app. Install BRouter, open it once " +
                    "and download the map data (segments) for your area.",
                action = "Get BRouter" to onGetBRouter,
            )
            RoutingProblem.SegmentsMissing -> Message(
                "BRouter has no map data for where you are. Open BRouter and download the segments " +
                    "that cover this area, then try again.",
                action = "Open BRouter" to onOpenBRouter,
            )
            is RoutingProblem.EngineError -> Message("Couldn't find a route. Please try again.")
        }
    }
}

@Composable
private fun Message(text: String, action: Pair<String, () -> Unit>? = null) {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

/** "10 km", "21.1 km"; without [unit] just the number, for the quick picks. */
private fun formatKm(metres: Int, unit: Boolean = true): String {
    val km = if (metres % 1000 == 0) "${metres / 1000}" else String.format(Locale.ROOT, "%.1f", metres / 1000.0)
    return if (unit) "$km km" else km
}

private fun Route.bounds() = BoundingBox(
    west = points.minOf { it.lon },
    south = points.minOf { it.lat },
    east = points.maxOf { it.lon },
    north = points.maxOf { it.lat },
)
