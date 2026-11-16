package com.ivandsky.puzzlebox.app.di

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BoardErgonomics (nav-rethink B5) gates the base tuning at the app binding: null while dark, the base's value while on, live. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlaggedPlayTuningSourceTest {

    private val flags = object : ReleaseFlags {
        override val enabled = MutableStateFlow<Set<ReleaseFlag>>(emptySet())
    }
    private val base = object : PlayTuningSource {
        override val tuning = MutableStateFlow<PlayTuning?>(PlayTuning.Starting)
    }

    private fun TestScope.source() = FlaggedPlayTuningSource(flags, base, backgroundScope)

    @Test
    fun `with the flag off it emits null though the base holds a tuning`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(source().tuning.value)
    }

    @Test
    fun `with the flag on it emits the base value`() = runTest(UnconfinedTestDispatcher()) {
        flags.enabled.value = setOf(ReleaseFlag.BoardErgonomics)

        assertEquals(PlayTuning.Starting, source().tuning.value)
    }

    @Test
    fun `another flag on does not open it`() = runTest(UnconfinedTestDispatcher()) {
        flags.enabled.value = setOf(ReleaseFlag.FlatSurfaces)

        assertNull(source().tuning.value)
    }

    @Test
    fun `flipping the flag changes the emitted value live`() = runTest(UnconfinedTestDispatcher()) {
        val source = source()

        flags.enabled.value = setOf(ReleaseFlag.BoardErgonomics)
        assertEquals(PlayTuning.Starting, source.tuning.value)

        flags.enabled.value = emptySet()
        assertNull(source.tuning.value)
    }

    @Test
    fun `with the flag on a change of the base reaches the emitted value`() = runTest(UnconfinedTestDispatcher()) {
        flags.enabled.value = setOf(ReleaseFlag.BoardErgonomics)
        val source = source()
        val next = PlayTuning(400.dp, 500.dp, null, LandscapeControls.SideColumn)

        base.tuning.value = next
        assertEquals(next, source.tuning.value)

        base.tuning.value = null
        assertNull(source.tuning.value)
    }
}
