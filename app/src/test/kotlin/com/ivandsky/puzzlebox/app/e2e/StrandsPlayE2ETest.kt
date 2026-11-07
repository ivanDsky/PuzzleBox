package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.script
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.games.strands.feed.NytStrandsDailySource
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.network.FakeNytTransport
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SC1 / SC5 over the real shell: today's Strands, parsed by the real NYT source and trust
 * gate from a committed fixture, played the way a player would — a dragged theme word, a
 * tapped non-theme word (double-tap on the tail), a hint earned by three of them, both hint
 * stages, a kill mid-puzzle that restores everything (A30), the solve, its Result (hints and
 * time, never a share line) and its History row. A touch-down while a found word still
 * settles rests its letters on the next frame (A12).
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the default Robolectric screen would push the 8-row board off it.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class StrandsPlayE2ETest {

    @Test
    fun `SC1 SC5 today's Strands plays from the hub to the result and its History row`() =
        shellRobot(playHub, today = TODAY, graph = strandsGraph()) {
            openGame(STRANDS)
            awaitStrands()

            drag(ACORN)
            assertCells(ACORN, CellState.FoundTheme)

            tapWord(ARTS)
            assertEquals("a tapped non-theme word counts", 1, rule.runOnIdle { strands().session?.state?.nonTheme?.size })
            tapWord(BRAN)
            tapWord(CART)
            assertTrue("three non-theme words earn a hint", rule.runOnIdle { strands().hint.available })

            pressHint()
            assertEquals("the press spends one hint", 1, rule.runOnIdle { strands().session?.state?.hintsUsed })
            // Stage 1 outlines the shortest unfound theme word.
            assertCells(HUSK, CellState.HintOutlined)

            listOf(ALTS, AVER, BACH).forEach { tapWord(it) }
            pressHint()
            HUSK.forEachIndexed { index, at -> assertCell(at, CellState.HintOrderRevealed(index)) }

            // Both stages are on the board: the pill explains instead of spending.
            pressHint()
            awaitTag(HINT_DIALOG)
            tap(HINT_DIALOG_DISMISS)
            assertTrue("the dialog closes", rule.onAllNodesWithText("Your hint's on the board.").fetchSemanticsNodes().isEmpty())

            background()
            val killed = checkNotNull(rule.runOnIdle { strands().session })
            // The earlier word commits already saved a blob; only backgrounding banks the
            // running clock, so wait for the save that holds this folded elapsed time.
            rule.waitUntil(TIMEOUT_MS) {
                savedSession()?.let { it.state == killed.state && it.elapsedMs == killed.elapsedMs } == true
            }
            foreground()

            relaunch()
            openGame(STRANDS)
            val resumed = awaitStrands()
            assertEquals("found words, non-theme words and hints survive the kill", killed.state, resumed.state)
            assertEquals("same elapsed time (±1 s)", killed.elapsedMs.toDouble(), resumed.elapsedMs.toDouble(), 1_000.0)
            assertCells(ACORN, CellState.FoundTheme)
            HUSK.forEachIndexed { index, at -> assertCell(at, CellState.HintOrderRevealed(index)) }

            listOf(BRANCH, HUSK, LEAVES, MOSS, PINECONE, TWIG).forEach { drag(it) }
            assertTrue("no record before the last word", runBlocking { strandsRecords() }.isEmpty())
            drag(SPANGRAM)

            awaitTag(RESULT_SCREEN)
            assertRoute(RESULT)
            rule.onNode(hasTestTag("result.tile.hints") and hasAnyDescendant(hasText("2"))).assertExists()
            assertTrue(
                "the result shows the play time",
                rule.onAllNodes(clockText, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            )

            val record = runBlocking { strandsRecords() }.single()
            assertEquals("the record keeps the solve's hints", 2, record.hints)

            switchTab(HomeKey)
            tapText("See all")
            awaitTag(HISTORY_ROW)
            // The captured row exists from the opening; only the solve's record swaps its
            // "Keep going" pill for the time and hints.
            rule.onNodeWithTag("history.unfinished.$STRANDS/$TODAY", useUnmergedTree = true).assertDoesNotExist()
            rule.onNode(hasTestTag(HISTORY_ROW) and hasAnyDescendant(hasText("2 hints")), useUnmergedTree = true)
                .assertExists()
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a touch-down mid-settle rests the word just found on the next frame`() =
        shellRobot(playHub, today = TODAY, graph = strandsGraph()) {
            openGame(STRANDS)
            awaitStrands()
            // ACORN's first letter bounces first and longest: the settle's clearest frame.
            val watched = ACORN.first()
            rule.mainClock.autoAdvance = false
            drag(ACORN)
            assertCells(ACORN, CellState.FoundTheme)
            rule.mainClock.advanceTimeBy(MID_SETTLE_MS)
            val settling = pixels(watched)

            rule.onNodeWithTag(BOARD).performTouchInput { down(centreOf(FREE)) }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            val touched = pixels(watched)

            rule.onNodeWithTag(BOARD).performTouchInput { up() }
            rule.mainClock.advanceTimeBy(SETTLED_MS)
            val rest = pixels(watched)
            rule.mainClock.autoAdvance = true

            assertFalse("before the touch the letter is mid-bounce", settling.contentEquals(rest))
            assertTrue("the touch-down rests it at once", touched.contentEquals(rest))
        }

    private fun ShellRobot.pixels(at: Coord): IntArray =
        rule.onNodeWithTag(cellTag(at)).captureToImage().toPixelMap().buffer

    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / COLS, height * (at.row + 0.5f) / ROWS)

    private fun ShellRobot.savedSession(): StrandsSession? =
        runBlocking { graph.saved.load(SLOT) }
            ?.let(SessionBlob::decode)
            ?.let { StrandsSessionCodec.deserialize(it.session).getOrNull() }

    private suspend fun ShellRobot.strandsRecords() =
        graph.records.observeRecords().first().filter { it.puzzleRef.gameId == StrandsDefinition.id }

    private fun ShellRobot.strands(): StrandsPlayController =
        checkNotNull(graph.koin.get<PlaySessionStore>().peek(SLOT)) { "no live Strands play for $TODAY" }
            .controller as StrandsPlayController

    private fun ShellRobot.awaitStrands(): StrandsSession {
        rule.waitUntil(TIMEOUT_MS) {
            rule.runOnIdle { graph.koin.get<PlaySessionStore>().peek(SLOT) }
                ?.let { (it.controller as StrandsPlayController).session } != null
        }
        awaitTag(BOARD)
        return checkNotNull(rule.runOnIdle { strands().session })
    }

    /** One finger across [path]: a single move per cell, so no neighbour is grazed on a diagonal. */
    private fun ShellRobot.drag(path: List<Coord>) {
        rule.onNodeWithTag(BOARD).performScrollTo().performTouchInput {
            down(centreOf(path.first()))
            path.drop(1).forEach { moveTo(centreOf(it)) }
            up()
        }
        rule.waitForIdle()
    }

    /** Taps each letter, then the last one again: the double-tap submit (A29). */
    private fun ShellRobot.tapWord(path: List<Coord>) {
        (path + path.last()).forEach { at -> rule.onNodeWithTag(cellTag(at)).performScrollTo().performClick() }
        rule.waitForIdle()
    }

    /** On a phone the hint pill sits under the board, on the same scrolling page. */
    private fun ShellRobot.pressHint() {
        rule.onNodeWithTag(HINT).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun ShellRobot.assertCells(path: List<Coord>, state: CellState) = path.forEach { assertCell(it, state) }

    private fun ShellRobot.assertCell(at: Coord, state: CellState) {
        val actual = rule.runOnIdle { strands().model.cells[at.row * COLS + at.col].state }
        assertEquals("cell $at", state, actual)
    }

    /** Today's Strands served by the real NYT adapter over the committed 2026-11-06 fixture. */
    private fun strandsGraph() = E2eKoinGraph(TODAY).apply {
        val transport = FakeNytTransport().apply {
            respond("svc/strands/v2/$TODAY.json", FetchResult.Ok(fixture(TODAY)))
        }
        val fetched = runBlocking { NytStrandsDailySource(transport).fetchDaily(TODAY) }
        val envelope = (fetched as FetchResult.Ok).value.puzzle
        daily.script(
            StrandsDefinition.id,
            DailyToday.Fresh(envelope),
            history = listOf(DailyEntry(StrandsDefinition.id, TODAY, StrandsNumber.of(TODAY), StrandsDefinition.VARIANT.value, 0L)),
        )
    }

    private fun fixture(day: LocalDate): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/strands/$day.json")) { "no fixture for $day" }.readText()

    private fun cellTag(at: Coord) = "strands.cell.${at.row}.${at.col}"

    private val clockText = SemanticsMatcher("a m:ss clock") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("""\d+:\d\d""").matches(it.text) }
    }

    private val playHub = NavigationState(
        tab = PlayKey,
        stacks = listOf<List<NavKey>>(listOf(HomeKey), listOf(PlayKey), listOf(ProfileKey)),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 6)
        val SLOT = PlaySlot.Daily(StrandsDefinition.id, TODAY)
        const val STRANDS = "strands"
        const val ROWS = 8
        const val COLS = 6
        const val TIMEOUT_MS = 5_000L
        const val BOARD = "strands.board"
        const val SETTLED_MS = 3_000L

        /** A fifth into the first letter's 1 s bounce: deep in its dip. */
        const val MID_SETTLE_MS = 200L

        /** A free letter far from ACORN: the touch-down lands on it. */
        val FREE = Coord(7, 0)
        const val HINT = "strands.hint"
        const val HINT_DIALOG = "strands.hint_dialog"
        const val HINT_DIALOG_DISMISS = "strands.hint_dialog_dismiss"
        const val RESULT = "route.result"
        const val RESULT_SCREEN = "result.screen"
        val HISTORY_ROW = "history.row.$STRANDS/$TODAY"

        fun path(vararg cells: Pair<Int, Int>) = cells.map { (r, c) -> Coord(r, c) }

        // The 2026-11-06 board ("Autumn walk in the woods"): its theme words and spangram…
        val ACORN = path(4 to 5, 3 to 4, 2 to 3, 1 to 2, 0 to 1)
        val BRANCH = path(1 to 5, 2 to 5, 1 to 4, 0 to 5, 0 to 4, 0 to 3)
        val HUSK = path(2 to 1, 3 to 2, 4 to 2, 4 to 3)
        val LEAVES = path(5 to 4, 5 to 3, 6 to 4, 6 to 3, 6 to 2, 5 to 2)
        val MOSS = path(3 to 1, 3 to 0, 2 to 0, 1 to 0)
        val PINECONE = path(5 to 0, 4 to 0, 4 to 1, 5 to 1, 6 to 1, 7 to 1, 7 to 0, 6 to 0)
        val TWIG = path(3 to 5, 2 to 4, 1 to 3, 0 to 2)
        val SPANGRAM = path(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 5, 7 to 5, 7 to 4, 7 to 3, 7 to 2)

        // …and six of its valid non-theme words, none crossing ACORN's locked letters.
        val ARTS = path(1 to 4, 2 to 5, 3 to 5, 4 to 4)
        val BRAN = path(1 to 5, 2 to 5, 1 to 4, 0 to 5)
        val CART = path(0 to 4, 1 to 4, 2 to 5, 3 to 5)
        val ALTS = path(6 to 4, 5 to 4, 5 to 5, 4 to 4)
        val AVER = path(6 to 4, 6 to 3, 6 to 2, 7 to 2)
        val BACH = path(1 to 5, 1 to 4, 0 to 4, 0 to 3)
    }
}
