package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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

/** The Duels tab's layout: one column of sections under the challenge card, whatever the window. */
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

    private fun show(rows: List<DuelRow>) {
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
                )
            }
        }
    }

    private fun left(tag: String): Float = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `every section and the challenge card take the width the host gives, in one column`() {
        show(listOf(move, waiting, finished))

        val card = bounds("duels.challenge")
        listOf("duels.section.yourMove", "duels.section.waiting", "duels.section.finished").forEach {
            assertEquals(card.left.value, left(it), 0.5f)
            assertEquals(card.width.value, bounds(it).width.value, 0.5f)
        }
        assertTrue(bounds("duels.section.yourMove").bottom < bounds("duels.section.waiting").top)
        assertTrue(bounds("duels.section.waiting").bottom < bounds("duels.section.finished").top)
    }

    @Test
    fun `a row sits in its section and an empty section is not drawn`() {
        show(listOf(move, finished))

        rule.onAllNodesWithTag("duels.section.waiting").assertCountEquals(0)
        rule.onAllNodesWithTag("duels.section.invites").assertCountEquals(0)
        rule.onNode(hasTestTag("duel.row.YMV222") and hasAnyAncestor(hasTestTag("duels.section.yourMove"))).assertExists()
        rule.onNode(hasTestTag("duel.row.FNS222") and hasAnyAncestor(hasTestTag("duels.section.finished"))).assertExists()
    }

    @Test
    fun `the finished header shows the tally only when a score was kept`() {
        show(listOf(finished, row("FNS333", DuelRowKind.Finished(null, null))))

        rule.onNodeWithTag("duels.finished.tally").assertIsDisplayed()
    }

    @Test
    fun `without a kept score the finished header has no tally`() {
        show(listOf(row("FNS333", DuelRowKind.Finished(null, null))))

        rule.onAllNodesWithTag("duels.finished.tally").assertCountEquals(0)
    }
}
