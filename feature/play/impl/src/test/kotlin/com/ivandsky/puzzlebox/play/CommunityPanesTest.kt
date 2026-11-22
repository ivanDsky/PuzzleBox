package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.selectedRow
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Community tab's tablet scaffold (M12-5, ticket 124): the tabs and their list on the left, the page of the current
 * tab's pick on the right. A row that picks through [LocalCommunityPicker] sets only its own tab's pick and pushes
 * nothing; each tab keeps its pick across a tab switch and a restore; a tab with no pick shows its default, and with
 * no default the right pane says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityPanesTest {

    @get:Rule
    val rule = createComposeRule()

    /** What the app's slot would push on the back stack: only a row with no picker around it does. */
    private val pushed = ArrayList<String>()
    private val defaults = mutableStateMapOf<CommunityTab, CommunityPick?>(
        CommunityTab.NewToday to CommunityPick.Puzzle("AAA111"),
        CommunityTab.Friends to CommunityPick.Puzzle("FFF111"),
        CommunityTab.Yours to null,
    )
    private var cleared = 0

    /** Each tab's rows; BBB222 is in New today and in Friends, to show a row is selected only in its own tab. */
    private fun rows(tab: CommunityTab) = when (tab) {
        CommunityTab.NewToday -> listOf("AAA111", "BBB222")
        CommunityTab.Friends -> listOf("FFF111", "BBB222")
        CommunityTab.Yours -> emptyList()
    }

    private fun content(): @Composable () -> Unit = {
        PuzzleBoxTheme {
            CommunityPanes(
                default = { tab -> defaults[tab] },
                list = { sized, picks ->
                    Column(sized) {
                        CommunityTab.entries.forEach { tab ->
                            Text(tab.name, Modifier.testTag("tab.${tab.name}").clickable { picks.select(tab) })
                        }
                        picks.ProvideFor(picks.tab) {
                            val picker = LocalCommunityPicker.current
                            rows(picks.tab).forEach { code ->
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
                            Text("draft", Modifier.testTag("row.draft").clickable { pushed.add("draft") })
                        }
                    }
                },
                empty = { tab -> Text("Nothing to show on ${tab.name}", Modifier.testTag("pane.empty")) },
                detail = { pick, clear ->
                    val kind = if (pick is CommunityPick.Published) "published" else "puzzle"
                    Text("$kind ${pick.code}", Modifier.testTag("pane.detail").clickable { cleared++; clear() })
                },
            )
        }
    }

    private fun detail() = rule.onNodeWithTag("pane.detail")

    private fun tab(tab: CommunityTab) = rule.onNodeWithTag("tab.${tab.name}").performClick()

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
    fun `tapping a second row crossfades the right pane in place, the list still`() {
        rule.mainClock.autoAdvance = false
        rule.setContent(content())
        rule.mainClock.advanceTimeBy(500L)
        val listLeft = rule.onNodeWithTag("row.AAA111").getBoundsInRoot().left

        rule.onNodeWithTag("row.BBB222").performClick()
        rule.mainClock.advanceTimeBy(100L)

        assertEquals(2, rule.onAllNodesWithTag("pane.detail").fetchSemanticsNodes().size)
        assertEquals(listLeft, rule.onNodeWithTag("row.AAA111").getBoundsInRoot().left)
        rule.mainClock.advanceTimeBy(500L)
        assertEquals(1, rule.onAllNodesWithTag("pane.detail").fetchSemanticsNodes().size)
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
    fun `a tab with no pick of its own shows its default and each tab has one`() {
        rule.setContent(content())

        tab(CommunityTab.Friends)
        detail().assertTextEquals("puzzle FFF111")
        rule.onNodeWithTag("row.FFF111").assertIsSelected()

        tab(CommunityTab.NewToday)
        detail().assertTextEquals("puzzle AAA111")
    }

    @Test
    fun `a pick belongs to its tab and is there again after a tab switch`() {
        rule.setContent(content())
        rule.onNodeWithTag("row.BBB222").performClick()

        tab(CommunityTab.Friends)
        detail().assertTextEquals("puzzle FFF111")
        rule.onNodeWithTag("row.BBB222").assertIsNotSelected()
        rule.onNodeWithTag("row.mine").performClick()
        detail().assertTextEquals("published MINE99")

        tab(CommunityTab.NewToday)
        detail().assertTextEquals("puzzle BBB222")
        rule.onNodeWithTag("row.BBB222").assertIsSelected()
        rule.onNodeWithTag("row.mine").assertIsNotSelected()

        tab(CommunityTab.Friends)
        detail().assertTextEquals("published MINE99")
        rule.onNodeWithTag("row.mine").assertIsSelected()
    }

    @Test
    fun `the picks and the current tab survive a restore`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent(content())
        rule.onNodeWithTag("row.BBB222").performClick()
        tab(CommunityTab.Friends)
        rule.onNodeWithTag("row.mine").performClick()

        restoration.emulateSavedInstanceStateRestore()

        detail().assertTextEquals("published MINE99")
        tab(CommunityTab.NewToday)
        detail().assertTextEquals("puzzle BBB222")
    }

    @Test
    fun `Yours with no published puzzle shows the empty pane and one pick fills it`() {
        rule.setContent(content())

        tab(CommunityTab.Yours)
        rule.onNodeWithTag("pane.empty").assertTextEquals("Nothing to show on Yours")
        detail().assertDoesNotExist()

        rule.onNodeWithTag("row.mine").performClick()
        detail().assertTextEquals("published MINE99")
    }

    @Test
    fun `a draft never fills the pane`() {
        rule.setContent(content())
        tab(CommunityTab.Yours)

        rule.onNodeWithTag("row.draft").performClick()

        assertEquals(listOf("draft"), pushed)
        rule.onNodeWithTag("pane.empty").assertTextEquals("Nothing to show on Yours")
    }

    @Test
    fun `clearing the page goes back to that tab's default pick and leaves the others`() {
        rule.setContent(content())
        rule.onNodeWithTag("row.BBB222").performClick()
        tab(CommunityTab.Friends)
        rule.onNodeWithTag("row.mine").performClick()

        detail().performClick()

        assertEquals(1, cleared)
        detail().assertTextEquals("puzzle FFF111")
        tab(CommunityTab.NewToday)
        detail().assertTextEquals("puzzle BBB222")
    }

    @Test
    fun `with nothing to select the right pane shows the empty message`() {
        defaults[CommunityTab.NewToday] = null
        rule.setContent(content())

        rule.onNodeWithTag("pane.empty").assertTextEquals("Nothing to show on NewToday")
        rule.onNodeWithTag("pane.detail").assertDoesNotExist()
    }

    @Test
    fun `a list that fills in later selects its first puzzle`() {
        defaults[CommunityTab.NewToday] = null
        rule.setContent(content())

        defaults[CommunityTab.NewToday] = CommunityPick.Puzzle("AAA111")
        rule.waitForIdle()

        detail().assertTextEquals("puzzle AAA111")
    }

    @Test
    fun `each tab's default is its own first puzzle`() {
        val phase = CommunityPhase.Loaded(listOf(CommunityRowModels.first))
        val friends = FriendPreviewCards.take(2)
        val yours = YoursSummary(count = 3, firstPublishedCode = "MINE99")

        assertEquals(CommunityPick.Puzzle(CommunityRowModels.first.code.value), defaultCommunityPick(CommunityTab.NewToday, phase, friends, yours))
        assertEquals(CommunityPick.Puzzle(friends.first().row.code.value), defaultCommunityPick(CommunityTab.Friends, phase, friends, yours))
        assertEquals(CommunityPick.Published("MINE99"), defaultCommunityPick(CommunityTab.Yours, phase, friends, yours))
        assertEquals(null, defaultCommunityPick(CommunityTab.Yours, phase, friends, YoursSummary(count = 2, firstPublishedCode = null)))
        assertEquals(null, defaultCommunityPick(CommunityTab.NewToday, CommunityPhase.Loaded(emptyList()), friends, yours))
        assertEquals(null, defaultCommunityPick(CommunityTab.Friends, phase, emptyList(), yours))
    }

    @Test
    fun `the list pane is as wide as it is told, Games' list width when it is not`() {
        rule.setContent {
            PuzzleBoxTheme {
                CommunityPanes(
                    default = { null },
                    list = { sized, _ -> Box(sized.testTag("list")) },
                    empty = { Text("none") },
                    detail = { _, _ -> },
                    listPaneWidth = ShelvesListPaneWidth,
                )
            }
        }
        rule.onNodeWithTag("list").assertWidthIsEqualTo(ShelvesListPaneWidth)
    }
}

private object CommunityRowModels {
    val first = CommunityPanesGoldenScreenshots.row("K7Q2MX", "Zip", CommunityPanesGoldenScreenshots.ZIP, "Spiral", Difficulty.Level.Hard, "5 min ago")
}
