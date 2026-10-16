package dev.routeplanner.watch

import kotlinx.coroutines.flow.Flow

/** How a send went on the phone's side; whether the watch app got it shows only in its reply. */
enum class SendResult {
    Sent,
    NotConnected,
    Failed,
}

/** The link to the watch app (the Connect IQ Mobile SDK, through Garmin Connect Mobile). */
interface WatchLink {
    /** Messages from the watch app as they arrive; only those sent while collecting are seen. */
    val messages: Flow<WatchMessage>

    suspend fun send(message: PhoneMessage): SendResult
}
