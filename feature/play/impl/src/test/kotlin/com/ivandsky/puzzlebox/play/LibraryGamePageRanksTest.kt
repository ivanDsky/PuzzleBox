package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveProgress
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

/** The game page's board is the summary row only when the hub callback is handed in, and the row hands back the game and the day it shows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LibraryGamePageRanksTest {

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
        koin = startGamePageBoardKoin(listOf(row.card.game))
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private fun show(onOpenRanks: ((GameId, LocalDate?) -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LibraryGamePage(
                    row = row,
                    page = null,
                    month = null,
                    layout = PlayLayout.SinglePane,
                    calendar = CalendarActions(),
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    onBack = null,
                    offline = false,
                    hasLesson = false,
                    onOpenGameHistory = { _, _ -> },
                    onOpenRanks = onOpenRanks,
                )
            }
        }
    }

    @Test
    fun `with the hub on the summary row replaces the board and opens the hub at the game and day`() {
        val opened = mutableListOf<Pair<GameId, LocalDate?>>()
        show { game, day -> opened += game to day }
        composeRule.onNodeWithTag("daily_board.title").assertDoesNotExist()
        composeRule.onNodeWithTag("summary.rank").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip") to today), opened) }
    }

    @Test
    fun `without the callback the page keeps its board and has no summary row`() {
        show(onOpenRanks = null)
        composeRule.onNodeWithTag("summary.rank").assertDoesNotExist()
    }
}
