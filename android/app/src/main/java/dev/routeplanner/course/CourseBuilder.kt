package dev.routeplanner.course

import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.PriorityQueue
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Points closer than this to the simplified line are dropped. */
private const val TOLERANCE_M = 5.0

/** The most points a course carries; a 500-point course fits one message with about 3× headroom (ADR 0002). */
const val MAX_COURSE_POINTS = 500

private const val EARTH_RADIUS_M = 6_371_000.0

/** One course point, in whole numbers only: microdegrees and metres along the route from its start. */
data class CoursePoint(val latE6: Int, val lonE6: Int, val cumM: Int)

/**
 * A route as the watch gets it. [id] is the same for the same route, so the watch can tell a resend;
 * [sequence] orders sends, so a newer one replaces an older one.
 */
data class Course(val id: String, val sequence: Int, val totalM: Int, val points: List<CoursePoint>)

/**
 * Builds the course for [route]: its line simplified at about 5 m and capped at [MAX_COURSE_POINTS].
 * The course is as long as the route says, the distance the phone shows; cumulative metres are
 * measured along the full-resolution line and scaled to that, so simplifying doesn't shorten it.
 */
fun buildCourse(route: Route, sequence: Int): Course {
    val measuredM = cumulativeM(route.points)
    val scale = if (measuredM.last() > 0) route.lengthM / measuredM.last() else 0.0
    val points = simplify(route.points).map { i ->
        CoursePoint(route.points[i].lat.toE6(), route.points[i].lon.toE6(), (measuredM[i] * scale).roundToInt())
    }
    return Course(id = geometryId(route.points), sequence = sequence, totalM = route.lengthM, points = points)
}

private fun Double.toE6() = (this * 1e6).roundToInt()

private fun cumulativeM(points: List<LatLon>): DoubleArray {
    val cum = DoubleArray(points.size)
    for (i in 1 until points.size) cum[i] = cum[i - 1] + haversineM(points[i - 1], points[i])
    return cum
}

private fun haversineM(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceAtMost(1.0)))
}

/**
 * Douglas–Peucker that always splits the worst segment first, so stopping at [MAX_COURSE_POINTS]
 * keeps the points that matter most, as if the tolerance had been raised. Returns kept indices.
 */
private fun simplify(points: List<LatLon>): List<Int> {
    if (points.size <= 2) return points.indices.toList()
    val origin = points.first()
    val mPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS_M
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(origin.lat))
    val x = DoubleArray(points.size) { (points[it].lon - origin.lon) * mPerDegLon }
    val y = DoubleArray(points.size) { (points[it].lat - origin.lat) * mPerDegLat }

    class Split(val from: Int, val to: Int, val at: Int, val offM: Double)

    fun worst(from: Int, to: Int): Split? {
        var at = -1
        var offM = 0.0
        for (i in from + 1 until to) {
            val d = segmentDistance(x[i], y[i], x[from], y[from], x[to], y[to])
            if (d > offM) {
                offM = d
                at = i
            }
        }
        return if (at < 0) null else Split(from, to, at, offM)
    }

    val kept = sortedSetOf(0, points.lastIndex)
    val queue = PriorityQueue<Split>(compareByDescending { it.offM })
    worst(0, points.lastIndex)?.let(queue::add)
    while (kept.size < MAX_COURSE_POINTS) {
        val split = queue.poll() ?: break
        if (split.offM <= TOLERANCE_M) break
        kept += split.at
        worst(split.from, split.at)?.let(queue::add)
        worst(split.at, split.to)?.let(queue::add)
    }
    return kept.toList()
}

private fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    val ex = ax + t * dx - px
    val ey = ay + t * dy - py
    return sqrt(ex * ex + ey * ey)
}

/** The first 8 hex digits of a SHA-256 over the route's points in microdegrees. */
private fun geometryId(points: List<LatLon>): String {
    val bytes = ByteBuffer.allocate(points.size * 8)
    for (p in points) bytes.putInt(p.lat.toE6()).putInt(p.lon.toE6())
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array())
    return digest.take(4).joinToString("") { "%02x".format(it) }
}
