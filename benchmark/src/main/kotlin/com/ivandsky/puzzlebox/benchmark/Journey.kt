package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry

/** Journeys run this many times: enough for a P50/P90/P99 over the frames of every run. */
private const val JOURNEY_ITERATIONS = 5

/**
 * The default compilation: Partial with the profile used when the APK has one. The benchmark build
 * ships none until the S-01 fix lands, so today this warms up, then compiles what ran; once a
 * profile ships, the same tests measure it with no edit.
 */
internal fun partialCompilation(): CompilationMode =
    CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.UseIfAvailable, warmupIterations = 3)

/**
 * What a journey compiles with: None unless `-Pandroid.testInstrumentationRunnerArguments.compilation=partial`.
 * Partial with no shipped profile warms up and then compiles the whole app, minutes per test;
 * None resets to the install-time state, which is what a user without a profile runs.
 */
internal fun journeyCompilation(): CompilationMode =
    when (InstrumentationRegistry.getArguments().getString("compilation")) {
        "partial" -> partialCompilation()
        else -> CompilationMode.None()
    }

/**
 * The daily data a journey starts from. Every journey names one, and the README table lists them.
 * Both clear the benchmark package's app data first, so no run inherits the last run's puzzles,
 * sessions or tutorials.
 */
internal sealed interface DataState {
    /**
     * Cleared, then launched to Today. The Today launch syncs today's puzzle for every game (the
     * app's prefetch, when online), so each game's today row is stored. No game is opened and no
     * tutorial is seen, so the first Play of any game shows its tutorial.
     */
    data object Cleared : DataState

    /**
     * Cleared, then [game] opened once with its tutorial skipped: today's puzzle for [game] is
     * stored and its tutorial is seen, as a player who has played it once finds it.
     */
    data class Opened(val game: BenchmarkGame) : DataState
}

/**
 * Wipes the benchmark package's app data with `pm clear`. The instrumentation's shell can run it
 * on the release-like benchmark build, which is not debuggable, so `run-as` is not an option.
 */
internal fun MacrobenchmarkScope.clearAppData() {
    val result = device.executeShellCommand("pm clear $TARGET_PACKAGE").trim()
    check(result == "Success") { "pm clear $TARGET_PACKAGE failed: $result" }
}

/** Puts [data] in place once the app is on Today; [setup] runs after it. */
internal fun MacrobenchmarkScope.prepare(data: DataState) = when (data) {
    DataState.Cleared -> Unit
    is DataState.Opened -> skipTutorial(data.game)
}

/**
 * One journey: the app is cleared to [data] before every run, [block] is measured, and [setup] runs
 * first and unmeasured. The process lives on between runs (journeys measure interaction, startup
 * has its own benchmark), so [setup] and [block] each leave the app on the tab they started on.
 */
internal fun MacrobenchmarkRule.journey(
    data: DataState = DataState.Cleared,
    compilationMode: CompilationMode = journeyCompilation(),
    metrics: List<Metric> = listOf(FrameTimingMetric()),
    setup: MacrobenchmarkScope.() -> Unit = {},
    block: MacrobenchmarkScope.() -> Unit,
) = measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = metrics,
    compilationMode = compilationMode,
    iterations = JOURNEY_ITERATIONS,
    startupMode = null,
    setupBlock = {
        clearAppData()
        pressHome()
        startActivityAndWait()
        ensureToday()
        prepare(data)
        setup()
    },
    measureBlock = block,
)
