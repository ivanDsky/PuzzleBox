package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game page's "Make one": shown only when the app hands in a handler, on the phone and the tablet,
 * and it reports the game whose page it is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class GamePageMakeOneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null
    private val today = LocalDate.parse("2026-11-05")

    private val row = LibraryRowModel(
        card = GameCardModel(
            game = GameId("zip"),
            name = "Zip",
            brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
            status = TodayStatus.NotStarted,
            rankOrder = RankOrder.TimeThenHints,
            puzzleNumber = 10,
            best = null,
        ),
        today = 10,
        progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
        stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
        rules = emptyList(),
    )

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(listOf(row.card.game))
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private fun show(layout: PlayLayout, onMakeOne: ((GameId) -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LibraryGamePage(
                    row = row,
                    page = null,
                    month = null,
                    layout = layout,
                    calendar = CalendarActions(),
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    onBack = null,
                    offline = false,
                    hasLesson = false,
                    onOpenRanks = { _, _ -> },
                    onMakeOne = onMakeOne,
                )
            }
        }
    }

    @Test
    fun `with a handler the page offers Make one and reports its game`() {
        val made = mutableListOf<GameId>()
        show(PlayLayout.SinglePane) { made += it }
        composeRule.onNodeWithTag("library.page.makeOne").performScrollTo().assertTextEquals("Make one").assertHasClickAction()
        composeRule.onNodeWithTag("library.page.makeOne").performClick()
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), made) }
    }

    @Test
    fun `the tablet's two panes offer it too`() {
        val made = mutableListOf<GameId>()
        show(PlayLayout.TwoPane) { made += it }
        composeRule.onNodeWithTag("library.page.makeOne").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), made) }
    }

    @Test
    fun `with no handler there is no Make one`() {
        show(PlayLayout.SinglePane, onMakeOne = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library.page.makeOne").assertDoesNotExist()
    }
}
