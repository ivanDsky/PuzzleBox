package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric compose tests (A17): pane choice, theme dialog and resume-cue behavior, no emulator. */
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
        identity: Identity? = null,
        onSetThemeMode: (ThemeMode) -> Unit = {},
        resumeFeatured: MutableState<Boolean> = mutableStateOf(false),
        onPlayToday: () -> Unit = {},
        onOpenProfile: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeScreen(
                    layout = layout,
                    game = game,
                    identity = identity,
                    themeMode = themeMode,
                    onSetThemeMode = onSetThemeMode,
                    history = history,
                    resumeFeatured = resumeFeatured.value,
                    onPlayToday = onPlayToday,
                    onOpenProfile = onOpenProfile,
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
    fun `play today fires the play destination from the today card`() {
        // Ticket 17 regression: the Today card must hoist a REAL play destination —
        // this is the hook that used to be a "coming soon" snackbar.
        var played = false
        setContent(HomeLayout.SinglePane, onPlayToday = { played = true })

        composeRule.onNodeWithText("Play today").performClick()
        composeRule.runOnIdle { check(played) { "expected onPlayToday" } }
    }

    @Test
    fun `play today fires the play destination in the two-pane detail`() {
        var played = false
        setContent(HomeLayout.TwoPane, onPlayToday = { played = true })

        // Composition order: list pane (TodayCard) first, detail pane second — [1] is
        // the detail-pane CTA this test names.
        composeRule.onAllNodesWithText("Play today")[1].performClick()
        composeRule.runOnIdle { check(played) { "expected onPlayToday" } }
    }

    @Test
    fun `today card label switches to resume when the featured game is in progress - SC11`() {
        val resume = mutableStateOf(false)
        var played = 0
        setContent(HomeLayout.SinglePane, resumeFeatured = resume, onPlayToday = { played++ })
        composeRule.onNodeWithText("Play today").assertExists()
        composeRule.onNodeWithText("Resume").assertDoesNotExist()

        // Today's featured game gains a save: the same card now offers to resume it.
        composeRule.runOnIdle { resume.value = true }

        composeRule.onNodeWithText("Play today").assertDoesNotExist()
        composeRule.onNodeWithText("Resume").performClick()
        composeRule.runOnIdle { check(played == 1) { "expected one onPlayToday, was $played" } }
    }

    @Test
    fun `two pane reads resume on the card and the detail action`() {
        setContent(HomeLayout.TwoPane, resumeFeatured = mutableStateOf(true))

        composeRule.onAllNodesWithText("Resume").assertCountEquals(2)
        composeRule.onNodeWithText("Play today").assertDoesNotExist()
    }

    @Test
    fun `sign in fires the profile destination`() {
        // Ticket 17 regression: the account card opens Profile (the real account
        // surface) instead of showing placeholder copy.
        var opened = false
        setContent(HomeLayout.SinglePane, onOpenProfile = { opened = true })

        composeRule
            .onNodeWithText("Sign in")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { check(opened) { "expected onOpenProfile" } }
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

    @Test
    fun `account card reflects signed-out identity as guest`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithText("Guest player").assertExists()
        composeRule.onNodeWithText("Sign in").assertExists()
    }

    @Test
    fun `account card reflects a signed-in identity`() {
        setContent(
            HomeLayout.SinglePane,
            identity = Identity(uid = "u1", displayName = "Ivan D.", photoUrl = null),
        )

        composeRule.onNodeWithText("Ivan D.").assertExists()
        composeRule.onNodeWithText("Competing on leaderboards.").assertExists()
        composeRule.onNodeWithText("Profile").assertExists()
    }
}
