package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Duels tab's layout on a tablet-sized window: which sections stand where, and when it falls back to one column. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelsTabLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun row(code: String, kind: DuelRowKind) = DuelRow(
        code = checkNotNull(ShareCode.parse(code)),
        opponent = Player(uid = "uid-$code", name = "Sam", photoUrl = null),
        kind = kind,
        legsDone = 1,
        theirDone = 2,
        legs = 3,
        deadline = Instant.parse("2026-11-10T12:00:00Z"),
    )

    private val move = row("YMV222", DuelRowKind.YourMove)
    private val waiting = row("WTG222", DuelRowKind.Waiting)
    private val finished = row("FNS222", DuelRowKind.Finished(2, 1))

    private fun show(rows: List<DuelRow>, wide: Boolean) {
        rule.setContent {
            PuzzleBoxTheme {
                DuelsTabContent(
                    rows = rows,
                    signedIn = true,
                    inviter = "Ivan",
                    now = { Instant.parse("2026-11-09T12:00:00Z") },
                    onNewDuel = {},
                    onOpenDuel = {},
                    onOpenReveal = {},
                    wide = wide,
                )
            }
        }
    }

    private fun left(tag: String): Float = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value

    @Test
    fun `wide puts the sections that need me left of the others`() {
        show(listOf(move, waiting, finished), wide = true)

        assertTrue(left("duels.section.yourMove") < left("duels.section.waiting"))
        assertEquals(left("duels.section.waiting"), left("duels.section.finished"), 0.5f)
    }

    @Test
    fun `wide keeps one column when only the sections that need me exist`() {
        show(listOf(move), wide = true)

        rule.onNodeWithTag("duels.section.yourMove").assertIsDisplayed()
        assertTrue("the section spans the window", rule.onNodeWithTag("duels.section.yourMove").getUnclippedBoundsInRoot().width > 1000.dp)
    }

    @Test
    fun `wide keeps one column when only the other sections exist`() {
        show(listOf(waiting, finished), wide = true)

        assertTrue("the section spans the window", rule.onNodeWithTag("duels.section.waiting").getUnclippedBoundsInRoot().width > 1000.dp)
        assertEquals(left("duels.section.waiting"), left("duels.section.finished"), 0.5f)
    }

    @Test
    fun `not wide stacks every section in one column`() {
        show(listOf(move, waiting, finished), wide = false)

        assertEquals(left("duels.section.yourMove"), left("duels.section.waiting"), 0.5f)
        assertTrue(
            rule.onNodeWithTag("duels.section.yourMove").getUnclippedBoundsInRoot().bottom <
                rule.onNodeWithTag("duels.section.waiting").getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun `a row sits in its section and an empty section is not drawn`() {
        show(listOf(move, finished), wide = false)

        rule.onAllNodesWithTag("duels.section.waiting").assertCountEquals(0)
        rule.onAllNodesWithTag("duels.section.invites").assertCountEquals(0)
        rule.onNode(hasTestTag("duel.row.YMV222") and hasAnyAncestor(hasTestTag("duels.section.yourMove"))).assertExists()
        rule.onNode(hasTestTag("duel.row.FNS222") and hasAnyAncestor(hasTestTag("duels.section.finished"))).assertExists()
    }

    @Test
    fun `the finished header shows the tally only when a score was kept`() {
        show(listOf(finished, row("FNS333", DuelRowKind.Finished(null, null))), wide = false)

        rule.onNodeWithTag("duels.finished.tally").assertIsDisplayed()
    }

    @Test
    fun `without a kept score the finished header has no tally`() {
        show(listOf(row("FNS333", DuelRowKind.Finished(null, null))), wide = false)

        rule.onAllNodesWithTag("duels.finished.tally").assertCountEquals(0)
    }
}
