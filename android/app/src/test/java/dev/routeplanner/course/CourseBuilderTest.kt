package dev.routeplanner.course

import dev.routeplanner.route.LatLon
import dev.routeplanner.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseBuilderTest {
    private val stockholmLoop = loadRoute("stockholm-10k-loop.txt")

    @Test
    fun `a real 10 km route simplifies to at most 500 points that stay on the route`() {
        val course = buildCourse(stockholmLoop, sequence = 1)

        assertTrue("${course.points.size} points", course.points.size in 2..500)
        assertTrue("${course.points.size} points", course.points.size < stockholmLoop.points.size / 2)
        for (point in stockholmLoop.points) {
            val off = distanceToLineM(point, course.points.map { it.toLatLon() })
            assertTrue("route point $point is $off m off the course", off <= 6.0)
        }
    }

    @Test
    fun `the course starts and ends where the route does`() {
        val course = buildCourse(stockholmLoop, sequence = 1)

        assertEquals(stockholmLoop.points.first().toE6(), course.points.first().toLatLon().toE6())
        assertEquals(stockholmLoop.points.last().toE6(), course.points.last().toLatLon().toE6())
    }

    @Test
    fun `the course is as long as the route, with cumulative metres from 0 to its total distance`() {
        val course = buildCourse(stockholmLoop, sequence = 1)

        // BRouter measured this route as 10262 m; the phone shows that, so the watch must too.
        assertEquals(10_262, course.totalM)
        assertEquals(0, course.points.first().cumM)
        assertEquals(course.totalM, course.points.last().cumM)
        course.points.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", b.cumM >= a.cumM) }
    }

    @Test
    fun `a dense route is capped at 500 points`() {
        // 5000 points zig-zagging 20 m either side of a line: every corner is worth keeping at 5 m.
        val corners = List(5_000) { i -> LatLon(59.0 + i * 0.0002, 18.0 + (if (i % 2 == 0) 0.0 else 0.0007)) }
        val zigzag = Route(corners, lengthM(corners))

        val course = buildCourse(zigzag, sequence = 1)

        assertEquals(500, course.points.size)
        assertEquals(zigzag.points.first().toE6(), course.points.first().toLatLon().toE6())
        assertEquals(zigzag.points.last().toE6(), course.points.last().toLatLon().toE6())
    }

    @Test
    fun `the same route gives the same course id, a different route a different one`() {
        val again = Route(stockholmLoop.points.toList(), stockholmLoop.lengthM)
        val other = Route(stockholmLoop.points.drop(10), stockholmLoop.lengthM)

        assertEquals(buildCourse(stockholmLoop, 1).id, buildCourse(again, 2).id)
        assertTrue(buildCourse(stockholmLoop, 1).id != buildCourse(other, 1).id)
    }

    @Test
    fun `the course carries the sequence number it was built with`() {
        assertEquals(1_760_000_000, buildCourse(stockholmLoop, sequence = 1_760_000_000).sequence)
    }
}

private fun CoursePoint.toLatLon() = LatLon(latE6 / 1e6, lonE6 / 1e6)

private fun LatLon.toE6() = Math.round(lat * 1e6) to Math.round(lon * 1e6)
