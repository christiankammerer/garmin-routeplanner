package dev.routeplanner.smoketest

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

fun distanceM(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
}

fun lengthM(points: List<LatLon>): Double =
    points.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }

fun cumulativeM(points: List<LatLon>): List<Int> {
    var acc = 0.0
    return points.mapIndexed { i, p ->
        if (i > 0) acc += distanceM(points[i - 1], p)
        acc.toInt()
    }
}

/** A wobbly ~10 km loop around [centre] with exactly [n] points, standing in for a real course. */
fun syntheticCourse(centre: LatLon, n: Int): List<LatLon> {
    val radiusM = 10_000.0 / (2 * PI)
    val mPerDegLat = 111_320.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(centre.lat))
    return List(n) { i ->
        val a = 2 * PI * i / n
        val r = radiusM + 40 * sin(a * 17)
        LatLon(centre.lat + r * sin(a) / mPerDegLat, centre.lon + r * cos(a) / mPerDegLon)
    }
}

/** Compact encoding: flat ints [latE6, lonE6, cumulativeM, ...]. */
fun encodeInts(points: List<LatLon>): List<Int> {
    val cum = cumulativeM(points)
    return points.flatMapIndexed { i, p -> listOf((p.lat * 1e6).toInt(), (p.lon * 1e6).toInt(), cum[i]) }
}

/** Naive encoding: flat doubles [lat, lon, cumulativeM, ...]. */
fun encodeDoubles(points: List<LatLon>): List<Double> {
    val cum = cumulativeM(points)
    return points.flatMapIndexed { i, p -> listOf(p.lat, p.lon, cum[i].toDouble()) }
}

/** Douglas–Peucker in a local metric projection. */
fun simplify(points: List<LatLon>, toleranceM: Double): List<LatLon> {
    if (points.size < 3) return points
    val ref = points.first()
    val mPerDegLon = 111_320.0 * cos(Math.toRadians(ref.lat))
    val xy = points.map { Pair((it.lon - ref.lon) * mPerDegLon, (it.lat - ref.lat) * 111_320.0) }
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
        val (s, e) = stack.removeLast()
        var maxD = 0.0
        var idx = -1
        for (i in s + 1 until e) {
            val d = segmentDistance(xy[i], xy[s], xy[e])
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        if (idx >= 0 && maxD > toleranceM) {
            keep[idx] = true
            stack.addLast(s to idx)
            stack.addLast(idx to e)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

private fun segmentDistance(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dx = b.first - a.first
    val dy = b.second - a.second
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
    val px = a.first + t * dx - p.first
    val py = a.second + t * dy - p.second
    return sqrt(px * px + py * py)
}
