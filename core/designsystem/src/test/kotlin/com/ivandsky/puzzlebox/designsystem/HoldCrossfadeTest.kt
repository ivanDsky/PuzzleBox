package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tablet right panes switch in place (ticket 1317): the old page stays under the new one until the fade ends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h400dp-420dpi")
class HoldCrossfadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var target by mutableStateOf("one")

    private fun composed(tag: String) = runCatching { composeRule.onNodeWithTag(tag).assertExists() }.isSuccess

    @Test
    fun `a new target fades in over the old page in place, then only the new page is left`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HoldCrossfade(target) { Box(Modifier.fillMaxSize()) { Text(it, Modifier.testTag("page.$it")) } }
            }
        }
        composeRule.mainClock.advanceTimeBy(MotionTokens.TabletPageCrossfade * 2L)
        val before = composeRule.onNodeWithTag("page.one").getBoundsInRoot()
        assertEquals(listOf(true, false), listOf("page.one", "page.two").map(::composed))

        target = "two"
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(MotionTokens.TabletPageCrossfade / 2L)

        assertEquals("both pages mid-fade", listOf(true, true), listOf("page.one", "page.two").map(::composed))
        val incoming = composeRule.onNodeWithTag("page.two").getBoundsInRoot()
        assertEquals(before.left, incoming.left)
        assertEquals(before.top, incoming.top)
        composeRule.mainClock.advanceTimeBy(MotionTokens.TabletPageCrossfade * 2L)
        assertEquals(listOf(false, true), listOf("page.one", "page.two").map(::composed))
    }
}
