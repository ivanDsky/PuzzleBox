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
 * ships the committed baseline profile, so this compiles it after the warmup; a run with
 * `-Ppuzzlebox.baselineProfileGeneration` has no R8, so never measure with it.
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
 * The daily data a journey starts from. Every run through [journey] has one (default [OnToday]); the
 * README table lists them. Nothing clears the app's data: each state is reached from whatever the
 * last run left.
 */
internal sealed interface DataState {
    /**
     * Launched to Today and nothing more. The Today launch starts syncing today's puzzle for every
     * game (the app's prefetch, when online); nothing waits for it, so the sync may still be running.
     * No game is opened here.
     */
    data object OnToday : DataState

    /**
     * Launched to Today, then [game] opened with its tutorial skipped: today's puzzle for [game] is
     * stored and its tutorial is seen. Idempotent: a tutorial already seen is not shown again.
     */
    data class Opened(val game: BenchmarkGame) : DataState

    /**
     * Launched to Today with two years of solved dailies in the install's history (the app's benchmark-build
     * seed provider, [seedHistory]): the Insights hub lists year and month tiles. The seed skips the newest four
     * months, so today's puzzles stay unsolved, but it stays in the install: run the journeys that expect an
     * empty history before this one, or on a fresh install.
     */
    data object SeededHistory : DataState
}

/** Puts [data] in place once the app is on Today; [setup] runs after it. */
internal fun MacrobenchmarkScope.prepare(data: DataState) = when (data) {
    DataState.OnToday -> Unit
    is DataState.Opened -> skipTutorial(data.game)
    DataState.SeededHistory -> seedHistory()
}

/**
 * One journey: [data] is set up before every run, [block] is measured, and [setup] runs first and
 * unmeasured. Nothing clears the data between runs, so each [data] state is set up idempotently from
 * whatever the last run left. The process lives on between runs (journeys measure interaction,
 * startup has its own benchmark), so [setup] and [block] each leave the app on the tab they started on.
 */
internal fun MacrobenchmarkRule.journey(
    data: DataState = DataState.OnToday,
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
        prepare(data)
        setup()
    },
    measureBlock = block,
)
