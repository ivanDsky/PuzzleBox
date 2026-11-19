package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The renders of [FilterSheetGoldenTest] on a tablet (the qualifier the other tablet goldens use): the single and multi sheets open and the multi page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class FilterSheetTabletGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `chips`() = composeRule.render("Tablet_FilterChips", dark = false) { ChipsScene() }

    @Test
    fun `single sheet`() = composeRule.render("Tablet_SingleFilterSheet", dark = false, target = SHEET) { SingleSheetScene() }

    @Test
    fun `single sheet in the dark theme`() = composeRule.render("Tablet_SingleFilterSheet_dark", dark = true, target = SHEET) { SingleSheetScene() }

    @Test
    fun `multi sheet`() = composeRule.render("Tablet_MultiFilterSheet", dark = false, target = SHEET) { MultiSheetScene() }

    @Test
    fun `multi sheet in the dark theme`() = composeRule.render("Tablet_MultiFilterSheet_dark", dark = true, target = SHEET) { MultiSheetScene() }

    @Test
    fun `multi page`() = composeRule.render("Tablet_MultiFilterPage", dark = false) { MultiPageScene() }

    @Test
    fun `multi page in the dark theme`() = composeRule.render("Tablet_MultiFilterPage_dark", dark = true) { MultiPageScene() }
}
