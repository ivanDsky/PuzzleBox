package com.ivandsky.puzzlebox.create.importtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Import sheet's states: while a check runs the Import button is replaced by the checking
 * row, the box is read-only and a swipe does not close the sheet; each failure kind shows its
 * own `create.import.error.<kind>` line. Tags and state only, never copy text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = 0

    /** Shows [ui] and lets the sheet slide in; the spinner never idles, so the clock is manual. */
    private fun show(ui: ImportSheetUi) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                ImportSheet(ui = ui, onEdit = {}, onSubmit = {}, onDismiss = { dismissed++ })
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
    }

    private fun swipeSheetDown() {
        composeRule.onNodeWithTag("create.import.sheet").performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
    }

    private val notEditable = SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)

    @Test
    fun `an idle box with text offers Import and is editable`() {
        show(ImportSheetUi(visible = true, text = "x"))

        composeRule.onNodeWithTag("create.import.submit").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.checking").assertDoesNotExist()
        composeRule.onNodeWithTag("create.import.text").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
    }

    @Test
    fun `while checking the Import button gives way to the checking row and the box is read-only`() {
        show(ImportSheetUi(visible = true, text = "x", checking = true))

        composeRule.onNodeWithTag("create.import.checking").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.submit").assertDoesNotExist()
        composeRule.onNodeWithTag("create.import.text").assert(notEditable)
    }

    @Test
    fun `a swipe closes an idle sheet`() {
        show(ImportSheetUi(visible = true, text = "x"))

        swipeSheetDown()

        assertTrue(dismissed > 0)
    }

    @Test
    fun `a swipe does not close the sheet while checking`() {
        show(ImportSheetUi(visible = true, text = "x", checking = true))

        swipeSheetDown()

        assertEquals(0, dismissed)
    }

    @Test
    fun `each failure kind renders its own non-empty line`() {
        val kinds = mapOf(
            "malformed" to ImportResult.Malformed(line = 3, col = 2, message = "expected 5 rows"),
            "header" to headerFailure(""),
            "unknownGame" to ImportResult.UnknownGame("chess"),
            "unsupportedVersion" to ImportResult.UnsupportedVersion(found = 9, maxKnown = 1),
            "tooLarge" to ImportResult.TooLarge(bytes = 70_000, line = 12, col = 1),
        )
        var shown by mutableStateOf<ImportResult?>(null)
        composeRule.setContent {
            PuzzleBoxTheme {
                ImportSheet(ui = ImportSheetUi(visible = true, error = shown), onEdit = {}, onSubmit = {}, onDismiss = {})
            }
        }
        kinds.forEach { (kind, error) ->
            shown = error
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("create.import.error.$kind").assertIsDisplayed().assert(hasText())
            kinds.keys.filter { it != kind }.forEach { other ->
                composeRule.onNodeWithTag("create.import.error.$other").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a garbled header gets the header line, not the generic malformed one`() {
        val error = headerFailure("puzzlebox queens\n00111\n")
        show(ImportSheetUi(visible = true, error = error))

        composeRule.onNodeWithTag("create.import.error.header").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.error.malformed").assertDoesNotExist()
    }

    private fun hasText() = SemanticsMatcher("has non-empty text") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty().isNotBlank()
    }

    /** What the real parser says for [text] with no readable header, as the sheet receives it. */
    private fun headerFailure(text: String): ImportResult {
        val parse = NotationText.parse(text, GameRegistry(emptyList()))
        require(parse is NotationParse.Malformed) { "expected a header failure, got $parse" }
        return ImportResult.Malformed(parse.line, parse.col, parse.message)
    }

    private companion object {
        const val SHEET_SETTLE_MS = 1_000L
    }
}
