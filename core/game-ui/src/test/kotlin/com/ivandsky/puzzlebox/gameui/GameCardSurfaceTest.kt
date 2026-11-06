package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameCardSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    private fun clicksFor(offline: Boolean): Int {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameCardSurface(
                    colors = gameCardColors(brand, offline),
                    shape = AppShapes.large,
                    onClick = { clicks++ },
                    modifier = Modifier.size(184.dp, 156.dp).testTag(CARD),
                    motif = {},
                    content = {},
                )
            }
        }
        composeRule.onNodeWithTag(CARD).assertIsEnabled().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        return clicks
    }

    @Test
    fun `offline card is enabled and its click fires`() {
        assertEquals(1, clicksFor(offline = true))
    }

    @Test
    fun `online card click fires`() {
        assertEquals(1, clicksFor(offline = false))
    }

    private companion object {
        const val CARD = "card"
    }
}
