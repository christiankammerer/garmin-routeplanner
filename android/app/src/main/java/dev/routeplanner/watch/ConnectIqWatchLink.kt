package dev.routeplanner.watch

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** The watch app's name, as the runner sees it on the watch. */
const val WATCH_APP_NAME = "Route Runner"

/** The watch app's id; must match the id in watch/manifest.xml. */
const val WATCH_APP_ID = "5eb1b417be064f7a8f031ce160e9b8ab"

/** The real watch link: the Connect IQ Mobile SDK, which talks to the watch through Garmin Connect Mobile. */
class ConnectIqWatchLink(context: Context) : WatchLink {
    private val context = context.applicationContext
    private val ciq = ConnectIQ.getInstance(this.context, ConnectIQ.IQConnectType.WIRELESS)
    private val app = IQApp(WATCH_APP_ID)
    private val setUp = Mutex()
    private var sdkReady = false
    private var listeningTo: IQDevice? = null

    private val fromWatch = MutableSharedFlow<WatchMessage>(extraBufferCapacity = 16)
    override val messages = fromWatch.asSharedFlow()

    override suspend fun send(message: PhoneMessage): SendResult {
        val device = connectedDevice() ?: return SendResult.NotConnected
        val status = suspendCancellableCoroutine { cont ->
            ciq.sendMessage(device, app, message.toWire()) { _, _, status ->
                if (cont.isActive) cont.resume(status)
            }
        }
        return if (status == ConnectIQ.IQMessageStatus.SUCCESS) SendResult.Sent else SendResult.Failed
    }

    /** The connected watch, listening to its watch app; null when the SDK or the watch isn't there. */
    private suspend fun connectedDevice(): IQDevice? = setUp.withLock {
        if (!sdkReady) sdkReady = initialize()
        if (!sdkReady) return null
        val device = ciq.connectedDevices?.firstOrNull() ?: return null
        if (device.deviceIdentifier != listeningTo?.deviceIdentifier) {
            ciq.registerForAppEvents(device, app) { _, _, message, _ ->
                message.firstNotNullOfOrNull(::watchMessageFromWire)?.let(fromWatch::tryEmit)
            }
            listeningTo = device
        }
        device
    }

    /** False when Garmin Connect Mobile is missing or too old. */
    private suspend fun initialize(): Boolean = suspendCancellableCoroutine { cont ->
        ciq.initialize(context, false, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                if (cont.isActive) cont.resume(true)
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                if (cont.isActive) cont.resume(false)
            }

            override fun onSdkShutDown() {
                sdkReady = false
                listeningTo = null
            }
        })
    }
}
