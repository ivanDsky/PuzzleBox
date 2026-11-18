package com.ivandsky.puzzlebox.profile.whatsnew

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WhatsNewViewModelTest {

    private val main = StandardTestDispatcher()
    private val io = StandardTestDispatcher(main.scheduler)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(main)

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = this@WhatsNewViewModelTest.main
        override val io: CoroutineDispatcher = this@WhatsNewViewModelTest.io
        override val default: CoroutineDispatcher = this@WhatsNewViewModelTest.io
    }

    private val warnings = mutableListOf<String>()

    private fun vm(releases: () -> List<WhatsNewRelease>) = WhatsNewViewModel(
        source = object : WhatsNewSource {
            override fun releases() = releases()
        },
        dispatchers = dispatchers,
        failures = { _, message, _ -> warnings += message },
    )

    private fun release(version: String, date: String) = WhatsNewRelease(version, date, listOf("note $version"))

    @Test
    fun `three releases are listed in the source's order with worded dates`() = runTest(main) {
        val vm = vm {
            listOf(release("0.36.2", "2026-11-18"), release("0.35.0", "2026-11-18"), release("0.34.3", "2026-11-17"))
        }
        advanceUntilIdle()

        assertEquals(
            WhatsNewUiState(
                listOf(
                    WhatsNewCard("0.36.2", "18 Nov 2026", listOf("note 0.36.2")),
                    WhatsNewCard("0.35.0", "18 Nov 2026", listOf("note 0.35.0")),
                    WhatsNewCard("0.34.3", "17 Nov 2026", listOf("note 0.34.3")),
                ),
            ),
            vm.uiState.value,
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `until the read ends the state is empty and not failed, and the source is not touched on the caller's thread`() = runTest(main) {
        var reads = 0
        val vm = vm { reads++; listOf(release("0.36.2", "2026-11-18")) }

        assertEquals(WhatsNewUiState(), vm.uiState.value)
        assertEquals(0, reads)

        advanceUntilIdle()

        assertEquals(1, reads)
        assertEquals(1, vm.uiState.value.releases.size)
    }

    @Test
    fun `a source that throws shows the load-failed state and reports the cause`() = runTest(main) {
        val vm = vm { throw FileNotFoundException("whats_new.txt") }
        advanceUntilIdle()

        assertEquals(WhatsNewUiState(releases = emptyList(), loadFailed = true), vm.uiState.value)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), "whats_new.txt" in warnings.single())
    }

    @Test
    fun `a release with a malformed date fails the whole load, not that card alone`() = runTest(main) {
        val vm = vm { listOf(release("0.36.2", "2026-11-18"), release("0.36.1", "yesterday")) }
        advanceUntilIdle()

        assertEquals(WhatsNewUiState(loadFailed = true), vm.uiState.value)
    }
}
