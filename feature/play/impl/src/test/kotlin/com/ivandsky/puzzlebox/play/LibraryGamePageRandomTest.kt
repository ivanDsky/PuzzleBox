package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * The game page's Random button in the title row: only for a game with an archive and only with a handler, handing back
 * this page's game, at touch size, and on the phone without costing the archive line its solved count.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class LibraryGamePageRandomTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null
    private val today = LocalDate.parse("2026-11-05")
    private val zip = GameId("zip")

    private fun row(progress: ArchiveProgress?) = LibraryRowModel(
        card = GameCardModel(
            game = zip,
            name = "Zip",
            brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
            status = TodayStatus.NotStarted,
            rankOrder = RankOrder.TimeThenHints,
            puzzleNumber = 10,
            best = null,
        ),
        today = 10,
        progress = progress,
        stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
        rules = emptyList(),
    )

    private val archive = ArchiveProgress(today.minusDays(1_229)..today, 1_230, 12, 0)

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(listOf(zip))
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private fun show(layout: PlayLayout, row: LibraryRowModel = row(archive), width: Dp? = null, onRandom: ((GameId) -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(if (width != null) Modifier.width(width) else Modifier) {
                    LibraryGamePage(
                        row = row,
                        page = null,
                        month = null,
                        layout = layout,
                        calendar = CalendarActions(),
                        onPlay = { _, _ -> },
                        onRetry = {},
                        onOpenLesson = {},
                        onBack = {}.takeIf { layout == PlayLayout.SinglePane },
                        offline = false,
                        hasLesson = false,
                        onOpenRanks = { _, _ -> },
                        onRandom = onRandom,
                    )
                }
            }
        }
    }

    private fun archiveLine(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasText("puzzles since", substring = true)).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private fun SemanticsNodeInteraction.assertTouchSizeInside(screen: Dp) {
        assertIsDisplayed()
        assertHeightIsAtLeast(48.dp)
        val right = getBoundsInRoot().right
        assert(right <= screen) { "ends at $right" }
    }

    @Test
    fun `the phone page has Random at touch size on screen, reporting this page's game`() {
        val random = mutableListOf<GameId>()
        show(PlayLayout.SinglePane) { random += it }

        composeRule.onNodeWithTag("library.page.random").assertTouchSizeInside(360.dp)
        composeRule.onNodeWithTag("library.page.random").performClick()

        composeRule.runOnIdle { assertEquals(listOf(zip), random) }
    }

    @Test
    fun `the phone's Random sits under the title and leaves the archive line as long as without it`() {
        var withRandom by mutableStateOf(false)
        composeRule.setContent {
            PuzzleBoxTheme {
                LibraryGamePage(
                    row = row(archive), page = null, month = null, layout = PlayLayout.SinglePane, calendar = CalendarActions(),
                    onPlay = { _, _ -> }, onRetry = {}, onOpenLesson = {}, onBack = {}, offline = false, hasLesson = false,
                    onOpenRanks = { _, _ -> }, onRandom = if (withRandom) ({ _ -> }) else null,
                )
            }
        }
        val without = archiveLine().getLineEnd(0, visibleEnd = true)
        composeRule.runOnIdle { withRandom = true }
        composeRule.waitForIdle()

        // At 360 dp the line already ellipsizes; beside Random it would lose the solved count.
        assertEquals(without, archiveLine().getLineEnd(0, visibleEnd = true))
        val button = composeRule.onNodeWithTag("library.page.random").getBoundsInRoot()
        assertTrue("Random is under the title", button.top >= composeRule.onNodeWithText("Zip").getBoundsInRoot().bottom)
    }

    @Test
    fun `a game with no archive has no Random button`() {
        show(PlayLayout.SinglePane, row = row(progress = null)) {}

        composeRule.onNodeWithTag("library.page.random").assertDoesNotExist()
    }

    @Test
    fun `without a handler the page has no Random button`() {
        show(PlayLayout.SinglePane, onRandom = null)

        composeRule.onNodeWithTag("library.page.random").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `the tablet page has Random after the title in its row, at touch size inside the pane, reporting its game`() {
        val random = mutableListOf<GameId>()
        show(PlayLayout.TwoPane, width = 800.dp) { random += it }

        val button = composeRule.onNodeWithTag("library.page.random")
        button.assertTouchSizeInside(800.dp)
        val title = composeRule.onNodeWithText("Zip").getBoundsInRoot()
        assertTrue("Random is beside the title", button.getBoundsInRoot().top < title.bottom)
        assertTrue("Random is after the title", button.getBoundsInRoot().left >= title.right)
        // The whole line shows beside Random: the ellipsis flag reads true even for a line that fits.
        assertEquals(archiveLine().layoutInput.text.length, archiveLine().getLineEnd(0, visibleEnd = true))
        button.performClick()

        composeRule.runOnIdle { assertEquals(listOf(zip), random) }
    }
}
