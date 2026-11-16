package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.LocalPlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The host hands the screens below the bound source's tuning, live; no binding or a null tuning keeps today's layout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTuningHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun bind(source: PlayTuningSource?) {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module { if (source != null) single<PlayTuningSource> { source } }) }
    }

    private fun provided(): () -> PlayTuning? {
        var seen: PlayTuning? = null
        composeRule.setContent { PlayTuningHost { seen = LocalPlayTuning.current } }
        composeRule.waitForIdle()
        return { seen }
    }

    @Test
    fun `a bound tuning reaches the content below`() {
        bind(FakePlayTuningSource(PlayTuning.Starting))

        assertEquals(PlayTuning.Starting, provided()())
    }

    @Test
    fun `a bound null reaches the content as null`() {
        bind(FakePlayTuningSource(null))

        assertNull(provided()())
    }

    @Test
    fun `no binding reads as null`() {
        bind(null)

        assertNull(provided()())
    }

    @Test
    fun `a change at the source reaches the content live`() {
        val source = FakePlayTuningSource(null)
        bind(source)
        val seen = provided()
        val next = PlayTuning(400.dp, 500.dp, null, LandscapeControls.SideColumn)

        source.set(next)
        composeRule.waitForIdle()

        assertEquals(next, seen())
    }
}
