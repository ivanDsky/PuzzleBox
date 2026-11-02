package com.ivandsky.puzzlebox.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric compose tests (A17): pane choice and theme dialog behavior, no emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        layout: HomeLayout,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        history: List<HistoryDay> = emptyList(),
        game: GameSummary = GameSummary(GameId("zip"), "Zip"),
        onSetThemeMode: (ThemeMode) -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeScreen(
                    layout = layout,
                    game = game,
                    themeMode = themeMode,
                    onSetThemeMode = onSetThemeMode,
                    history = history,
                    modifier = Modifier,
                )
            }
        }
    }

    @Test
    fun `single pane renders home content without a detail pane`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("home.today_card").assertExists()
        composeRule.onNodeWithTag("home.detail_pane").assertDoesNotExist()
    }

    @Test
    fun `two pane renders the detail pane beside the list`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("home.today_card").assertExists()
        composeRule.onNodeWithTag("home.detail_pane").assertExists()
    }

    @Test
    fun `history teaser shows the empty state when nothing is cached`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithText("No games yet").assertExists()
    }

    @Test
    fun `history rows render a formatted headline per day`() {
        setContent(HomeLayout.TwoPane, history = listOf(HistoryDay("2026-10-30", "October 30", "Zip")))

        composeRule.onNodeWithText("October 30").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `theme dialog opens and reports the selection`() {
        var selected: ThemeMode? = null
        setContent(HomeLayout.SinglePane, onSetThemeMode = { selected = it })

        composeRule.onNodeWithContentDescription("Appearance").performClick()
        composeRule.onNodeWithText("Dark").performClick()

        composeRule.runOnIdle { check(selected == ThemeMode.DARK) { "expected DARK, was $selected" } }
    }

    @Test
    fun `theme dialog reflects the current mode`() {
        setContent(HomeLayout.SinglePane, themeMode = ThemeMode.LIGHT)

        composeRule.onNodeWithContentDescription("Appearance").performClick()

        composeRule.onNodeWithText("Light").assertIsSelected()
    }
}
