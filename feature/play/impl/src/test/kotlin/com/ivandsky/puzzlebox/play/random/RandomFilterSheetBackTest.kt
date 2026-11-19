package com.ivandsky.puzzlebox.play.random

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** The option page inside the real `random.sheet` (epic picker-filters, ticket 06): the system Back, the Close button, and a rotation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RandomFilterSheetBackTest {
    @get:Rule
    val rule = createComposeRule()

    private val sheet = RandomFilterSheetFixture(rule)

    private fun systemBack() {
        val dialog = ShadowDialog.getLatestDialog()
        rule.runOnUiThread { dialog.onBackPressed() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(RandomFilterSheetFixture.SETTLE_MS)
    }

    @Test
    fun `the system Back closes an open page, not the sheet, and the Random content comes back`() {
        sheet.mountSheet()
        sheet.tap("random.filter.game")
        assertEquals(1, sheet.count("random.filter.game.page"))

        systemBack()

        assertEquals(0, sheet.count("random.filter.game.page"))
        assertEquals(0, sheet.dismissed)
        sheet.node("random.sheet").assertExists()
        sheet.node("random.source.past").assertIsDisplayed()
        sheet.node("random.filter.game").assertIsDisplayed()
    }

    @Test
    fun `with no page open the system Back closes the sheet as before`() {
        sheet.mountSheet()

        systemBack()

        assertEquals(1, sheet.dismissed)
    }

    @Test
    fun `an open page has no Close button of its own`() {
        sheet.mountSheet()

        sheet.tap("random.filter.game")

        sheet.node("random.filter.game.sheet.close").assertDoesNotExist()
        sheet.node("random.filter.game.page.back").assertIsDisplayed()
    }

    @Test
    fun `a rotation with a page open keeps the page`() {
        val restoration = StateRestorationTester(rule)
        sheet.mountSheet(restoration = restoration)
        sheet.tap("random.filter.game")

        restoration.emulateSavedInstanceStateRestore()
        rule.mainClock.advanceTimeBy(RandomFilterSheetFixture.SETTLE_MS)
        rule.waitForIdle()

        assertEquals(1, sheet.count("random.filter.game.page"))
    }
}
