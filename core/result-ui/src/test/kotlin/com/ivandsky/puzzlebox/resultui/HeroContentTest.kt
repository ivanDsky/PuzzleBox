package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hero's content on a card shorter than its natural height: the bottom group keeps its place on the padding. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HeroContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `below the natural height the bottom group stays on the bottom padding and overflows the card`() {
        composeRule.setContent {
            HeroContent(
                padding = 8.dp,
                timeGapTop = 4.dp,
                timeGapBottom = 4.dp,
                modifier = Modifier.requiredHeight(60.dp),
                onNaturalHeight = null,
            ) {
                Box(Modifier.height(20.dp))
                Box(Modifier.height(40.dp))
                Box(Modifier.height(30.dp).testTag("group"))
            }
        }
        composeRule.waitForIdle()

        // Natural height is 8 + 20 + 4 + 40 + 4 + 30 + 8 = 114 dp, so the group sits at 114 - 8 - 30 = 76 dp.
        val group = composeRule.onNodeWithTag("group").getUnclippedBoundsInRoot()
        assertEquals(76f, group.top.value, 0.5f)
    }
}
