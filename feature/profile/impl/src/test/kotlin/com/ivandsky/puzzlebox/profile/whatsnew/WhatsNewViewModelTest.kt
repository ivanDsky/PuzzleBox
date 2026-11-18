package com.ivandsky.puzzlebox.profile.whatsnew

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewViewModelTest {

    private val warnings = mutableListOf<String>()

    private fun vm(releases: () -> List<WhatsNewRelease>) = WhatsNewViewModel(
        source = object : WhatsNewSource {
            override fun releases() = releases()
        },
        failures = { _, message, _ -> warnings += message },
    )

    private fun release(version: String, date: String) = WhatsNewRelease(version, date, listOf("note $version"))

    @Test
    fun `three releases are listed in the source's order with worded dates`() {
        val vm = vm {
            listOf(release("0.36.2", "2026-11-18"), release("0.35.0", "2026-11-18"), release("0.34.3", "2026-11-17"))
        }

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
    fun `a source that throws shows the load-failed state and reports the cause`() {
        val vm = vm { throw FileNotFoundException("whats_new.txt") }

        assertEquals(WhatsNewUiState(releases = emptyList(), loadFailed = true), vm.uiState.value)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), "whats_new.txt" in warnings.single())
    }

    @Test
    fun `a release with a malformed date fails the whole load, not that card alone`() {
        val vm = vm { listOf(release("0.36.2", "2026-11-18"), release("0.36.1", "yesterday")) }

        assertEquals(WhatsNewUiState(loadFailed = true), vm.uiState.value)
    }

    @Test
    fun `the source is read once however often the state is read`() {
        var reads = 0
        val vm = vm { reads++; listOf(release("0.36.2", "2026-11-18")) }

        repeat(3) { vm.uiState.value }

        assertEquals(1, reads)
    }
}
