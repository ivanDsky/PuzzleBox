package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `SnapTrace` reaches game boards only through [LocalSnapTrace], provided once by [SnapTraceHost]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapTraceFlagTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun readInside(flags: ReleaseFlags): Boolean {
        var seen: Boolean? = null
        composeRule.setContent {
            SnapTraceHost(flags) { seen = LocalSnapTrace.current }
        }
        composeRule.waitForIdle()
        return seen!!
    }

    @Test
    fun `the flag ships off`() {
        assertFalse(ReleaseFlag.SnapTrace.releaseDefault)
        assertEquals("Snap trace drawing", ReleaseFlag.SnapTrace.label)
    }

    @Test
    fun `outside any host the local reads false`() {
        var seen: Boolean? = null
        composeRule.setContent { seen = LocalSnapTrace.current }
        composeRule.waitForIdle()
        assertEquals(false, seen)
    }

    @Test
    fun `with the flag off the local reads false inside the host`() {
        assertFalse(readInside(FakeReleaseFlags()))
    }

    @Test
    fun `with the flag on the local reads true inside the host`() {
        assertEquals(true, readInside(FakeReleaseFlags(ReleaseFlag.SnapTrace)))
    }

    @Test
    fun `other flags do not turn the local on`() {
        assertFalse(readInside(FakeReleaseFlags(ReleaseFlag.ZipEditor)))
    }

    @Test
    fun `a live override flips the local without recreating the host`() {
        val state = MutableStateFlow(emptySet<ReleaseFlag>())
        val flags = object : ReleaseFlags {
            override val enabled: StateFlow<Set<ReleaseFlag>> = state
        }
        var seen: Boolean? = null
        composeRule.setContent { SnapTraceHost(flags) { seen = LocalSnapTrace.current } }
        composeRule.waitForIdle()
        assertEquals(false, seen)
        state.value = setOf(ReleaseFlag.SnapTrace)
        composeRule.waitForIdle()
        assertEquals(true, seen)
    }
}
