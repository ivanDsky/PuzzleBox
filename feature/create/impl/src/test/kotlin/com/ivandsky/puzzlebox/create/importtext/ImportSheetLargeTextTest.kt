package com.ivandsky.puzzlebox.create.importtext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ImportResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A big paste fills the box, not the sheet: the box scrolls inside, so the Import button and
 * the error line under it stay on screen, on a phone and on a landscape tablet. Tags and
 * state only.
 */
@RunWith(RobolectricTestRunner::class)
class ImportSheetLargeTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
    fun `a 400 line paste keeps the button and the error line on screen on a phone`() = keepsOnScreen()

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `a 400 line paste keeps the button and the error line on screen on a tablet`() = keepsOnScreen()

    private fun keepsOnScreen() {
        val text = "puzzlebox queens v1\n" + (1..LINES).joinToString("\n") { "00001" }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                ImportSheet(
                    ui = ImportSheetUi(
                        visible = true,
                        text = text,
                        error = ImportResult.Malformed(line = 3, col = 2, message = "unexpected character"),
                    ),
                    actions = ImportSheetActions(),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
        composeRule.onNodeWithTag("create.import.submit").assertIsDisplayed()
        composeRule.onNodeWithTag("create.import.error.malformed").assertIsDisplayed()
    }

    private companion object {
        const val LINES = 400
        const val SHEET_SETTLE_MS = 1_000L
    }
}
