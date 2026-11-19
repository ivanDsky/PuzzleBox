package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.robolectric.shadows.ShadowDialog

/**
 * The single-select sheet and its chip (epic picker-filters, ticket 02): the rows and their selection, the pick and its 150 ms close,
 * the close paths, sizes and the 92% cap, tags and semantics, the chip's two presentations, reduced motion and the contrast floors.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SingleFilterSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private val picked = mutableStateOf<String?>(null)
    private val selects = mutableListOf<String?>()
    private val openChanges = mutableListOf<Boolean>()
    private var dismissals = 0
    private var inlineOpens = 0
    private var windowHeight = 0.dp

    private fun option(value: String, label: String = value.uppercase()) =
        FilterOption(value, label, icon = { tint -> Box(Modifier.size(24.dp).background(tint)) }, tag = "opt.$value")

    private val options = listOf(option("a", "Alpha"), option("b", "Beta"), option("c", "Gamma"))

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun showSheet(rows: List<FilterOption<String>> = options, fontScale: Float = 1f, followCaller: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Scaled(fontScale) {
                    windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                    SingleFilterSheet(
                        title = "Game",
                        allLabel = "All games",
                        options = rows,
                        selected = picked.value,
                        onSelect = { selects += it; if (followCaller) picked.value = it },
                        onDismiss = { dismissals++ },
                        tag = "x.filter.game",
                        allTag = "x.filter.game.all",
                    )
                }
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
                SingleFilterChip(
                    name = "Game",
                    allLabel = "All games",
                    options = options,
                    selected = picked.value,
                    onSelect = { picked.value = it },
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

    @Composable
    private fun Scaled(fontScale: Float, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun textInset(text: String, rowTag: String) =
        (composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left - node(rowTag).getUnclippedBoundsInRoot().left).value

    private fun top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    private fun collapsed() = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")

    private fun expanded() = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded")

    @Test
    fun `the all row comes first with no icon and is selected only when nothing is picked`() {
        showSheet()

        node("x.filter.game.all").assertIsSelected()
        assertTrue(top("x.filter.game.all") < top("opt.a") && top("opt.a") < top("opt.b") && top("opt.b") < top("opt.c"))
        options.forEach { node(it.tag!!).assertIsNotSelected() }
        assertEquals("the all row's text starts at the row's 16dp padding", 16f, textInset("All games", "x.filter.game.all"), 0.5f)
        assertEquals("an icon row's text starts after the 24dp icon and a 16dp gap", 56f, textInset("Alpha", "opt.a"), 0.5f)
    }

    @Test
    fun `the picked row is the only selected one`() {
        picked.value = "b"
        showSheet()

        node("opt.b").assertIsSelected()
        node("opt.a").assertIsNotSelected()
        node("x.filter.game.all").assertIsNotSelected()
    }

    @Test
    fun `a tap moves the highlight at once, reports once and closes at 150 ms while taps in between do nothing`() {
        showSheet()

        node("opt.b").performClick()
        advance(FRAME_MS)
        node("opt.b").assertIsSelected()
        advance(100)
        node("opt.c").performClick()

        assertEquals(listOf<String?>("b"), selects)
        node("opt.c").assertIsNotSelected()
        assertEquals("not closed at 100 ms", 0, dismissals)
        advance(60)
        assertEquals("closed by 150 ms", 1, dismissals)
    }

    @Test
    fun `the all row reports null`() {
        picked.value = "a"
        showSheet(followCaller = true)

        node("x.filter.game.all").performClick()
        advance(FRAME_MS)

        assertEquals(listOf<String?>(null), selects)
        node("x.filter.game.all").assertIsSelected()
    }

    @Test
    fun `a re-tap of the picked row reports it again and closes`() {
        picked.value = "b"
        showSheet(followCaller = true)

        node("opt.b").performClick()
        advance(160)

        assertEquals(listOf<String?>("b"), selects)
        node("opt.b").assertIsSelected()
        assertEquals(1, dismissals)
    }

    @Test
    fun `the close button is 48dp, described Close and dismisses at once`() {
        showSheet()

        node("x.filter.game.sheet.close").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Close").performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun `the scrim and system back dismiss`() {
        showSheet()
        composeRule.onNodeWithContentDescription("Close sheet").performTouchInput { click(topLeft + Offset(4f, 4f)) }
        repeat(60) { advance(FRAME_MS) }
        assertEquals("scrim", 1, dismissals)

        val dialog = ShadowDialog.getLatestDialog()
        composeRule.runOnUiThread { dialog.onBackPressed() }
        advance(SETTLE_MS)
        assertEquals("back", 2, dismissals)
    }

    @Test
    fun `rows are at least 52dp with a 48dp touch height and a long label wraps and grows the row at font scale 1 point 3`() {
        val wordy = option("w", "A long source label that has to wrap onto several lines in a narrow sheet")
        showSheet(options + wordy, fontScale = 1.3f)

        options.forEach {
            node(it.tag!!).assertHeightIsAtLeast(52.dp)
            assertTrue(composeRule.onNodeWithTag(it.tag!!).fetchSemanticsNode().touchBoundsInRoot.height >= 48 * composeRule.density.density)
        }
        assertTrue("the wrapped row grows: ${node("opt.w").getUnclippedBoundsInRoot()} vs ${node("opt.a").getUnclippedBoundsInRoot()}", node("opt.w").getUnclippedBoundsInRoot().height > node("opt.a").getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `a three row list is a short sheet`() {
        showSheet()

        assertTrue(windowHeight > 400.dp)
        assertTrue(node("x.filter.game.sheet").getUnclippedBoundsInRoot().height < windowHeight * 0.6f)
    }

    @Test
    fun `a long list scrolls inside a sheet no taller than 92 percent of the window`() {
        val many = List(30) { option("r$it", "Row $it") }
        showSheet(many)

        assertTrue(node("x.filter.game.sheet").getUnclippedBoundsInRoot().height <= windowHeight * 0.92f + 0.5.dp)
        node("opt.r29").assertIsNotDisplayed()
        composeRule.mainClock.autoAdvance = true
        node("opt.r29").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the title is a heading and the sheet's pane title, the rows are radios in a selectable group`() {
        showSheet()

        composeRule.onNodeWithText("Game").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        node("x.filter.game.sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Game"))
        listOf("x.filter.game.all").plus(options.map { it.tag!! }).forEach {
            node(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
    }

    @Test
    fun `the chip says its name, opens its sheet, and closes it again`() {
        showChip()
        composeRule.onNodeWithText("Game").assert(collapsed())

        node("x.filter.game").performClick()
        advance(SETTLE_MS)

        assertEquals(1, count("x.filter.game.sheet"))
        node("x.filter.game").assert(expanded())
        assertEquals(listOf(true), openChanges)

        node("opt.b").performClick()
        advance(160)

        assertEquals(0, count("x.filter.game.sheet"))
        node("x.filter.game").assert(collapsed())
        composeRule.onNodeWithText("Beta").assert(collapsed())
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
        picked.value = "c"
        showChip(presentation = FilterPresentation.Inline { inlineOpens++ })

        composeRule.onNodeWithText("Gamma").assert(collapsed())
        assertEquals(1, count(FILTER_CHIP_CHECK_TAG))
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

    /** The black box's luminance at the start, at 60 ms and at the end of its fade, over a white page. */
    private fun fadeSamples(reduced: Boolean): Triple<Float, Float, Float> {
        animatorScale(if (reduced) 0f else 1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(Color.White)) {
                    Box(Modifier.size(40.dp).testTag("fade").reducedFadeIn().background(Color.Black))
                }
            }
        }
        fun shade(): Float {
            val image = node("fade").captureToImage()
            return image.toPixelMap()[image.width / 2, image.height / 2].luminance()
        }
        composeRule.mainClock.advanceTimeByFrame()
        val start = shade()
        advance(MotionTokens.ReducedFadeDuration / 2L)
        val middle = shade()
        advance(MotionTokens.ReducedFadeDuration.toLong())
        return Triple(start, middle, shade())
    }

    @Test
    fun `reduced motion fades the content in over 120 ms`() {
        val (start, middle, end) = fadeSamples(reduced = true)

        assertEquals("dark at the end", 0f, end, 0.05f)
        assertTrue("faded out at the start: $start", start > 0.8f)
        assertTrue("part way at 60 ms: $middle", middle > end + 0.01f && middle < start - 0.5f)
    }

    @Test
    fun `without reduced motion the content is there at once`() {
        val (start, _, end) = fadeSamples(reduced = false)

        assertEquals(0f, start, 0.05f)
        assertEquals(0f, end, 0.05f)
    }

    @Test
    fun `selected text and the list border meet the contrast floors in light and dark`() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (name, scheme) ->
            val text = contrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertTrue("$name text on the selected row = $text", text >= 4.5f)
            val border = contrast(scheme.outline, scheme.surfaceContainerLow)
            assertTrue("$name list border on the sheet = $border", border >= 3f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }

    private companion object {
        const val SETTLE_MS = 1000L
        const val FRAME_MS = 16L
    }
}
