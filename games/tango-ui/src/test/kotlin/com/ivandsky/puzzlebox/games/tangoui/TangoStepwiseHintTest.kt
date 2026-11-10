package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.playCaption
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepGame
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the Stepwise wiring the controller owns (ticket 31): a press shows a Spotlight and places nothing (A1), counts
 * once through the persisted session (A9), marks a wrong entry and re-shows the mark for free without touching the
 * board (A4), clears on every input (A10), hides the caption and the violation line while it shows (A11), and the
 * board draws it without taking taps. The press rules are StepHints' (04b) and the step search TangoStepGame's (30).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoStepwiseHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.parse("2026-10-31")
    private val saved = mutableListOf<ByteArray>()

    private fun stepwise(
        compute: CoroutineDispatcher = Dispatchers.Unconfined,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ) = TangoPlayController(
        env = PlayEnvironment(scope, onSessionChanged = { saved += it }, onWon = {}),
        now = { Instant.parse("2026-10-31T00:00:00Z") },
        hintMode = HintMode.Stepwise(compute),
    )

    private fun loaded(
        compute: CoroutineDispatcher = Dispatchers.Unconfined,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ) = stepwise(compute, scope).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(today)))
    }

    /** Cell 4 holds a sun where the solution has a moon; no rule breaks yet, so only the solution accuses it. */
    private fun withWrongSun() = loaded().apply { onPlacement(TangoPlacement(WRONG_IDX, TangoCell.SUN)) }

    private fun TangoPlayController.culprits(): Set<Int> =
        spotlight?.roles?.filterValues { it == SpotlightRole.CULPRIT }?.keys.orEmpty()

    private fun lastSaved(): TangoSession = (TangoSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    // Press (A1, A9, A15)

    @Test
    fun `a press shows a spotlight, places nothing, leaves the legacy tile inert and counts once`() {
        val s = loaded()
        val before = s.session!!.grid()

        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(before, s.session!!.grid())
        assertNull(s.hintCell)
        assertNull(s.hintWhy)
        assertNull(s.rendererModel()!!.hintCell)
        assertEquals(1, s.hintsUsed)
        assertEquals(1, lastSaved().hintsUsed) // the count reaches the persisted bytes

        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(before, s.session!!.grid())
        assertEquals(1, s.hintsUsed)
    }

    @Test
    fun `a press on a stopped clock opens it even when no step has arrived`() {
        val scheduler = TestCoroutineScheduler()
        val s = loaded(compute = StandardTestDispatcher(scheduler))
        s.onBackground()
        assertNull(s.clockWindow)

        s.requestHint()

        assertNull(s.spotlight) // the search is still held
        assertNotNull(s.clockWindow)
    }

    @Test
    fun `a press on a stopped clock opens it when the press shows a step`() {
        val s = loaded()
        s.onBackground()

        s.requestHint()

        assertNotNull(s.spotlight)
        assertNotNull(s.clockWindow)
    }

    @Test
    fun `a completed session keeps the hint disabled and a press shows nothing and opens no clock`() {
        val s = loaded()
        for ((idx, value) in TangoPlayFixture.placements) s.onPlacement(TangoPlacement(idx, value))
        assertTrue(s.isComplete)
        assertFalse(s.hintEnabled)
        val used = s.hintsUsed

        s.requestHint()

        assertNull(s.spotlight)
        assertNull(s.clockWindow)
        assertEquals(used, s.hintsUsed)
    }

    @Test
    fun `at the ceiling a new press stays at 255 and the session still serialises`() {
        val s = stepwise()
        val full = TangoSession(ref = TangoPlayFixture.ref(today), puzzle = TangoPlayFixture.puzzle, hintsUsed = 255)
        assertNotNull(s.adoptRestored(TangoSessionCodec.serialize(full)))

        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(255, s.hintsUsed)
        assertEquals(255, (TangoSessionCodec.deserialize(s.persist()!!) as DecodeResult.Ok).value.hintsUsed)
    }

    // Culprit mark only (A4)

    @Test
    fun `a wrong sun is marked as the culprit, and the second press re-shows the mark and changes nothing`() {
        val s = withWrongSun()

        s.requestHint()
        assertEquals(setOf(WRONG_IDX), s.culprits())
        assertEquals(1, s.hintsUsed)
        val marked = s.session!!
        val playId = checkNotNull(s.spotlight).playId

        s.requestHint()

        assertEquals(setOf(WRONG_IDX), s.culprits())
        assertEquals(playId + 1, s.spotlight?.playId)
        assertEquals(TangoCell.SUN, s.session!!.placements[WRONG_IDX])
        assertEquals(marked.history, s.session!!.history)
        assertEquals(1, s.hintsUsed)
        assertEquals(1, lastSaved().hintsUsed)
    }

    @Test
    fun `the player's own clear takes the marked sun off and clears the mark`() {
        val s = withWrongSun()
        s.requestHint()
        s.requestHint()

        s.onPlacement(TangoPlacement(WRONG_IDX, null))

        assertNull(s.session!!.placements[WRONG_IDX])
        assertNull(s.spotlight)
        assertEquals(1, s.hintsUsed)
    }

    // Caption (A11)

    @Test
    fun `while the spotlight shows the caption slot and the violation line are empty, then both return`() {
        val scheduler = TestCoroutineScheduler()
        val s = loaded(scope = CoroutineScope(StandardTestDispatcher(scheduler)))
        s.onPlacement(TangoPlacement(2, TangoCell.SUN)) // row 0 now holds three suns in a row
        scheduler.advanceTimeBy(MotionTokens.SettleMillis + 1) // the new violation waits out the settle window
        scheduler.runCurrent()
        val phrase = s.violation
        assertNotNull(phrase)

        s.requestHint()
        scheduler.runCurrent()

        assertNotNull(s.spotlight)
        assertNull(s.hintCaption)
        assertNull(s.violation)
        assertNull(playCaption(s, s))

        s.onPlacement(TangoPlacement(2, TangoCell.SUN)) // a tap that changes nothing

        assertNull(s.spotlight)
        assertEquals(phrase, s.violation)
        assertEquals(phrase, playCaption(s, s))
    }

    // Input clears (A10)

    @Test
    fun `a placement clears the spotlight`() {
        val s = loaded().apply { requestHint() }

        s.onPlacement(TangoPlacement(2, TangoCell.MOON))

        assertNull(s.spotlight)
    }

    @Test
    fun `a tap that changes nothing clears the spotlight`() {
        val s = loaded().apply { requestHint() }

        s.onPlacement(TangoPlacement(0, TangoCell.MOON)) // a given: inert

        assertNull(s.spotlight)
    }

    @Test
    fun `undo clears the spotlight`() {
        val s = loaded().apply { onPlacement(TangoPlacement(2, TangoCell.MOON)) }
        s.requestHint()
        assertNotNull(s.spotlight)

        s.undo()

        assertNull(s.spotlight)
    }

    @Test
    fun `redo clears the spotlight`() {
        val s = loaded().apply {
            onPlacement(TangoPlacement(2, TangoCell.MOON))
            undo()
        }
        s.requestHint()
        assertNotNull(s.spotlight)

        s.redo()

        assertNull(s.spotlight)
    }

    @Test
    fun `clear clears the spotlight`() {
        val s = loaded().apply { onPlacement(TangoPlacement(2, TangoCell.MOON)) }
        s.requestHint()
        assertNotNull(s.spotlight)

        s.clearBoard()

        assertNull(s.spotlight)
    }

    @Test
    fun `replay clears the spotlight and forgets counted steps, so the same step counts again`() {
        val s = loaded().apply { requestHint() }
        val first = s.spotlight
        assertEquals(1, s.hintsUsed)

        s.replay()

        assertNull(s.spotlight)
        assertEquals(0, s.hintsUsed)

        s.requestHint()

        assertEquals(first?.roles, s.spotlight?.roles)
        assertEquals(1, s.hintsUsed)
    }

    @Test
    fun `after an unrelated move the same step re-shows twice for free`() {
        val s = loaded().apply { requestHint() }
        val first = checkNotNull(s.spotlight)
        assertEquals(1, s.hintsUsed)

        s.onPlacement(TangoPlacement(UNRELATED_IDX, TangoPlayFixture.solution[UNRELATED_IDX])) // a right value off the step
        assertNull(s.spotlight)
        s.requestHint()
        s.requestHint()

        assertEquals(first.roles, s.spotlight?.roles)
        assertEquals(1, s.hintsUsed)
        assertEquals(1, lastSaved().hintsUsed)
    }

    @Test
    fun `adopting a second session clears the spotlight and counts the same step again`() {
        val s = loaded().apply { requestHint() }
        val first = s.spotlight
        assertEquals(1, s.hintsUsed)

        assertEquals(Adopt.Adopted, s.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(today)))

        assertNull(s.spotlight)
        assertEquals(0, s.hintsUsed)
        s.requestHint()
        assertEquals(first?.roles, s.spotlight?.roles)
        assertEquals(1, s.hintsUsed)
    }

    @Test
    fun `restoring a session clears the spotlight and counts the same step again`() {
        val s = loaded().apply { requestHint() }
        val fresh = TangoSession(ref = TangoPlayFixture.ref(today), puzzle = TangoPlayFixture.puzzle)

        assertNotNull(s.adoptRestored(TangoSessionCodec.serialize(fresh)))

        assertNull(s.spotlight)
        s.requestHint()
        assertNotNull(s.spotlight)
        assertEquals(1, s.hintsUsed)
    }

    @Test
    fun `replay keeps the searched solution, so the solver runs once`() {
        var solves = 0
        val s = TangoPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}),
            now = { Instant.parse("2026-10-31T00:00:00Z") },
            hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
            solve = { solves++; TangoStepGame.boundedSolution(it) },
        )
        s.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(today))
        s.requestHint()

        s.replay()
        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(1, solves)
    }

    @Test
    fun `a search still pending when the player places never produces a spotlight`() {
        val scheduler = TestCoroutineScheduler()
        val s = loaded(compute = StandardTestDispatcher(scheduler))

        s.requestHint()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        scheduler.advanceUntilIdle()

        assertNull(s.spotlight)
        assertEquals(0, s.hintsUsed)
    }

    // Overlay (the board draws it, never takes taps)

    @Test
    fun `the callout shows on the board after a press and a tap on a cell places and clears it`() {
        val s = loaded()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())).Board(s, Modifier.fillMaxWidth())
            }
        }
        s.requestHint()
        composeRule.waitForIdle()
        val text = s.spotlight!!.callout!!.text
        composeRule.onNodeWithText(text).assertExists()

        composeRule.onNode(hasContentDescription(TangoSemantics.cellContentDescription(0, 2), substring = true))
            .performClick()
        composeRule.waitForIdle()

        assertNotNull(s.session!!.placements[2])
        assertNull(s.spotlight)
        composeRule.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun `with no spotlight the board semantics are the default renderer's, and a spotlight leaves none behind`() {
        val s = loaded()
        val model = s.rendererModel()!!
        s.requestHint()
        var shown: Spotlight? by mutableStateOf(null)
        var overlaid by mutableStateOf(false)
        val plain = TangoBoardRenderer(onPlacement = s::onPlacement)
        val withOverlay = TangoBoardRenderer(onPlacement = s::onPlacement, spotlight = { shown })
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                (if (overlaid) withOverlay else plain).Content(model, Modifier.fillMaxWidth())
            }
        }
        val baseline = semanticsTree()

        overlaid = true
        composeRule.waitForIdle()
        assertEquals(baseline, semanticsTree())

        shown = s.spotlight
        composeRule.waitForIdle()
        assertNotEquals(baseline, semanticsTree()) // the callout joins the tree while it shows

        shown = null
        composeRule.waitForIdle()
        assertEquals(baseline, semanticsTree())
    }

    /** How much of [fill] a pixel shows, 0..1: the channel spread [fill] has and white and the culprit red lack. */
    private fun Color.share(fill: Color): Float =
        if (fill.blue > fill.red) (blue - red) / (fill.blue - fill.red) else (green - blue) / (fill.green - fill.blue)

    /** The pixels of cell [idx] showing at least [VISIBLE_SHARE] of the board glyph's [fill]: the glyph and its rim. */
    private fun glyphPixels(idx: Int, fill: Color): Set<Int> {
        val pixels = composeRule
            .onNode(hasContentDescription(TangoSemantics.cellContentDescription(idx / 6, idx % 6), substring = true))
            .captureToImage()
            .toPixelMap()
        return (0 until pixels.width * pixels.height).filterTo(HashSet()) {
            pixels[it % pixels.width, it / pixels.width].share(fill) >= VISIBLE_SHARE
        }
    }

    private fun assertCulpritCovers(idx: Int, value: TangoCell, fill: androidx.compose.ui.graphics.Color) {
        val s = loaded().apply { onPlacement(TangoPlacement(idx, value)) }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())).Board(s, Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
        assertTrue("the board draws the $value", glyphPixels(idx, fill).isNotEmpty())

        s.requestHint()
        composeRule.waitForIdle()

        assertEquals(setOf(idx), s.culprits())
        assertEquals("board-$value pixels left under the culprit", emptySet<Int>(), glyphPixels(idx, fill))
    }

    @Test
    fun `the culprit covers every pixel of the board's own wrong moon, its crescent's inner arc included`() =
        assertCulpritCovers(WRONG_MOON_IDX, TangoCell.MOON, LightBoardTokens.moonFill)

    @Test
    fun `the culprit covers every pixel of the board's own wrong sun`() =
        assertCulpritCovers(WRONG_IDX, TangoCell.SUN, LightBoardTokens.sunFill)

    /** The unmerged tree without node ids or action lambda identities, which a recomposition renews. */
    private fun semanticsTree(): String = composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        .replace(Regex("#\\d+|@[0-9a-f]+"), "")

    private companion object {
        /** Row 0 is S S M S M M: a sun at cell 4 is off the solution but breaks no rule. */
        const val WRONG_IDX = 4

        /** A moon where the solution has a sun; its SAME marker partner below is empty, so no rule breaks. */
        const val WRONG_MOON_IDX = 3

        /** Row 1, column 4: empty, a moon in the solution, off the first step, which stays the one shown after it. */
        const val UNRELATED_IDX = 10

        /** A tenth of the glyph's colour counts as a surviving pixel: the faintest rim a player could see. */
        const val VISIBLE_SHARE = 0.10f
    }
}
