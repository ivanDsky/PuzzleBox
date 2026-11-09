package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
 * once through the persisted session (A9), erases a wrong entry on the second press as one undo unit (A4), clears on
 * every input (A10), hides the caption and the violation line while it shows (A11), and the board draws it without
 * taking taps. The press rules are StepHints' (04b) and the step search TangoStepGame's (30).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoStepwiseHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.parse("2026-10-31")
    private val saved = mutableListOf<ByteArray>()

    private fun stepwise(compute: CoroutineDispatcher = Dispatchers.Unconfined) = TangoPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = { saved += it }, onWon = {}),
        now = { Instant.parse("2026-10-31T00:00:00Z") },
        hintMode = HintMode.Stepwise(compute),
    )

    private fun loaded(compute: CoroutineDispatcher = Dispatchers.Unconfined) = stepwise(compute).also {
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

    // Erase (A4)

    @Test
    fun `a wrong sun is shown as the culprit, then erased by the second press as one undo unit`() {
        val s = withWrongSun()

        s.requestHint()
        assertEquals(setOf(WRONG_IDX), s.culprits())
        assertEquals(1, s.hintsUsed)
        val historyBefore = s.session!!.history.done.size

        s.requestHint()

        assertNull(s.session!!.placements[WRONG_IDX])
        assertNull(s.spotlight)
        assertEquals(1, s.hintsUsed)
        assertEquals(historyBefore + 1, s.session!!.history.done.size)
        assertTrue(s.canUndo)
        assertEquals(s.session, lastSaved())

        s.undo()

        assertEquals(TangoCell.SUN, s.session!!.placements[WRONG_IDX])
        assertEquals(1, s.hintsUsed)
    }

    // Caption (A11)

    @Test
    fun `while the spotlight shows the caption slot and the violation line are empty, then both return`() {
        val s = loaded()
        s.onPlacement(TangoPlacement(2, TangoCell.SUN)) // row 0 now holds three suns in a row
        Thread.sleep(SETTLED_MS) // the new violation waits out the settle window
        val phrase = s.violation
        assertNotNull(phrase)

        s.requestHint()

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
    fun `a search still pending when the player places never produces a spotlight`() {
        val scheduler = TestCoroutineScheduler()
        val s = loaded(compute = StandardTestDispatcher(scheduler))

        s.requestHint()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        scheduler.advanceUntilIdle()

        assertNull(s.spotlight)
        assertEquals(0, s.hintsUsed)
    }

    @Test
    fun `a held search delivers once released when nothing intervenes`() {
        val scheduler = TestCoroutineScheduler()
        val s = loaded(compute = StandardTestDispatcher(scheduler))

        s.requestHint()
        assertNull(s.spotlight)
        scheduler.advanceUntilIdle()

        assertNotNull(s.spotlight)
        assertEquals(1, s.hintsUsed)
    }

    // Overlay (the board draws it, never takes taps)

    @Test
    fun `the callout shows on the board after a press and a tap on a cell places and clears it`() {
        val s = loaded()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { TangoPlayUi().Board(s, Modifier.fillMaxWidth()) }
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
        assertNotEquals(today, semanticsTree()) // the callout joins the tree while it shows

        shown = null
        composeRule.waitForIdle()
        assertEquals(baseline, semanticsTree())
    }

    /** The unmerged tree without node ids or action lambda identities, which a recomposition renews. */
    private fun semanticsTree(): String = composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        .replace(Regex("#\\d+|@[0-9a-f]+"), "")

    private companion object {
        /** Row 0 is S S M S M M: a sun at cell 4 is off the solution but breaks no rule. */
        const val WRONG_IDX = 4

        /** Past MotionTokens.SettleMillis (500 ms), which the env's Unconfined scope waits in real time. */
        const val SETTLED_MS = 600L
    }
}
