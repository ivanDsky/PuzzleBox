package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the tablet's right pane shows beside the Games list: the selected game's page under Dailies, and under Duels
 * the slot's duel pane, never a game's page. Going back to Dailies shows the game selected before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryDuelsPaneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var koin: KoinApplication? = null

    /** The two-pane layout's game page carries the game's board, which a view model builds. */
    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private val slot = DuelsSlot(
        content = { _, _, _ -> Text("duels body", Modifier.testTag("duels.body")) },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { modifier -> Text("the duel", modifier.testTag(PANE)) },
    )

    private fun show(layout: PlayLayout) = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            LibraryScreen(
                state = previewState(),
                page = null,
                month = null,
                layout = layout,
                hasLesson = { false },
                onSelect = {},
                onPlay = { _, _ -> },
                onRetry = {},
                onOpenLesson = {},
                calendar = CalendarActions(),
                onOpenRanks = { _, _ -> },
                duels = slot,
                custom = CustomSlot { _, _, _ -> },
                onMakeOne = {},
            )
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `Dailies shows the selected game's page and no duel`() {
        show(PlayLayout.TwoPane)

        composeRule.onNodeWithTag(GAME_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertDoesNotExist()
    }

    @Test
    fun `on the Duels tab the right pane is the duel and no game page`() {
        show(PlayLayout.TwoPane)

        tap("library.tab.duels")

        composeRule.onNodeWithTag(PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_PAGE).assertDoesNotExist()
    }

    @Test
    fun `back on Dailies the game page that was selected shows again`() {
        show(PlayLayout.TwoPane)
        tap("library.tab.duels")

        tap("library.tab.dailies")

        composeRule.onNodeWithTag(GAME_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertDoesNotExist()
    }

    @Test
    fun `the phone has no duel pane`() {
        show(PlayLayout.SinglePane)

        tap("library.tab.duels")

        composeRule.onNodeWithTag("duels.body").assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertDoesNotExist()
    }

    private companion object {
        const val PANE = "duels.pane.stub"
        const val GAME_PAGE = "library.page.zip"
    }
}
