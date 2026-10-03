package dev.routeplanner.smoketest

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val SWEEP_POINTS = listOf(100, 250, 500, 750, 1000, 1500, 2000, 3000)

class MainActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()
    private lateinit var watch: WatchLink
    private lateinit var brouter: BRouterTimer
    private var lastRoute: List<LatLon>? = null
    private var job: Job? = null

    private fun log(line: String) {
        val stamped = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + " " + line
        Log.i("CourseSmoke", stamped)
        runOnUiThread { lines += stamped }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (job?.isActive == true) {
            log("Busy, wait for the current test")
            return
        }
        job = lifecycleScope.launch {
            log("== $label")
            try {
                block()
            } catch (e: Exception) {
                log("FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watch = WatchLink(applicationContext, ::log)
        brouter = BRouterTimer(applicationContext, ::log)
        setContent {
            var lat by remember { mutableStateOf("59.3293") }
            var lon by remember { mutableStateOf("18.0686") }
            var km by remember { mutableStateOf("10") }
            var profile by remember { mutableStateOf("") }
            val start = { LatLon(lat.toDouble(), lon.toDouble()) }
            val listState = rememberLazyListState()
            LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Watch", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button({ run("Connect") { watch.connect() } }) { Text("Connect") }
                            Button({ run("App info") { log(watch.appInfo()) } }) { Text("App info") }
                            Button({ run("Open app") { val (s, ms) = watch.openApp(); log("openApplication -> $s in $ms ms") } }) { Text("Open app") }
                            Button({ run("Ping x10") { pingTen() } }) { Text("Ping x10") }
                            Button({ run("Send 500 pts") { watch.probe(encodeInts(syntheticCourse(start(), 500)), "int") } }) { Text("Send 500") }
                            Button({ run("Sweep int") { sweep(start(), "int") } }) { Text("Sweep int") }
                            Button({ run("Sweep double") { sweep(start(), "dbl") } }) { Text("Sweep dbl") }
                        }
                        Text("BRouter", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(lat, { lat = it }, Modifier.weight(1f), label = { Text("lat") })
                            OutlinedTextField(lon, { lon = it }, Modifier.weight(1f), label = { Text("lon") })
                            OutlinedTextField(km, { km = it }, Modifier.weight(0.6f), label = { Text("km") })
                        }
                        OutlinedTextField(profile, { profile = it }, label = { Text("profile (blank = v=foot)") })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button({ run("3 candidates, sequential") { candidates(start(), km.toDouble(), profile, false) } }) { Text("3 seq") }
                            Button({ run("3 candidates, parallel") { candidates(start(), km.toDouble(), profile, true) } }) { Text("3 par") }
                            Button({ run("Send last route") { sendLastRoute() } }) { Text("Send route") }
                            Button({ copyLog() }) { Text("Copy log") }
                        }
                        LazyColumn(state = listState) {
                            items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun pingTen() {
        val times = (1..10).mapNotNull { watch.ping().ackMs }
        if (times.isEmpty()) {
            log("No acks")
            return
        }
        val sorted = times.sorted()
        log("Ping acks ${times.size}/10: min ${sorted.first()} ms, median ${sorted[sorted.size / 2]} ms, max ${sorted.last()} ms")
    }

    private suspend fun sweep(start: LatLon, enc: String) {
        var failures = 0
        for (n in SWEEP_POINTS) {
            val course = syntheticCourse(start, n)
            val values: List<Number> = if (enc == "int") encodeInts(course) else encodeDoubles(course)
            log("-- $n points ($enc, ${values.size} values)")
            val r = watch.probe(values, enc)
            log("   => ${if (r.ok) "OK" else "FAIL"}")
            failures = if (r.ok) 0 else failures + 1
            if (failures == 2) break
        }
    }

    private suspend fun candidates(start: LatLon, km: Double, profile: String, parallel: Boolean) {
        val results = brouter.candidates(start, km * 1000, profile, parallel)
        lastRoute = results.filterNotNull().firstOrNull()?.points
    }

    private suspend fun sendLastRoute() {
        val route = lastRoute ?: error("Generate candidates first")
        for (tol in listOf(2.0, 5.0, 10.0)) {
            log("Simplified at $tol m: ${route.size} -> ${simplify(route, tol).size} points")
        }
        val course = simplify(route, 5.0)
        log("Sending the 5 m version (${course.size} points)")
        watch.probe(encodeInts(course), "int")
    }

    private fun copyLog() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("smoke log", lines.joinToString("\n")))
        log("Log copied to clipboard")
    }
}
