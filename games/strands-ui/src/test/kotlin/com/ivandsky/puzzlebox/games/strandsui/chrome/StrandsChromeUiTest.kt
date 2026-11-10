package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.gameui.HINT_COOLDOWN
import com.ivandsky.puzzlebox.gameui.LocalHintCooldown

/**
 * The chrome composables on their own (SC3 header, SC4 hint control semantics, A7 modals,
 * A36 rating, SC12 slot announcement), JVM via Robolectric.
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
        rule.onNodeWithTag("strands.counter").assertTextEquals("1 of 4 theme words found.")
    }

    @Test
    fun `a solved card shows the rating word in place of the label`() {
        show { ThemeCard(clue = header.clue, rating = StrandsRating.Great) }
        rule.onNodeWithTag("strands.theme_label").assertTextEquals("Great!")
        rule.onAllNodesWithText(StrandsCopy.THEME_LABEL).assertCountEquals(0)
        rule.onNodeWithTag("strands.clue").assertTextEquals(header.clue)
    }

    @Test
    fun `with no hint available the control announces unavailable with its thirds, and still presses`() {
        var presses = 0
        show { StrandsHintControl(HintUi(count = 0, meterThirds = 2), onHint = { presses++ }, reduceMotion = true) }
        rule.onNodeWithTag("strands.hint")
            .assertIsEnabled()
            .assert(hasContentDescription(StrandsCopy.HINT))
            .assert(hasStateDescription("No hint available, 2 of 3 words toward the next hint"))
            .performClick()
        assertEquals(1, presses)
    }

    @Test
    fun `an available hint enables the control, which presses through`() {
        var presses = 0
        show { StrandsHintControl(HintUi(count = 2, meterThirds = 0), onHint = { presses++ }, reduceMotion = true) }
        rule.onNodeWithTag("strands.hint")
            .assertIsEnabled()
            .assert(hasStateDescription("2 hints available, 0 of 3 words toward the next hint"))
            .performClick()
        assertEquals(1, presses)
    }

    @Test
    fun `under the hint cooldown a press disables the control until the wait is over (F2)`() {
        var presses = 0
        show {
            CompositionLocalProvider(LocalHintCooldown provides HINT_COOLDOWN) {
                StrandsHintControl(HintUi(count = 0, meterThirds = 1), onHint = { presses++ }, reduceMotion = true)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("strands.hint").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("strands.hint").assertIsNotEnabled().performClick()
        assertEquals(1, presses)
        ShadowSystemClock.advanceBy(Duration.ofMillis(5_100))
        rule.mainClock.advanceTimeBy(5_100)
        rule.onNodeWithTag("strands.hint").assertIsEnabled().performClick()
        assertEquals(2, presses)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a waiting hint is a chunky Strands-pink button on its lip`() {
        var lip = Color.Unspecified
        show {
            lip = StrandsBrand.accent.colors().lip
            Box(Modifier.testTag("frame")) {
                StrandsHintControl(HintUi(count = 1, meterThirds = 0), onHint = {}, reduceMotion = true)
            }
        }
        val image = rule.onNodeWithTag("frame").captureToImage().toPixelMap()
        assertEquals(lip, image[image.width / 2, image.height - 2])
    }

    @Test
    fun `the control's only label is Hint, never a used count (SC4)`() {
        show { StrandsHintControl(HintUi(count = 1, meterThirds = 2), onHint = {}, reduceMotion = true) }
        val node = rule.onNodeWithTag("strands.hint").fetchSemanticsNode()
        val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
        val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue(texts.all { it == StrandsCopy.HINT })
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
            .assert(hasContentDescription("PIES. 1 of 4 theme words found."))
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
}
