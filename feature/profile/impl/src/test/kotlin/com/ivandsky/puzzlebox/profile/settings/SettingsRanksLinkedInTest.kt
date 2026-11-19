package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Ranks LinkedIn games switch on the Settings screen (#1770): drawn only when its state is given, a tap reports the flipped value. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class SettingsRanksLinkedInTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<Boolean>()

    private fun setContent(linkedIn: Boolean?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SettingsScreen(
                    state = SettingsUiState(ThemeMode.LIGHT, "1.4.0", visibility = OwnTier.Unset, ranksLinkedInDefault = linkedIn),
                    onSetThemeMode = {},
                    onSetQueensAutoCross = {},
                    onSetSudokuPeerHighlight = {},
                    onSetSudokuSameDigitHighlight = {},
                    onSetRanksLinkedInDefault = { taps += it },
                    onBack = {},
                    onOpenCatalog = null,
                )
            }
        }
    }

    @Test
    fun `the switch shows the stored default and a tap reports the flipped one`() {
        setContent(linkedIn = false)

        composeRule.onNodeWithTag("settings.ranks.linkedin").performScrollTo().assertIsOff().performClick()

        assertEquals(listOf(true), taps)
    }

    @Test
    fun `a stored LinkedIn games default shows the switch on`() {
        setContent(linkedIn = true)

        composeRule.onNodeWithTag("settings.ranks.linkedin").performScrollTo().assertIsOn()
    }

    @Test
    fun `without the state the ranks block has no LinkedIn games switch`() {
        setContent(linkedIn = null)

        composeRule.onNodeWithTag("settings.ranks").performScrollTo()
        composeRule.onNodeWithTag("settings.ranks.linkedin").assertDoesNotExist()
    }
}
