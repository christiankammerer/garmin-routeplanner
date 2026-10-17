package dev.routeplanner.watch

import dev.routeplanner.course.Course
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PING_TIMEOUT_MS = 3_000L

/** An acknowledgement normally takes 1.5–2.3 s after the send. */
private const val ACK_TIMEOUT_MS = 10_000L

enum class DeliveryProblem {
    NotConnected,

    /** The watch app didn't answer a ping, so it isn't open. */
    AppNotOpen,
    SendFailed,
    NoAcknowledgement,

    /** The watch is recording a run and won't change course mid-run. */
    WatchRecording,
}

sealed interface DeliveryOutcome {
    data class Delivered(val course: Course) : DeliveryOutcome

    data class Failed(val problem: DeliveryProblem) : DeliveryOutcome
}

/**
 * Gets a course onto the watch: complete only when the watch confirms it holds the course.
 * The watch app must already be open; opening it and retrying come later.
 */
class Delivery(private val watch: WatchLink) {
    suspend fun deliver(course: Course): DeliveryOutcome {
        exchange(PhoneMessage.Ping, PING_TIMEOUT_MS) { it == WatchMessage.Pong }
            .problem(ifUnanswered = DeliveryProblem.AppNotOpen)
            ?.let { return DeliveryOutcome.Failed(it) }
        val ack = exchange(PhoneMessage.SendCourse(course), ACK_TIMEOUT_MS) {
            it is WatchMessage.Ack && it.id == course.id && it.sequence == course.sequence
        }
        ack.problem(ifUnanswered = DeliveryProblem.NoAcknowledgement)?.let { return DeliveryOutcome.Failed(it) }
        return when (((ack as Exchange.Answered).reply as WatchMessage.Ack).outcome) {
            AckOutcome.Stored -> DeliveryOutcome.Delivered(course)
            AckOutcome.Busy -> DeliveryOutcome.Failed(DeliveryProblem.WatchRecording)
        }
    }

    private sealed interface Exchange {
        data class Answered(val reply: WatchMessage) : Exchange

        data object Unanswered : Exchange

        data object NotConnected : Exchange

        data object SendFailed : Exchange
    }

    private fun Exchange.problem(ifUnanswered: DeliveryProblem) = when (this) {
        is Exchange.Answered -> null
        Exchange.Unanswered -> ifUnanswered
        Exchange.NotConnected -> DeliveryProblem.NotConnected
        Exchange.SendFailed -> DeliveryProblem.SendFailed
    }

    /** Sends [message] and waits up to [timeoutMs] for a reply. Listens first, so a quick reply isn't missed. */
    private suspend fun exchange(message: PhoneMessage, timeoutMs: Long, isReply: (WatchMessage) -> Boolean): Exchange =
        coroutineScope {
            val reply = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(timeoutMs) { watch.messages.first(isReply) }
            }
            when (watch.send(message)) {
                SendResult.Sent -> reply.await()?.let(Exchange::Answered) ?: Exchange.Unanswered
                SendResult.NotConnected -> Exchange.NotConnected.also { reply.cancel() }
                SendResult.Failed -> Exchange.SendFailed.also { reply.cancel() }
            }
        }
}
