package com.ivandsky.puzzlebox.insights.share

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.insights.recap.SolvedFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The poster at its real size: a long value or name fits instead of clipping, in every style, and a reader hears the final values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h1920dp-mdpi")
class RecapShareImageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val style = mutableStateOf(ShareStyle.Classic)

    private fun show(content: RecapShareContent, shown: ShareStyle) {
        style.value = shown
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) { RecapShareImage(content, style.value) }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node: SemanticsNodeInteraction = rule.onNodeWithText(text, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        rule.runOnUiThread { node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        return results.single()
    }

    @Test
    fun `the longest values fit their tiles on one line in every style`() {
        show(RecapShareContent.of("October 2026", "Ivan", ShareContract.longest, SolvedFixtures.gameName), ShareStyle.Classic)
        for (style in ShareStyle.entries) {
            this.style.value = style
            rule.waitForIdle()

            for (value in listOf("155 of 155", "99h 59m", "31 days")) {
                val layout = layoutOf(value)
                assertFalse("$style $value overflows", layout.didOverflowWidth)
                assertEquals("$style $value wraps", 1, layout.lineCount)
            }
        }
    }

    @Test
    fun `a long display name wraps by word inside the image and never loses a word`() {
        show(RecapShareContent.of("October 2026", ShareContract.LONG_NAME, ShareContract.longest, SolvedFixtures.gameName), ShareStyle.Classic)
        rule.waitForIdle()

        val layout = layoutOf(ShareContract.LONG_NAME)
        val lines = (0 until layout.lineCount).map { ShareContract.LONG_NAME.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim() }
        assertFalse(layout.didOverflowHeight)
        assertTrue("a word was cut: $lines", lines.any { it.contains("Featherstonehaugh") } && lines.any { it.contains("Maximiliana") })
    }

    @Test
    fun `a year's Strands at six solves keeps a readable count and its whole name in every style`() {
        show(RecapShareContent.of("2026", "Ivan", ShareContract.year, SolvedFixtures.gameName), ShareStyle.Classic)
        for (style in ShareStyle.entries) {
            this.style.value = style
            rule.waitForIdle()

            val name = layoutOf("Strands")
            assertEquals("$style Strands wraps", 1, name.lineCount)
            assertFalse("$style Strands is ellipsized", name.isLineEllipsized(0))
            assertEquals("$style Strands loses letters", "Strands".length, name.getLineEnd(0, visibleEnd = true))
            assertTrue("$style the count is too small", layoutOf("6").layoutInput.style.fontSize.value >= ShareStripe.CountMin)
        }
    }

    @Test
    fun `the image is read as one description with the final values`() {
        val content = RecapShareContent.of("October 2026", "Ivan", ShareContract.default, SolvedFixtures.gameName)
        show(content, ShareStyle.Night)

        rule.onNodeWithTag("recap.share.image").assertContentDescriptionEquals(content.description())
    }
}
