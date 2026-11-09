package com.ivandsky.puzzlebox.create.importtext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.create.importtext.ImportSheetUi.ClipboardNotation
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Import sheet's states: while a check runs the Import button is replaced by the checking
 * row, the box is read-only and a swipe does not close the sheet; each failure kind shows its
 * own `create.import.error.<kind>` line; the clipboard banner shows only for offered notation
 * and its arrival leaves the box in place. Tags and state only, never copy text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = 0
    private var pasted = 0
    private var bannerDismissed = 0
    private val offers = mutableListOf<String?>()

    /** Shows [ui] and lets the sheet slide in; the spinner never idles, so the clock is manual. */
    private fun show(ui: ImportSheetUi) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                ImportSheet(
                    ui = ui,
                    actions = ImportSheetActions(
                        onDismiss = { dismissed++ },
                        onClipboard = { offers += it },
                        onPaste = { pasted++ },
                        onDismissClipboard = { bannerDismissed++ },
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
    }

    @Composable
    private fun sheet(ui: ImportSheetUi) = ImportSheet(ui = ui, actions = ImportSheetActions(onClipboard = { offers += it }))

    /** Counts the full clip reads, the ones that show Android's paste toast. */
    private class CountingClipboard(private val delegate: Clipboard, private val reads: AtomicInteger) :
        Clipboard by delegate {
        override suspend fun getClipEntry(): ClipEntry? {
            reads.incrementAndGet()
            return delegate.getClipEntry()
        }
    }

    private fun putOnClipboard(text: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("test", text))
    }

    private val banner = ClipboardNotation(gameName = "Queens", lines = 6, text = "puzzlebox queens v1\n")

    private fun swipeSheetDown() {
        // Starts inside the sheet (not on the scrim) and ends below it: a drag, never a scrim tap.
        composeRule.onNodeWithTag("create.import.sheet").performTouchInput {
            swipeDown(startY = height / 2f, endY = height * 2f)
        }
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
    fun `the banner shows only when the clipboard holds notation`() {
        show(ImportSheetUi(visible = true))
        composeRule.onNodeWithTag("create.import.clipboard").assertDoesNotExist()
        composeRule.onNodeWithTag("create.import.clipboard.paste").assertDoesNotExist()
    }

    @Test
    fun `with notation on the clipboard the banner offers Paste and Dismiss and the box stays editable`() {
        show(ImportSheetUi(visible = true, clipboard = banner))

        composeRule.onNodeWithTag("create.import.clipboard").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.clipboard.paste").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.clipboard.dismiss").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.text").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
    }

    @Test
    fun `Paste and Dismiss on the banner call their callbacks once`() {
        show(ImportSheetUi(visible = true, clipboard = banner))

        composeRule.onNodeWithTag("create.import.clipboard.paste").performClick()
        assertEquals(1, pasted)
        assertEquals(0, bannerDismissed)

        composeRule.onNodeWithTag("create.import.clipboard.dismiss").performClick()
        assertEquals(1, bannerDismissed)
        assertEquals(1, pasted)
    }

    @Test
    fun `the banner is not drawn while a check runs`() {
        show(ImportSheetUi(visible = true, text = "x", checking = true, clipboard = banner))

        composeRule.onNodeWithTag("create.import.clipboard").assertDoesNotExist()
    }

    @Test
    fun `the banner's arrival never moves the box`() {
        var ui by mutableStateOf(ImportSheetUi(visible = true))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                ImportSheet(ui = ui, actions = ImportSheetActions())
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
        val before = composeRule.onNodeWithTag("create.import.text").fetchSemanticsNode().boundsInRoot

        ui = ui.copy(clipboard = banner)
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
        val after = composeRule.onNodeWithTag("create.import.text").fetchSemanticsNode().boundsInRoot

        assertEquals(before.top, after.top, 0.5f)
        composeRule.onNodeWithTag("create.import.clipboard").assertIsDisplayed()
    }

    @Test
    fun `opening the sheet offers the clipboard's text once`() {
        putOnClipboard("puzzlebox queens v1\n0011\n")

        show(ImportSheetUi(visible = true))
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)

        assertEquals(listOf<String?>("puzzlebox queens v1\n0011\n"), offers)
    }

    @Test
    fun `a clipboard that is not text is never read`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val image = ClipData("pic", arrayOf("image/png"), ClipData.Item(Uri.parse("content://x/1")))
        manager.setPrimaryClip(image)
        val reads = AtomicInteger()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalClipboard provides CountingClipboard(LocalClipboard.current, reads)) {
                PuzzleBoxTheme { sheet(ImportSheetUi(visible = true)) }
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)

        assertEquals(0, reads.get())
        assertEquals(listOf<String?>(null), offers)
    }

    @Test
    fun `an activity recreation while the sheet is open does not read the clipboard again`() {
        putOnClipboard("puzzlebox queens v1\n0011\n")
        val reads = AtomicInteger()
        val restoration = StateRestorationTester(composeRule)

        restoration.setContent {
            CompositionLocalProvider(LocalClipboard provides CountingClipboard(LocalClipboard.current, reads)) {
                PuzzleBoxTheme { sheet(ImportSheetUi(visible = true)) }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, reads.get())

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(1, reads.get())
        assertEquals(1, offers.size)
    }

    @Test
    fun `closing the sheet and opening it again reads the clipboard again`() {
        putOnClipboard("puzzlebox queens v1\n0011\n")
        var ui by mutableStateOf(ImportSheetUi(visible = true))
        composeRule.setContent { PuzzleBoxTheme { sheet(ui) } }
        composeRule.waitForIdle()
        assertEquals(1, offers.size)

        composeRule.runOnUiThread { ui = ImportSheetUi(visible = false) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { ui = ImportSheetUi(visible = true) }
        composeRule.waitForIdle()

        assertEquals(2, offers.size)
    }

    @Test
    fun `the banner says line for one line and lines otherwise`() {
        assertFalse(clipboardBannerText(banner.copy(lines = 1)).contains("lines"))
        assertTrue(clipboardBannerText(banner.copy(lines = 6)).contains("lines"))
    }

    @Test
    fun `a hidden sheet reads nothing from the clipboard`() {
        putOnClipboard("puzzlebox queens v1\n")

        show(ImportSheetUi(visible = false))
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)

        assertTrue(offers.isEmpty())
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
                ImportSheet(ui = ImportSheetUi(visible = true, error = shown), actions = ImportSheetActions())
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
