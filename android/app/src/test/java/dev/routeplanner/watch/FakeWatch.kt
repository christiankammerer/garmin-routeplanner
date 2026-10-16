package dev.routeplanner.watch

import dev.routeplanner.course.Course
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Stands in for the watch and its link. By default the watch is connected, the watch app is open,
 * answers pings and acknowledges every course as stored; each property changes one of those.
 */
class FakeWatch : WatchLink {
    var connected = true
    var appOpen = true
    var transferFails = false

    /** How the watch acknowledges a course; null sends no acknowledgement. */
    var acknowledge: (Course) -> WatchMessage.Ack? = { WatchMessage.Ack(it.id, it.sequence, AckOutcome.Stored) }

    /** The courses the watch app received, oldest first. */
    val courses = mutableListOf<Course>()

    private val fromWatch = MutableSharedFlow<WatchMessage>(extraBufferCapacity = 16)
    override val messages: Flow<WatchMessage> = fromWatch

    private var acks = CompletableDeferred(Unit)

    /** Makes the watch hold back acknowledgements until [releaseAcks]. */
    fun holdAcks() {
        acks = CompletableDeferred()
    }

    fun releaseAcks() {
        acks.complete(Unit)
    }

    override suspend fun send(message: PhoneMessage): SendResult {
        if (!connected) return SendResult.NotConnected
        if (transferFails) return SendResult.Failed
        when (message) {
            PhoneMessage.Ping -> if (appOpen) fromWatch.emit(WatchMessage.Pong)
            is PhoneMessage.SendCourse -> if (appOpen) {
                courses += message.course
                acknowledge(message.course)?.let { ack ->
                    acks.await()
                    fromWatch.emit(ack)
                }
            }
        }
        return SendResult.Sent
    }
}
