package dev.routeplanner.watch

import dev.routeplanner.course.buildCourse
import dev.routeplanner.course.loadRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryTest {
    private val course = buildCourse(loadRoute("stockholm-10k-loop.txt"), sequence = 1_760_000_000)
    private val watch = FakeWatch()
    private val delivery = Delivery(watch)

    @Test
    fun `delivers a course to the open watch app once the watch acknowledges it`() = runTest {
        val outcome = delivery.deliver(course)

        assertEquals(DeliveryOutcome.Delivered(course), outcome)
        assertEquals(listOf(course), watch.courses)
    }

    @Test
    fun `tells when the watch isn't connected`() = runTest {
        watch.connected = false

        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.NotConnected), delivery.deliver(course))
    }

    @Test
    fun `gives up after 3 seconds without a ping reply, without sending the course`() = runTest {
        watch.appOpen = false

        val outcome = delivery.deliver(course)

        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.AppNotOpen), outcome)
        assertEquals(3_000L, currentTime)
        assertTrue(watch.courses.isEmpty())
    }

    @Test
    fun `tells when the transfer fails`() = runTest {
        watch.transferFails = true

        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.SendFailed), delivery.deliver(course))
    }

    @Test
    fun `fails after 10 seconds without an acknowledgement`() = runTest {
        watch.acknowledge = { null }

        val outcome = delivery.deliver(course)

        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.NoAcknowledgement), outcome)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun `ignores an acknowledgement for a different send`() = runTest {
        watch.acknowledge = { WatchMessage.Ack(it.id, it.sequence - 1, AckOutcome.Stored) }
        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.NoAcknowledgement), delivery.deliver(course))

        watch.acknowledge = { WatchMessage.Ack("0badc0de", it.sequence, AckOutcome.Stored) }
        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.NoAcknowledgement), delivery.deliver(course))
    }

    @Test
    fun `tells when the watch refuses the course because it is recording a run`() = runTest {
        watch.acknowledge = { WatchMessage.Ack(it.id, it.sequence, AckOutcome.Busy) }

        assertEquals(DeliveryOutcome.Failed(DeliveryProblem.WatchRecording), delivery.deliver(course))
    }
}
