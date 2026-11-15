package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_ITERATIONS = 10

/**
 * Cold and warm start to Today's content, with the default Partial compilation and with None (the
 * un-warmed floor a fresh install sees). timeToInitialDisplayMs is the number to read; the frame
 * metrics cover the first screens after it.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldPartial() = startup(StartupMode.COLD, partialCompilation())

    @Test
    fun coldNone() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun warmPartial() = startup(StartupMode.WARM, partialCompilation())

    @Test
    fun warmNone() = startup(StartupMode.WARM, CompilationMode.None())

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = compilation,
        iterations = STARTUP_ITERATIONS,
        startupMode = mode,
        setupBlock = {
            // A warm start resumes whichever tab the last run left; the measured end is Today.
            if (mode == StartupMode.WARM) {
                startActivityAndWait()
                ensureToday()
            }
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitFor(BenchmarkTab.Today.ready)
    }
}
