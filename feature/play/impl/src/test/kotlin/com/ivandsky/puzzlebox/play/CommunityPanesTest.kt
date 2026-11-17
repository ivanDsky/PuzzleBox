package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.selectedRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Community tab's tablet scaffold (M10-1 follow-up): the list on the left, the selected puzzle's page on the right.
 * A row that picks through [LocalCommunityPicker] updates the right pane and pushes nothing; a pick survives a restore;
 * with nothing to select the right pane says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityPanesTest {

    @get:Rule
    val rule = createComposeRule()

    /** What the app's slot would push on the back stack: only a row with no picker around it does. */
    private val pushed = ArrayList<String>()
    private var default by mutableStateOf<CommunityPick?>(CommunityPick.Puzzle("AAA111"))
    private var cleared = 0

    private fun content(): @Composable () -> Unit = {
        PuzzleBoxTheme {
            CommunityPanes(
                default = default,
                list = { sized ->
                    val picker = LocalCommunityPicker.current
                    Column(sized) {
                        listOf("AAA111", "BBB222").forEach { code ->
                            Text(
                                "row $code",
                                Modifier.testTag("row.$code").selectedRow(LocalSelectedRowCode.current == code)
                                    .clickable { picker?.puzzle(code) ?: pushed.add(code) },
                            )
                        }
                        Text(
                            "mine",
                            Modifier.testTag("row.mine").selectedRow(LocalSelectedRowCode.current == "MINE99")
                                .clickable { picker?.published("MINE99") ?: pushed.add("MINE99") },
                        )
                    }
                },
                empty = { Text("Nothing to show", Modifier.testTag("pane.empty")) },
                detail = { pick, clear ->
                    val kind = if (pick is CommunityPick.Published) "published" else "puzzle"
                    Text("$kind ${pick.code}", Modifier.testTag("pane.detail").clickable { cleared++; clear() })
                },
            )
        }
    }

    private fun detail() = rule.onNodeWithTag("pane.detail")

    @Test
    fun `the first puzzle is selected by default and its page shows on the right`() {
        rule.setContent(content())

        detail().assertTextEquals("puzzle AAA111")
    }

    @Test
    fun `tapping a second row changes the right pane and pushes nothing`() {
        rule.setContent(content())

        rule.onNodeWithTag("row.BBB222").performClick()

        detail().assertTextEquals("puzzle BBB222")
        assertEquals(emptyList<String>(), pushed)
    }

    @Test
    fun `one of your published puzzles opens in the right pane too`() {
        rule.setContent(content())

        rule.onNodeWithTag("row.mine").performClick()

        detail().assertTextEquals("published MINE99")
        assertEquals(emptyList<String>(), pushed)
    }

    @Test
    fun `the picked row is selected, whether it is a community puzzle or one of yours`() {
        rule.setContent(content())
        rule.onNodeWithTag("row.AAA111").assertIsSelected()
        rule.onNodeWithTag("row.BBB222").assertIsNotSelected()
        rule.onNodeWithTag("row.mine").assertIsNotSelected()

        rule.onNodeWithTag("row.mine").performClick()

        rule.onNodeWithTag("row.mine").assertIsSelected()
        rule.onNodeWithTag("row.AAA111").assertIsNotSelected()
    }

    @Test
    fun `the pick survives a restore`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent(content())
        rule.onNodeWithTag("row.BBB222").performClick()

        restoration.emulateSavedInstanceStateRestore()

        detail().assertTextEquals("puzzle BBB222")
    }

    @Test
    fun `clearing the page goes back to the default pick`() {
        rule.setContent(content())
        rule.onNodeWithTag("row.BBB222").performClick()

        detail().performClick()

        assertEquals(1, cleared)
        detail().assertTextEquals("puzzle AAA111")
    }

    @Test
    fun `with nothing to select the right pane shows the empty message`() {
        default = null
        rule.setContent(content())

        rule.onNodeWithTag("pane.empty").assertTextEquals("Nothing to show")
        rule.onNodeWithTag("pane.detail").assertDoesNotExist()
    }

    @Test
    fun `a list that fills in later selects its first puzzle`() {
        default = null
        rule.setContent(content())

        default = CommunityPick.Puzzle("AAA111")
        rule.waitForIdle()

        detail().assertTextEquals("puzzle AAA111")
    }
}
