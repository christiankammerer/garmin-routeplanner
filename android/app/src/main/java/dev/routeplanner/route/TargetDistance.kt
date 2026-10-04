package dev.routeplanner.route

private const val STEP_M = 500
private const val MIN_M = 1_000
private const val MAX_M = 50_000

/** The shortest the runner wants the route to be. */
@JvmInline
value class TargetDistance private constructor(val metres: Int) {
    fun longer() = ofMetres(metres + STEP_M)

    fun shorter() = ofMetres(metres - STEP_M)

    companion object {
        val Default = ofMetres(10_000)

        val QuickPicks = listOf(5_000, 8_000, 10_000, 15_000, 21_100).map(::ofMetres)

        fun ofMetres(metres: Int) = TargetDistance(metres.coerceIn(MIN_M, MAX_M))
    }
}
