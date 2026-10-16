package dev.routeplanner.watch

import dev.routeplanner.course.Course
import dev.routeplanner.course.CoursePoint
import dev.routeplanner.course.buildCourse
import dev.routeplanner.course.loadRoute
import dev.routeplanner.course.readResource
import dev.routeplanner.course.wobblyLoop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sample course messages are built with this sequence number. */
private const val SAMPLE_SEQUENCE = 1_760_000_000

class ProtocolTest {
    private val normalCourse = buildCourse(loadRoute("stockholm-10k-loop.txt"), SAMPLE_SEQUENCE)
    private val fullCourse = buildCourse(wobblyLoop(), SAMPLE_SEQUENCE)

    @Test
    fun `the course builder produces the shared sample course messages`() {
        assertEquals(sample("course-normal.json"), PhoneMessage.SendCourse(normalCourse).toWire())
        assertEquals(sample("course-500.json"), PhoneMessage.SendCourse(fullCourse).toWire())
    }

    @Test
    fun `the 500-point sample is a full course`() {
        assertEquals(500, fullCourse.points.size)
    }

    @Test
    fun `a course message holds whole numbers and short strings only`() {
        val wire = PhoneMessage.SendCourse(normalCourse).toWire()

        for ((key, value) in wire) {
            when (value) {
                is String -> assertTrue("$key is ${value.length} characters", value.length <= 16)
                is Int -> {}
                is List<*> -> assertTrue("$key holds ${value.filter { it !is Int }}", value.all { it is Int })
                else -> throw AssertionError("$key is a ${value.javaClass}")
            }
        }
    }

    @Test
    fun `encoding then decoding a course gives the same course back`() {
        for (course in listOf(normalCourse, fullCourse)) {
            assertEquals(course, courseFromWire(PhoneMessage.SendCourse(course).toWire()))
        }
    }

    @Test
    fun `a ping is a message with only its type`() {
        assertEquals(mapOf("t" to "ping"), PhoneMessage.Ping.toWire())
    }

    @Test
    fun `reads the watch's pong and acknowledgement`() {
        assertEquals(WatchMessage.Pong, watchMessageFromWire(mapOf("t" to "pong")))
        assertEquals(
            WatchMessage.Ack(id = "8a1f03c2", sequence = 7, outcome = AckOutcome.Stored),
            watchMessageFromWire(mapOf("t" to "ack", "id" to "8a1f03c2", "seq" to 7, "outcome" to "stored")),
        )
    }

    @Test
    fun `ignores what it can't read`() {
        assertNull(watchMessageFromWire("hello"))
        assertNull(watchMessageFromWire(mapOf("t" to "bogus")))
        assertNull(watchMessageFromWire(mapOf("t" to "ack", "id" to "8a1f03c2")))
        assertNull(watchMessageFromWire(mapOf("t" to "ack", "id" to "8a1f03c2", "seq" to 7, "outcome" to "eaten")))
    }
}

@Suppress("UNCHECKED_CAST")
private fun sample(name: String) = Json.parseToJsonElement(readResource(name)).toValue() as Map<String, Any?>

/** JSON as the plain maps, lists, strings and ints a message is made of. */
private fun JsonElement.toValue(): Any? = when (this) {
    is JsonObject -> mapValues { it.value.toValue() }
    is JsonArray -> map { it.toValue() }
    is JsonPrimitive -> if (isString) content else int
}

/** Reads a course message back, the way the watch does. */
private fun courseFromWire(wire: Map<String, Any?>): Course {
    val flat = (wire["pts"] as List<*>).map { (it as Number).toInt() }
    return Course(
        id = wire["id"] as String,
        sequence = (wire["seq"] as Number).toInt(),
        totalM = (wire["dist"] as Number).toInt(),
        points = flat.chunked(3) { (lat, lon, cum) -> CoursePoint(lat, lon, cum) },
    )
}
