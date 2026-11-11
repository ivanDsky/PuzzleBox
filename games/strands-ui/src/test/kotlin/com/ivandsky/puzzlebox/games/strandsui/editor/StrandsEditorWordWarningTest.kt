package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.designsystem.LightWarning
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The word field is quiet for a dictionary word, a word still being typed and an empty field,
 * and warns (an amber border and one line, not the error red) only for text that is not a
 * dictionary word and can't become one, on the phone's compact field and the tablet's full one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorWordWarningTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}
    private val queue = QueueDispatcher()

    private fun showPhone() = rule.setContent {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalEditorDispatchers provides QueueDispatcherProvider(queue)) {
                Column { StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier.weight(1f)) }
            }
        }
    }

    private fun showPane() = rule.setContent {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalEditorDispatchers provides QueueDispatcherProvider(queue)) {
                StrandsEditorUi.PaneTools(controller, Modifier)
            }
        }
    }

    /** A four-cell path along the top row, selected, before the field is shown. */
    private fun tracePath() {
        listOf(0, 1, 2, 3).forEach { controller.traceTo(it) }
        controller.endTrace()
        controller.selectPath(0)
    }

    private fun type(text: String) {
        controller.typeWord(text)
        answerLookups()
    }

    private fun answerLookups() {
        rule.waitForIdle()
        queue.runAll()
        rule.waitForIdle()
    }

    private fun check() = rule.onNodeWithTag(CHECK, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.state(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun isError(): Boolean = rule.onNodeWithTag(FIELD).fetchSemanticsNode().config.contains(SemanticsProperties.Error)

    private fun assertQuiet(what: String) {
        check().assertDoesNotExist()
        rule.onNodeWithTag(FIT, useUnmergedTree = true).assertDoesNotExist()
        assertFalse("$what is no error", isError())
    }

    private fun assertWarns(what: String) {
        assertEquals("$what warns", WARN, check().assertIsDisplayed().state())
        rule.onNodeWithTag(FIT, useUnmergedTree = true).assertDoesNotExist()
        assertFalse("$what is no error", isError())
    }

    private fun quietForEach(show: () -> Unit) {
        tracePath()
        show()
        listOf("", "sta", "star").forEach {
            type(it)
            assertQuiet("'$it'")
        }
    }

    @Test
    fun `on the phone a dictionary word, a prefix and an empty field are quiet`() = quietForEach(::showPhone)

    @Test
    fun `in the tablet pane a dictionary word, a prefix and an empty field are quiet`() = quietForEach(::showPane)

    private fun unanswered(show: () -> Unit) {
        tracePath()
        show()
        controller.typeWord("xqzv")
        rule.waitForIdle()

        assertTrue(queue.pending > 0)
        assertQuiet("an unanswered lookup")
        queue.runAll()
        rule.waitForIdle()
        assertWarns("an answered lookup")
    }

    @Test
    fun `on the phone the field is quiet while the dictionary has not answered`() = unanswered(::showPhone)

    @Test
    fun `in the tablet pane the field is quiet while the dictionary has not answered`() = unanswered(::showPane)

    @Test
    fun `on the phone a word the dictionary lacks warns with a line under the field`() {
        tracePath()
        showPhone()
        // The field's bounds take in its supporting line once one shows: measure the box first.
        type("star")
        val field = rule.onNodeWithTag(FIELD).getUnclippedBoundsInRoot()
        type("xqzv")

        assertWarns("xqzv")
        val line = check().getUnclippedBoundsInRoot()
        assertTrue("line $line not under the field $field", line.top >= field.bottom - 0.5.dp)
        assertEquals(StrandsEditorCopy.NOT_IN_DICTIONARY, check().text())
    }

    @Test
    fun `in the tablet pane a word the dictionary lacks warns`() {
        tracePath()
        showPane()
        type("xqzv")

        assertWarns("xqzv")
        assertEquals(StrandsEditorCopy.NOT_IN_DICTIONARY, check().text())
    }

    private fun fullPrefix(show: () -> Unit) {
        tracePath()
        show()
        // STRO starts STRONG and the like, but it is not a word, and it uses all four cells.
        type("str")
        assertQuiet("a prefix shorter than the path")
        type("stro")
        assertWarns("a full-length prefix")
        type("star")
        assertQuiet("a word")
    }

    @Test
    fun `on the phone text that fills the path and is only a prefix warns until it is a word`() = fullPrefix(::showPhone)

    @Test
    fun `in the tablet pane text that fills the path and is only a prefix warns until it is a word`() = fullPrefix(::showPane)

    @Test
    fun `the phone field stays one box for a quiet word`() {
        tracePath()
        showPhone()
        type("star")

        assertTrue(rule.onNodeWithTag(FIELD).getUnclippedBoundsInRoot().height <= ONE_BOX)
    }

    private fun overflows(show: () -> Unit) {
        tracePath()
        show()
        type("stars")

        assertTrue(isError())
        assertEquals("TooLong", rule.onNodeWithTag(FIT, useUnmergedTree = true).state())
        check().assertDoesNotExist()
    }

    @Test
    fun `on the phone a word longer than its path is an error with its fit line and no dictionary warning`() = overflows(::showPhone)

    @Test
    fun `in the tablet pane a word longer than its path is an error with its fit line and no dictionary warning`() = overflows(::showPane)

    @Test
    fun `the warning border is amber, the error border stays red, a quiet field has neither`() {
        tracePath()
        showPhone()
        val light = LightWarning
        type("star")
        val quiet = rule.onNodeWithTag(FIELD).countPixels(light, LightColorScheme.error)
        type("xqzv")
        val warned = rule.onNodeWithTag(FIELD).countPixels(light, LightColorScheme.error)
        type("stars")
        val overflown = rule.onNodeWithTag(FIELD).countPixels(light, LightColorScheme.error)

        assertEquals("quiet: amber and red pixels", 0 to 0, quiet)
        assertTrue("warning shows no amber border: $warned", warned.first > 0)
        assertEquals("warning shows red", 0, warned.second)
        assertEquals("overflow shows amber", 0, overflown.first)
        assertTrue("overflow shows no red border: $overflown", overflown.second > 0)
    }

    /** Pixels in this node's image that are exactly [a] and exactly [b]. */
    private fun SemanticsNodeInteraction.countPixels(a: Color, b: Color): Pair<Int, Int> {
        val pixels = captureToImage().toPixelMap()
        var first = 0
        var second = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val p = pixels[x, y].copy(alpha = 1f)
            if (p == a.copy(alpha = 1f)) first++
            if (p == b.copy(alpha = 1f)) second++
        }
        return first to second
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

    private companion object {
        const val FIELD = "strands.editor.word"
        const val CHECK = "strands.editor.word.check"
        const val FIT = "strands.editor.word.fit"
        const val WARN = "Warn"

        /** The outlined box's 56dp floor with the theme's body line. */
        val ONE_BOX = 60.dp
    }
}
