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
 * What the tablet's right pane shows under the Train tab: the slot's pane, never a game's page, and the selected game's
 * page again once the player is back on Dailies. The phone draws no pane, only the slot's body.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryTrainPaneTest {

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

    private val slot = TrainSlot(
        content = { Text("train body", Modifier.testTag(BODY)) },
        pane = { modifier -> Text("the round", modifier.testTag(PANE)) },
    )

    /** The tab strip shows only with the Duels slot, so Train's tab needs one beside it. */
    private val duels = DuelsSlot(
        content = { _, _, _ -> Text("duels body") },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { _ -> },
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
                duels = duels,
                train = slot,
                onMakeOne = {},
            )
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `on the Train tab the right pane is the slot's pane and no game page`() {
        show(PlayLayout.TwoPane)

        tap("library.tab.train")

        composeRule.onNodeWithTag(BODY).assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_PAGE).assertDoesNotExist()
    }

    @Test
    fun `back on Dailies the game page shows again and the train pane is gone`() {
        show(PlayLayout.TwoPane)
        tap("library.tab.train")

        tap("library.tab.dailies")

        composeRule.onNodeWithTag(GAME_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertDoesNotExist()
    }

    @Test
    fun `the phone has no train pane`() {
        show(PlayLayout.SinglePane)

        tap("library.tab.train")

        composeRule.onNodeWithTag(BODY).assertIsDisplayed()
        composeRule.onNodeWithTag(PANE).assertDoesNotExist()
    }

    private companion object {
        const val BODY = "train.body.stub"
        const val PANE = "train.pane.stub"
        const val GAME_PAGE = "library.page.zip"
    }
}
