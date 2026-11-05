package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.HintDialog
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsHeader
import com.ivandsky.puzzlebox.games.strandsui.VerdictKind
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.games.strandsui.WordSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chrome composables on their own (SC3 header, SC4 hint control, A7 modals, A36 rating,
 * SC10 shake and meter under reduce motion, SC12 slot announcement), JVM via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsChromeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val header = StrandsHeader(clue = "Bake sale \uD83C\uDF70", puzzleNumber = 669, credit = "by Tess Tester")

    private fun show(content: @Composable () -> Unit) = rule.setContent { PuzzleBoxTheme(darkTheme = false) { content() } }

    @Test
    fun `the header shows the label, the clue verbatim, the number with the credit, and the counter`() {
        show {
            Column {
                ThemeCard(clue = header.clue, rating = null)
                StrandsHeaderLine(header)
                FoundCounter(found = 1, total = 4)
            }
        }
        rule.onNodeWithTag("strands.theme_label").assertTextEquals(StrandsCopy.THEME_LABEL)
        rule.onNodeWithTag("strands.clue").assertTextEquals("Bake sale \uD83C\uDF70")
        rule.onNodeWithTag("strands.header_line").assertTextEquals("No. 669 · by Tess Tester")
        rule.onNodeWithTag("strands.counter").assertTextEquals("1 of 4 theme words found")
    }

    @Test
    fun `a solved card shows the rating word in place of the label`() {
        show { ThemeCard(clue = header.clue, rating = StrandsRating.Great) }
        rule.onNodeWithTag("strands.theme_label").assertTextEquals("Great!")
        rule.onAllNodesWithText(StrandsCopy.THEME_LABEL).assertCountEquals(0)
        rule.onNodeWithTag("strands.clue").assertTextEquals(header.clue)
    }

    @Test
    fun `with no hint available the control is disabled and reads its progress in thirds`() {
        var presses = 0
        show { StrandsHintControl(HintUi(available = false, meterThirds = 2), onHint = { presses++ }, reduceMotion = true) }
        rule.onNodeWithTag("strands.hint")
            .assertIsNotEnabled()
            .assert(hasContentDescription(StrandsCopy.HINT))
            .assert(hasStateDescription("2 of 3 words toward the next hint"))
            .performClick()
        assertEquals(0, presses)
    }

    @Test
    fun `an available hint enables the control, which presses through`() {
        var presses = 0
        show { StrandsHintControl(HintUi(available = true, meterThirds = 0), onHint = { presses++ }, reduceMotion = true) }
        rule.onNodeWithTag("strands.hint")
            .assertIsEnabled()
            .assert(hasStateDescription("available"))
            .performClick()
        assertEquals(1, presses)
    }

    @Test
    fun `the control's only label is Hint, never a used count (SC4)`() {
        show { StrandsHintControl(HintUi(available = true, meterThirds = 2), onHint = {}, reduceMotion = true) }
        val node = rule.onNodeWithTag("strands.hint").fetchSemanticsNode()
        val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
        val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue(texts.isEmpty())
        assertEquals(listOf(StrandsCopy.HINT), descriptions)
        assertTrue(texts.plus(descriptions).none { label -> label.any(Char::isDigit) })
    }

    @Test
    fun `the on-board modal shows the A7 copy and dismisses`() {
        var dismissed = 0
        show { HintModal(HintDialog.OnBoard, onDismiss = { dismissed++ }) }
        rule.onNodeWithText(StrandsCopy.HINT_ON_BOARD_TITLE).assertExists()
        rule.onNodeWithText(StrandsCopy.HINT_ON_BOARD_BODY).assertExists()
        rule.onNodeWithTag("strands.hint_dialog_dismiss").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `the none-available modal shows the A7 copy`() {
        show { HintModal(HintDialog.NoneAvailable, onDismiss = {}) }
        rule.onNodeWithText(StrandsCopy.HINT_NONE_AVAILABLE_TITLE).assertExists()
        rule.onNodeWithText(StrandsCopy.HINT_NONE_AVAILABLE_BODY).assertExists()
    }

    @Test
    fun `a verdict is a polite live region announcing its copy and the counter (SC12)`() {
        show {
            WordSlotLine(WordSlot.Verdict("PIES", VerdictKind.Theme, seq = 1), found = 1, total = 4, reduceMotion = true)
        }
        rule.onNodeWithTag("strands.slot")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(hasContentDescription("PIES. 1 of 4 theme words found"))
    }

    @Test
    fun `the slot keeps one height whatever it shows`() {
        var slot: WordSlot by mutableStateOf(WordSlot.Empty)
        show { WordSlotLine(slot, found = 0, total = 4, reduceMotion = true) }
        val empty = rule.onNodeWithTag("strands.slot").getUnclippedBoundsInRoot().height
        for (next in listOf(
            WordSlot.Tracing("PIE"),
            WordSlot.Verdict("PIES", VerdictKind.Theme, seq = 1),
            WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq = 2),
            WordSlot.Verdict(StrandsCopy.TOO_SHORT, VerdictKind.Reject, seq = 3),
        )) {
            slot = next
            rule.onNodeWithTag("strands.slot").assertHeightIsEqualTo(empty)
        }
    }

    /** Holds the latest reader a probe composed, so the test reads the animation off the clock. */
    private class Probe { var read: () -> Float = { Float.NaN } }

    @Composable
    private fun ShakeProbe(seq: State<Int?>, reduceMotion: Boolean, probe: Probe) {
        probe.read = rememberRejectShake(seq.value, reduceMotion)
    }

    /** With the clock paused nothing flushes a test-thread write, so the recomposer would never see it. */
    private fun <T> MutableState<T>.post(value: T) {
        this.value = value
        Snapshot.sendApplyNotifications()
    }

    @Composable
    private fun MeterProbe(target: State<Float>, reduceMotion: Boolean, probe: Probe) {
        probe.read = rememberMeterFill(target.value, reduceMotion)
    }

    private fun shake(reduceMotion: Boolean): Pair<MutableState<Int?>, Probe> {
        val seq = mutableStateOf<Int?>(null)
        val probe = Probe()
        rule.mainClock.autoAdvance = false
        show { ShakeProbe(seq, reduceMotion, probe) }
        rule.mainClock.advanceTimeByFrame()
        return seq to probe
    }

    private fun meter(reduceMotion: Boolean): Pair<MutableState<Float>, Probe> {
        val target = mutableStateOf(1f / 3)
        val probe = Probe()
        rule.mainClock.autoAdvance = false
        show { MeterProbe(target, reduceMotion, probe) }
        rule.mainClock.advanceTimeByFrame()
        return target to probe
    }

    @Test
    fun `a rejection shakes, and a repeat of the same copy shakes again`() {
        val (seq, offset) = shake(reduceMotion = false)
        assertEquals(0f, offset.read())
        seq.post(1)
        rule.mainClock.advanceTimeBy(ProbeMs)
        assertNotEquals(0f, offset.read())
        rule.mainClock.advanceTimeBy(SettledMs)
        assertEquals(0f, offset.read())
        seq.post(2)
        rule.mainClock.advanceTimeBy(ProbeMs)
        assertNotEquals(0f, offset.read())
    }

    @Test
    fun `under reduce motion a rejection does not shake`() {
        val (seq, offset) = shake(reduceMotion = true)
        seq.post(1)
        repeat(SettledFrames) {
            rule.mainClock.advanceTimeByFrame()
            assertEquals(0f, offset.read())
        }
    }

    @Test
    fun `the meter fills toward a new third over its token`() {
        val (target, fill) = meter(reduceMotion = false)
        assertEquals(1f / 3, fill.read(), 1e-4f)
        target.post(2f / 3)
        rule.mainClock.advanceTimeBy(ProbeMs)
        assertTrue(fill.read() > 1f / 3 && fill.read() < 2f / 3)
        rule.mainClock.advanceTimeBy(SettledMs)
        assertEquals(2f / 3, fill.read(), 1e-4f)
    }

    @Test
    fun `under reduce motion the meter lands on its third at once`() {
        val (target, fill) = meter(reduceMotion = true)
        target.post(2f / 3)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(2f / 3, fill.read(), 1e-4f)
    }

    private companion object {
        /** Inside both the 300 ms shake and the 240 ms meter fill. */
        const val ProbeMs = 80L

        /** Past either motion's end. */
        const val SettledMs = 400L
        const val SettledFrames = 25
    }
}
