package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.DuelRowKind
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 1509: the Duels list keeps its section under the thumb when its duels load again and a new one joins above. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp")
class DuelsTabKeepsPositionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var rows by mutableStateOf(duels(moves = 3))
    private var now by mutableStateOf(Instant.parse("2026-11-09T12:00:00Z"))

    private fun duels(moves: Int) =
        List(moves) { row("YMV%03d".format(it), DuelRowKind.YourMove) } +
            List(3) { row("WTG%03d".format(it), DuelRowKind.Waiting) } +
            List(10) { row("FNS%03d".format(it), DuelRowKind.Finished(2, 1)) }

    private fun row(code: String, kind: DuelRowKind) = DuelRow(
        code = checkNotNull(ShareCode.parse(code)),
        opponent = Player(uid = "uid-$code", name = "Sam", photoUrl = null),
        kind = kind,
        legsDone = 1,
        theirDone = 2,
        legs = 3,
        deadline = Instant.parse("2026-11-10T12:00:00Z"),
    )

    @Test
    fun `Duels keeps its Finished section where it was when the list loads again with a new move above it`() {
        rule.setContent {
            PuzzleBoxTheme {
                DuelsTabContent(
                    rows = rows,
                    signedIn = true,
                    inviter = "Ivan",
                    now = { now },
                    onNewDuel = {},
                    onOpenDuel = {},
                    onOpenReveal = {},
                )
            }
        }
        // The challenge card is item 0, then Your move, Waiting and Finished.
        rule.onNodeWithTag("duels.tab").performScrollToIndex(3)
        rule.waitForIdle()
        val before = rule.onNodeWithTag("duels.section.finished").getUnclippedBoundsInRoot().top

        rows = duels(moves = 4)
        now = now.plusSeconds(60)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)

        assertEquals(before.value, rule.onNodeWithTag("duels.section.finished").getUnclippedBoundsInRoot().top.value, 0.5f)
    }
}
