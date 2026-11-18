package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.gameui.LocalZipDotPulse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local PlayBoardHosts provides equals the ZipDotPulse holder the graph bound (backlog #1151);. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZipDotPulseHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `the local reads true when ZipDotPulse is bound on`() {
        assertEquals(true, seen(module { single { ZipDotPulse(enabled = true) } }))
    }

    @Test
    fun `the local reads false when ZipDotPulse is bound off`() {
        assertEquals(false, seen(module { single { ZipDotPulse(enabled = false) } }))
    }

    @Test
    fun `the local reads false when the graph binds no ZipDotPulse`() {
        assertEquals(false, seen(module { }))
    }

    @Test
    fun `the replay screens' own host reads true when ZipDotPulse is bound on`() {
        assertEquals(true, seen(module { single { ZipDotPulse(enabled = true) } }, replay = true))
    }

    @Test
    fun `the replay screens' own host reads false when ZipDotPulse is bound off`() {
        assertEquals(false, seen(module { single { ZipDotPulse(enabled = false) } }, replay = true))
    }

    private fun seen(graph: org.koin.core.module.Module, replay: Boolean = false): Boolean? {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(graph) }
        var seen: Boolean? = null
        composeRule.setContent {
            if (replay) ZipDotPulseHost { seen = LocalZipDotPulse.current } else PlayBoardHosts { seen = LocalZipDotPulse.current }
        }
        composeRule.waitForIdle()
        return seen
    }
}
