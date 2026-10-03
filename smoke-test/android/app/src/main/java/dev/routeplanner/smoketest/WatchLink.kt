package dev.routeplanner.smoketest

import android.content.Context
import android.os.SystemClock
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Must match the id in smoke-test/watch/manifest.xml. */
const val WATCH_APP_ID = "6326862b9242768809312e69fd68aa1a"

private const val ACK_TIMEOUT_MS = 60_000L

data class ProbeResult(
    val ok: Boolean,
    val status: ConnectIQ.IQMessageStatus?,
    val sendCallbackMs: Long,
    val ackMs: Long?,
)

class WatchLink(private val context: Context, private val log: (String) -> Unit) {
    private val ciq = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
    private val app = IQApp(WATCH_APP_ID)
    private var ready = false
    private var device: IQDevice? = null
    private var seq = 0
    private val pendingAcks = ConcurrentHashMap<Int, CompletableDeferred<Map<*, *>>>()

    suspend fun connect(): Boolean {
        if (!ready) {
            ready = suspendCancellableCoroutine { cont ->
                ciq.initialize(context, true, object : ConnectIQ.ConnectIQListener {
                    override fun onSdkReady() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                        log("SDK init error: $status")
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onSdkShutDown() {
                        ready = false
                    }
                })
            }
            if (!ready) return false
        }
        val d = ciq.connectedDevices?.firstOrNull()
        if (d == null) {
            log("No connected Garmin device (known: ${ciq.knownDevices?.map { it.friendlyName }})")
            return false
        }
        device = d
        log("Device: ${d.friendlyName} (${ciq.getDeviceStatus(d)})")
        ciq.registerForDeviceEvents(d) { dev, status -> log("Device ${dev.friendlyName} -> $status") }
        ciq.registerForAppEvents(d, app) { _, _, message, _ ->
            val ack = message.firstOrNull() as? Map<*, *>
            val ackSeq = (ack?.get("seq") as? Number)?.toInt()
            val waiting = ackSeq?.let { pendingAcks.remove(it) }
            if (waiting != null) waiting.complete(ack) else log("Unmatched message from watch: $message")
        }
        return true
    }

    suspend fun appInfo(): String = suspendCancellableCoroutine { cont ->
        ciq.getApplicationInfo(WATCH_APP_ID, requireDevice(), object : ConnectIQ.IQApplicationInfoListener {
            override fun onApplicationInfoReceived(app: IQApp) {
                if (cont.isActive) cont.resume("installed, status=${app.status}, version=${app.version()}")
            }

            override fun onApplicationNotInstalled(applicationId: String) {
                if (cont.isActive) cont.resume("NOT installed")
            }
        })
    }

    /** Returns the open status and how long the phone waited for it. */
    suspend fun openApp(): Pair<ConnectIQ.IQOpenApplicationStatus, Long> {
        val t0 = SystemClock.elapsedRealtime()
        val status = suspendCancellableCoroutine { cont ->
            ciq.openApplication(requireDevice(), app) { _, _, status ->
                if (cont.isActive) cont.resume(status)
            }
        }
        return status to SystemClock.elapsedRealtime() - t0
    }

    suspend fun ping(): ProbeResult = exchange(mapOf("t" to "ping"))

    /** Sends one probe and checks the watch's ack against what was sent. */
    suspend fun probe(values: List<Number>, enc: String): ProbeResult {
        val result = exchange(mapOf("t" to "probe", "enc" to enc, "pts" to values)) { ack ->
            val n = (ack["n"] as? Number)?.toInt()
            val checks = mutableListOf("n=${n ?: "?"}/${values.size}")
            if (enc == "int") {
                val expected = values.sumOf { it.toLong() }.toString()
                checks += if (ack["sum"] == expected) "sum ok" else "sum MISMATCH (${ack["sum"]} vs $expected)"
            } else {
                checks += "first sent=${"%.6f".format(values.first().toDouble())} watch=${ack["first"]}"
            }
            checks += "stored=${ack["stored"]}"
            checks += "mem=${(ack["usedMem"] as? Number)?.toInt()?.div(1024)}/${(ack["totalMem"] as? Number)?.toInt()?.div(1024)} KB"
            checks += "watchMsSinceStart=${ack["msSinceStart"]}"
            log("   " + checks.joinToString(", "))
            n == values.size && (enc != "int" || ack["sum"] == values.sumOf { it.toLong() }.toString())
        }
        return result
    }

    private suspend fun exchange(
        body: Map<String, Any>,
        verify: (Map<*, *>) -> Boolean = { true },
    ): ProbeResult {
        val d = requireDevice()
        val s = ++seq
        val ackWait = CompletableDeferred<Map<*, *>>()
        pendingAcks[s] = ackWait
        val payload = body + ("seq" to s)
        val t0 = SystemClock.elapsedRealtime()
        val status = suspendCancellableCoroutine { cont ->
            ciq.sendMessage(d, app, payload) { _, _, status ->
                if (cont.isActive) cont.resume(status)
            }
        }
        val tSent = SystemClock.elapsedRealtime() - t0
        val ack = if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
            withTimeoutOrNull(ACK_TIMEOUT_MS) { ackWait.await() }
        } else {
            null
        }
        pendingAcks.remove(s)
        val tAck = ack?.let { SystemClock.elapsedRealtime() - t0 }
        log("#$s ${body["t"]}: send=$status in ${tSent} ms, ack=${tAck?.let { "$it ms" } ?: "NONE"}")
        val ok = ack != null && verify(ack)
        return ProbeResult(ok, status, tSent, tAck)
    }

    private fun requireDevice(): IQDevice = device ?: error("Tap Connect first")
}
