package dev.routeplanner.route

import kotlinx.coroutines.CompletableDeferred
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stands in for BRouter. A round trip's length comes from [lengthM] for the radius and direction
 * asked for, the way a real area answers; or every call fails with [problem].
 */
class FakeRoutingEngine private constructor(
    private val lengthM: (radiusM: Int, directionDeg: Int) -> Int,
    private val problem: RoutingProblem?,
) : RoutingEngine {
    private var answers = CompletableDeferred(Unit)

    /** Makes calls wait until [releaseAnswers], so a test can see what happens while routing runs. */
    fun holdAnswers() {
        answers = CompletableDeferred()
    }

    fun releaseAnswers() {
        answers.complete(Unit)
    }

    override suspend fun loop(start: LatLon, radiusM: Int, directionDeg: Int): EngineResult {
        answers.await()
        problem?.let { return EngineResult.Failure(it) }
        val length = lengthM(radiusM, directionDeg)
        return EngineResult.Success(Route(loopAround(start, length), length))
    }

    companion object {
        fun answering(lengthM: (radiusM: Int, directionDeg: Int) -> Int) = FakeRoutingEngine(lengthM, null)

        fun failingWith(problem: RoutingProblem) = FakeRoutingEngine({ _, _ -> 0 }, problem)
    }
}

/** A circle through [start] whose circumference is [lengthM]. */
private fun loopAround(start: LatLon, lengthM: Int): List<LatLon> {
    val radiusM = lengthM / (2 * PI)
    val mPerDegLat = 111_320.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(start.lat))
    return List(25) { i ->
        val a = 2 * PI * i / 24
        LatLon(start.lat + radiusM * sin(a) / mPerDegLat, start.lon + radiusM * (cos(a) - 1) / mPerDegLon)
    }
}
