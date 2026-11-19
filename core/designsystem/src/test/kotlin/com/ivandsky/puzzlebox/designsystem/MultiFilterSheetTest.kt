package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The multi-select sheet and its chip (epic picker-filters, ticket 03): rows toggle in place and the sheet stays, the "all" row clears the
 * group, the check slot is reserved and fades on the clock, the close paths, the chip's label rules and two presentations, semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class MultiFilterSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private val picks = mutableStateOf(emptySet<String>())
    private val toggles = mutableListOf<String>()
    private val openChanges = mutableListOf<Boolean>()
    private var clears = 0
    private var dismissals = 0
    private var inlineOpens = 0

    private fun option(value: String, label: String) = FilterOption(value, label, tag = "opt.$value")

    private val options = listOf(option("a", "Alpha"), option("b", "Beta"), option("c", "Gamma"))

    private fun toggle(value: String) {
        toggles += value
        picks.value = if (value in picks.value) picks.value - value else picks.value + value
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun showSheet() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                MultiFilterSheet(
                    title = "Game",
                    allLabel = "All games",
                    options = options,
                    selected = picks.value,
                    onToggle = ::toggle,
                    onClearGroup = { clears++; picks.value = emptySet() },
                    onDismiss = { dismissals++ },
                    tag = "x.filter.game",
                    allTag = "x.filter.game.all",
                )
            }
        }
        settle()
    }

    private fun showChip(
        presentation: FilterPresentation = FilterPresentation.Sheet,
        enabled: Boolean = true,
        restoration: StateRestorationTester? = null,
    ) {
        val content: @Composable () -> Unit = {
            PuzzleBoxTheme(darkTheme = false) {
                MultiFilterChip(
                    name = "Game",
                    allLabel = "All games",
                    options = options,
                    selected = picks.value,
                    onToggle = ::toggle,
                    onClearGroup = { clears++; picks.value = emptySet() },
                    enabled = enabled,
                    onOpenChange = { openChanges += it },
                    presentation = presentation,
                    tag = "x.filter.game",
                )
            }
        }
        if (restoration != null) restoration.setContent(content) else composeRule.setContent(content)
        settle()
    }

    private fun pick(values: Set<String>) {
        picks.value = values
        Snapshot.sendApplyNotifications()
        advance(FRAME_MS * 2)
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun collapsed() = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")

    /** How far the row's check stands out of the row's own fill: 0 while it is hidden. */
    private fun checkStrength(rowTag: String): Float {
        val image = composeRule.onNode(hasTestTag(FILTER_ROW_CHECK_TAG) and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val base = pixels[0, 0].luminance()
        return (0 until image.width).maxOf { x -> (0 until image.height).maxOf { y -> abs(pixels[x, y].luminance() - base) } }
    }

    private fun labelBounds(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `a tap toggles the row and the sheet stays open, again deselects`() {
        showSheet()

        node("opt.b").performClick()
        advance(SETTLE_MS)

        assertEquals(listOf("b"), toggles)
        assertEquals(0, dismissals)
        node("opt.b").assertIsSelected()
        node("opt.a").assertIsNotSelected()
        node("x.filter.game.all").assertIsNotSelected()
        assertTrue("check shows: ${checkStrength("opt.b")}", checkStrength("opt.b") > 0.2f)
        assertTrue("the other rows show none", checkStrength("opt.a") < 0.02f)

        node("opt.b").performClick()
        advance(SETTLE_MS)

        assertEquals(listOf("b", "b"), toggles)
        node("opt.b").assertIsNotSelected()
        node("x.filter.game.all").assertIsSelected()
        assertEquals(0, dismissals)
    }

    @Test
    fun `several rows can be picked at once`() {
        showSheet()

        node("opt.a").performClick()
        node("opt.c").performClick()
        advance(SETTLE_MS)

        node("opt.a").assertIsSelected()
        node("opt.c").assertIsSelected()
        node("opt.b").assertIsNotSelected()
    }

    @Test
    fun `the all row is selected with nothing picked, clears the group once and the sheet stays`() {
        picks.value = setOf("a", "c")
        showSheet()
        node("x.filter.game.all").assertIsNotSelected()
        assertTrue(checkStrength("x.filter.game.all") < 0.02f)

        node("x.filter.game.all").performClick()
        advance(SETTLE_MS)

        assertEquals(1, clears)
        assertEquals(0, dismissals)
        assertTrue(toggles.isEmpty())
        node("x.filter.game.all").assertIsSelected()
        node("opt.a").assertIsNotSelected()
        assertTrue("the all row shows its check: ${checkStrength("x.filter.game.all")}", checkStrength("x.filter.game.all") > 0.2f)
    }

    @Test
    fun `the check slot is reserved so a label has the same bounds selected and not`() {
        showSheet()
        val before = labelBounds("Beta")
        val allBefore = labelBounds("All games")

        node("opt.b").performClick()
        node("x.filter.game.all").performClick()
        node("x.filter.game.all").performClick()
        advance(SETTLE_MS)
        val after = labelBounds("Beta")

        assertEquals(before.left.value, after.left.value, 0.01f)
        assertEquals(before.right.value, after.right.value, 0.01f)
        assertEquals(allBefore.left.value, labelBounds("All games").left.value, 0.01f)
        assertEquals(allBefore.right.value, labelBounds("All games").right.value, 0.01f)
    }

    @Test
    fun `the check fades in over 120 ms on the clock`() {
        val on = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(Color.White)) {
                    FilterOptionRow("Alpha", selected = on.value, onClick = {}, modifier = Modifier.testTag("row"), multi = true)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue(checkStrength("row") < 0.02f)

        on.value = true
        advance(FRAME_MS)
        val start = checkStrength("row")
        advance(MotionTokens.DurationShort + 2 * FRAME_MS)
        val end = checkStrength("row")
        advance(SETTLE_MS)
        val rest = checkStrength("row")

        assertTrue("barely there after a frame: $start vs $end", start < end * 0.6f)
        assertEquals("settled by 120 ms", rest, end, rest * 0.1f)
        assertTrue("visible at the end: $end", end > 0.2f)
    }

    @Test
    fun `there is no Apply, the close button is 48dp and dismisses and the scrim dismisses`() {
        showSheet()

        composeRule.onAllNodes(hasText("Apply", substring = true, ignoreCase = true)).assertCountEquals(0)
        node("x.filter.game.sheet.close").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Close").performClick()
        advance(SETTLE_MS)
        assertEquals("close", 1, dismissals)

        composeRule.onNodeWithContentDescription("Close sheet").performTouchInput { click(topLeft + Offset(4f, 4f)) }
        repeat(60) { advance(FRAME_MS) }
        assertEquals("scrim", 2, dismissals)
        assertTrue(toggles.isEmpty() && clears == 0)
    }

    @Test
    fun `the title is a heading and the pane title, the rows are checkboxes in a selectable group`() {
        picks.value = setOf("a")
        showSheet()

        composeRule.onNodeWithText("Game").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        node("x.filter.game.sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Game"))
        listOf("x.filter.game.all").plus(options.map { it.tag!! }).forEach {
            node(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }
        node("opt.a").assertIsSelected()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
    }

    @Test
    fun `the chip says its name, the one label, then the name and a count`() {
        showChip()
        node("x.filter.game").assertTextEquals("Game")

        pick(setOf("c"))
        node("x.filter.game").assertTextEquals("Gamma")

        pick(setOf("c", "a"))
        node("x.filter.game").assertTextEquals("Game · 2")

        pick(setOf("c", "a", "b"))
        node("x.filter.game").assertTextEquals("Game · 3")
        assertEquals("picked shows the check", 1, count(FILTER_CHIP_CHECK_TAG))
    }

    @Test
    fun `the chip opens its sheet, hears open and close, and the sheet stays up through toggles`() {
        showChip()
        assertEquals(0, count(FILTER_CHIP_CHECK_TAG))

        node("x.filter.game").performClick()
        advance(SETTLE_MS)
        assertEquals(1, count("x.filter.game.sheet"))
        node("x.filter.game").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        node("opt.a").performClick()
        advance(SETTLE_MS)
        assertEquals(1, count("x.filter.game.sheet"))
        assertEquals(listOf(true), openChanges)
        node("x.filter.game").assertTextEquals("Alpha")

        composeRule.onNodeWithContentDescription("Close").performClick()
        advance(SETTLE_MS)

        assertEquals(0, count("x.filter.game.sheet"))
        assertEquals(listOf(true, false), openChanges)
    }

    @Test
    fun `a disabled chip does not open`() {
        showChip(enabled = false)

        node("x.filter.game").performClick()
        advance(SETTLE_MS)

        assertEquals(0, count("x.filter.game.sheet"))
        assertTrue(openChanges.isEmpty())
    }

    @Test
    fun `an open sheet survives a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        showChip(restoration = restoration)
        node("x.filter.game").performClick()
        advance(SETTLE_MS)

        restoration.emulateSavedInstanceStateRestore()
        advance(SETTLE_MS)

        assertEquals(1, count("x.filter.game.sheet"))
    }

    @Test
    fun `an inline chip reports its click and opens nothing`() {
        picks.value = setOf("a", "b")
        showChip(presentation = FilterPresentation.Inline { inlineOpens++ })
        node("x.filter.game").assertTextEquals("Game · 2").assert(collapsed())

        node("x.filter.game").performClick()
        advance(SETTLE_MS)

        assertEquals(1, inlineOpens)
        assertEquals(0, count("x.filter.game.sheet"))
        node("x.filter.game").assert(collapsed())
        assertTrue(openChanges.isEmpty())
    }

    @Test
    fun `a disabled inline chip does not report`() {
        showChip(presentation = FilterPresentation.Inline { inlineOpens++ }, enabled = false)

        node("x.filter.game").performClick()

        assertEquals(0, inlineOpens)
    }

    private companion object {
        const val SETTLE_MS = 1000L
        const val FRAME_MS = 16L
    }
}
