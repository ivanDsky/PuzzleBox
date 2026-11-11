package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric compose coverage for the Settings screen: the theme choice, About, Developer and Back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val themes = mutableListOf<ThemeMode>()
    private val queensAutoCross = mutableListOf<Boolean>()
    private val sudokuPeer = mutableListOf<Boolean>()
    private val sudokuSameDigit = mutableListOf<Boolean>()
    private var backClicks = 0
    private var catalogClicks = 0

    private fun setContent(
        state: SettingsUiState = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0"),
        showCatalog: Boolean = true,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SettingsScreen(
                    state = state,
                    onSetThemeMode = { themes += it },
                    onSetQueensAutoCross = { queensAutoCross += it },
                    onSetSudokuPeerHighlight = { sudokuPeer += it },
                    onSetSudokuSameDigitHighlight = { sudokuSameDigit += it },
                    onBack = { backClicks++ },
                    onOpenCatalog = if (showCatalog) ({ catalogClicks++ }) else null,
                )
            }
        }
    }

    @Test
    fun `each games row shows its stored value and a tap reports the flipped one`() {
        setContent(
            state = SettingsUiState(
                themeMode = ThemeMode.LIGHT,
                versionName = "1.4.0",
                games = GamesSectionState(queensAutoCross = false, sudokuPeerHighlight = true, sudokuSameDigitHighlight = false),
            ),
        )

        composeRule.onNodeWithTag("settings.games.queens.autocross").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("settings.games.sudoku.peer").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings.games.sudoku.samedigit").performScrollTo().assertIsOff().performClick()

        assertEquals(listOf(true), queensAutoCross)
        assertEquals(listOf(false), sudokuPeer)
        assertEquals(listOf(true), sudokuSameDigit)
    }

    @Test
    fun `the stored mode is selected and tapping another reports it`() {
        setContent()

        composeRule.onNodeWithTag("profile.appearance.light").assertIsSelected()

        composeRule.onNodeWithTag("profile.appearance.dark").performClick()

        assertEquals(listOf(ThemeMode.DARK), themes)
    }

    @Test
    fun `developer section and catalog button exist only with an open-catalog callback`() {
        setContent(showCatalog = true)

        composeRule.onNodeWithTag("settings.developer").assertExists()
        composeRule.onNodeWithTag("settings.catalog").performScrollTo().performClick()
        assertEquals(1, catalogClicks)
    }

    @Test
    fun `without an open-catalog callback there is no developer section`() {
        setContent(showCatalog = false)

        composeRule.onNodeWithTag("settings.developer").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.catalog").assertDoesNotExist()
    }

    @Test
    fun `the version name from the state is on screen`() {
        setContent(SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = "2.7.1"))

        composeRule.onNodeWithTag("settings.about").assertExists()
        composeRule.onNodeWithText("2.7.1").assertExists()
    }

    @Test
    fun `the back icon calls back`() {
        setContent()

        composeRule.onNodeWithTag("settings.back").performClick()

        assertEquals(1, backClicks)
    }
}
