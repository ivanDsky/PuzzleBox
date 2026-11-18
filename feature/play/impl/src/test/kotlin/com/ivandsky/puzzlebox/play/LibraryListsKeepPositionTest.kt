package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 1509: the Games lists keep their place on the tablet while the screen's state arrives again and a pick in the
 * pane beside them changes it. Dailies is one column on a scroll state, Duels a lazy list on its own; the screen holds
 * both above the tabs, so neither may go back to its top when a pick or an update recomposes the screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h360dp")
class LibraryListsKeepPositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var koin: KoinApplication? = null
    private var state by mutableStateOf(previewState())

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private val slot = DuelsSlot(
        content = { listState, _, _ ->
            LazyColumn(Modifier.testTag("duels.list"), state = listState) {
                items(DUELS) { Text("duel $it", Modifier.height(48.dp).testTag("duel.$it")) }
            }
        },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { modifier -> Text("the duel", modifier.testTag("duels.pane.stub")) },
    )

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = state,
                    page = null,
                    month = null,
                    layout = PlayLayout.TwoPane,
                    hasLesson = { false },
                    onSelect = { game -> state = state.copy(selected = game) },
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    onOpenRanks = { _, _ -> },
                    duels = slot,
                    onMakeOne = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** The Dailies page's scroller: the vertical one that holds the game rows. */
    private fun dailies() = composeRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasTestTag("library.row.zip")),
    )

    /** How far this vertical scroller has moved, in px. */
    private fun SemanticsNodeInteraction.scrolled(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `Dailies keeps its scroll when a game is picked beside it and the state arrives again`() {
        show()
        dailies().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val before = dailies().scrolled()
        assertTrue("the Dailies list scrolled", before > 0f)

        composeRule.onNodeWithTag("library.row.tango").performClick()
        composeRule.waitForIdle()
        state = previewState(solvedScale = 2).copy(selected = GameId("tango"))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, dailies().scrolled(), 0.5f)
    }

    @Test
    fun `Duels keeps its first visible item when the state arrives again`() {
        show()
        composeRule.onNodeWithTag("library.tab.duels").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duels.list").performScrollToIndex(20)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duel.20").assertIsDisplayed()
        val before = composeRule.onNodeWithTag("duel.20").getUnclippedBoundsInRoot().top

        state = previewState(solvedScale = 2)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithTag("duel.20").assertIsDisplayed()
        assertEquals(before.value, composeRule.onNodeWithTag("duel.20").getUnclippedBoundsInRoot().top.value, 0.5f)
        assertFalse("the Duels list went back to its top", runCatching { composeRule.onNodeWithTag("duel.0").assertIsDisplayed() }.isSuccess)
    }

    private companion object {
        const val DUELS = 30
    }
}
