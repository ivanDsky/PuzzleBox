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
 * One journey: the app is brought to Today before every run, [block] is measured, and [setup] runs
 * first and unmeasured. The process lives on between runs (journeys measure interaction, startup
 * has its own benchmark), so [setup] and [block] each leave the app on the tab they started on.
 */
internal fun MacrobenchmarkRule.journey(
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
        pressHome()
        startActivityAndWait()
        ensureToday()
        setup()
    },
    measureBlock = block,
)
