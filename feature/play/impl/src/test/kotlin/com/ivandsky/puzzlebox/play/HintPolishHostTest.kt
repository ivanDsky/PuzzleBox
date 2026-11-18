package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.gameui.LocalHintPolish
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

/** The local the play host provides equals the HintPolish holder the graph bound; no binding reads as off (backlog #1172). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintPolishHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `bound on, the local reads true`() = assertEquals(true, seen(bound = HintPolish(enabled = true)))

    @Test
    fun `bound off, the local reads false`() = assertEquals(false, seen(bound = HintPolish(enabled = false)))

    @Test
    fun `nothing bound, the local reads false`() = assertEquals(false, seen(bound = null))

    private fun seen(bound: HintPolish?): Boolean? {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module { if (bound != null) single { bound } }) }
        var seen: Boolean? = null
        composeRule.setContent { PlayBoardHosts { seen = LocalHintPolish.current } }
        return seen
    }
}
