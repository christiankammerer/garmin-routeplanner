package dev.routeplanner.watch

import dev.routeplanner.course.Course

/*
 * The phone ↔ watch message protocol, the contract between the two apps. Every message is a map
 * with its type under "t"; values are whole numbers and short strings only, never floats.
 * The watch side is watch/source/MessageHandler.mc; contract/ holds sample course messages for both.
 */

/** A message the phone sends to the watch app. */
sealed interface PhoneMessage {
    data object Ping : PhoneMessage

    data class SendCourse(val course: Course) : PhoneMessage
}

/** A message the watch app sends to the phone. */
sealed interface WatchMessage {
    data object Pong : WatchMessage

    /** The watch's answer to a course, echoing the course's [id] and [sequence]. */
    data class Ack(val id: String, val sequence: Int, val outcome: AckOutcome) : WatchMessage
}

enum class AckOutcome(val wire: String) {
    /** The watch now holds the course. */
    Stored("stored"),
}

fun PhoneMessage.toWire(): Map<String, Any> = when (this) {
    PhoneMessage.Ping -> mapOf("t" to "ping")
    is PhoneMessage.SendCourse -> mapOf(
        "t" to "course",
        "id" to course.id,
        "seq" to course.sequence,
        "dist" to course.totalM,
        // Flat [latE6, lonE6, cumulative metres, …]: one int list keeps the message small.
        "pts" to course.points.flatMap { listOf(it.latE6, it.lonE6, it.cumM) },
    )
}

/** Reads a message from the watch app; null when it isn't one this protocol knows. */
fun watchMessageFromWire(data: Any?): WatchMessage? {
    val wire = data as? Map<*, *> ?: return null
    return when (wire["t"]) {
        "pong" -> WatchMessage.Pong
        "ack" -> {
            val id = wire["id"] as? String ?: return null
            val sequence = (wire["seq"] as? Number)?.toInt() ?: return null
            val outcome = AckOutcome.entries.find { it.wire == wire["outcome"] } ?: return null
            WatchMessage.Ack(id, sequence, outcome)
        }
        else -> null
    }
}
