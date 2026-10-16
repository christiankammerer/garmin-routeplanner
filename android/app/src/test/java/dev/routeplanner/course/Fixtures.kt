package dev.routeplanner.course

import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A route saved from a real BRouter answer: comment lines, then one `lat,lon` per line. */
fun loadRoute(resource: String): Route {
    val lines = readResource(resource).lines().filter { it.isNotBlank() && !it.startsWith("#") }
    val points = lines.map { line -> line.split(",").let { (lat, lon) -> LatLon(lat.toDouble(), lon.toDouble()) } }
    val lengthM = Regex("""track-length (\d+)""").find(readResource(resource))!!.groupValues[1].toInt()
    return Route(points, lengthM)
}

fun readResource(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader!!.getResource(name)) { "no test resource $name" }.readText()

/**
 * A loop of 10 km circumference around central Stockholm that wobbles 30 m either side 300 times,
 * in 6000 points: it needs about 600 points at 5 m, so its course is a full one.
 */
fun wobblyLoop(): Route {
    val centre = LatLon(59.3293, 18.0686)
    val radiusM = 10_000 / (2 * PI)
    val mPerDegLat = 111_320.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(centre.lat))
    val points = List(6_001) { i ->
        val a = 2 * PI * i / 6_000
        val r = radiusM + 30 * sin(a * 300)
        LatLon(centre.lat + r * sin(a) / mPerDegLat, centre.lon + r * cos(a) / mPerDegLon)
    }
    return Route(points, lengthM(points))
}

/** A route's length along its points, as a routing engine would report it (flat-earth). */
fun lengthM(points: List<LatLon>): Int {
    val mPerDegLat = 111_320.0
    return points.zipWithNext().sumOf { (a, b) ->
        val dx = (b.lon - a.lon) * mPerDegLat * cos(Math.toRadians(a.lat))
        val dy = (b.lat - a.lat) * mPerDegLat
        sqrt(dx * dx + dy * dy)
    }.toInt()
}

/** How far [p] lies from the polyline [line], in metres (flat-earth, fine at a few km). */
fun distanceToLineM(p: LatLon, line: List<LatLon>): Double {
    val mPerDegLat = 111_320.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(p.lat))
    fun xy(q: LatLon) = (q.lon - p.lon) * mPerDegLon to (q.lat - p.lat) * mPerDegLat
    return line.zipWithNext().minOf { (a, b) ->
        val (ax, ay) = xy(a)
        val (bx, by) = xy(b)
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val x = ax + t * dx
        val y = ay + t * dy
        sqrt(x * x + y * y)
    }
}
