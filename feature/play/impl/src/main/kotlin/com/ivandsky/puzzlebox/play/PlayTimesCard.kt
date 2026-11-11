package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardNote
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRow
import com.ivandsky.puzzlebox.gameui.board.GhostRow
import com.ivandsky.puzzlebox.gameui.board.toBoardNote
import com.ivandsky.puzzlebox.gameui.rankRuleText
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.SignInPrompt

private enum class TimesTab(val label: String, val tag: String) {
    Top("Top", "play.stats.tab.top"),
    NearYou("Near you", "play.stats.tab.near"),
}

// The leaderboard's rows at their natural height: a name line, and under a connection's name a detail line too.
private val RowHeight = 40.dp
private val GhostRowHeight = 56.dp

// Top is five rows plus the player's pinned one, the tallest list either tab draws; the
// list area always reserves it, so loading, notes and either tab share one height.
private const val LIST_ROWS = 6

/**
 * Today's times on this game's board (A15): Top (the first five, the player's own row
 * pinned under them when it ranks lower) or Near you (two ranks either side of the
 * player). The rows are the leaderboard's own, connections included. There is no friend
 * graph yet, so neither tab claims to be friends. Signed out, loading, unavailable and
 * empty boards show a note in the same reserved area.
 */
@Composable
internal fun PlayTimesCard(
    times: PlayTimes,
    signIn: BoardSignIn,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TimesTab.Top) }
    val board = times as? PlayTimes.Board
    StatsCard(modifier.testTag("play.stats.times")) {
        CardLabel(board?.order?.let(::rankRuleText) ?: "Times today", Modifier.padding(bottom = Spacing.sm))
        SegmentedToggle(
            options = TimesTab.entries,
            selected = tab,
            onSelect = { tab = it },
            label = { it.label },
            tag = { it.tag },
            modifier = Modifier.fillMaxWidth(),
            enabled = board != null,
        )
        if (times == PlayTimes.SignedOut) {
            Box(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                SignInPrompt(title = "Sign in to see today's times", yourTimeMs = null, signIn = signIn, onSignIn = onSignIn)
            }
            return@StatsCard
        }
        val rowHeight = if (board?.rows?.any { it is DailyBoardLine.Ghost } == true) GhostRowHeight else RowHeight
        Box(Modifier.fillMaxWidth().padding(top = Spacing.sm).heightIn(min = rowHeight * LIST_ROWS)) {
            val rows = board?.let { if (tab == TimesTab.Top) it.top else it.nearYou }
            val note = noteFor(times, rows)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.lg).testTag("play.stats.times.note"),
                )
            } else if (rows != null) {
                // A board with no rank rule reads as time-first rows.
                TimeLines(rows, board.order ?: RankOrder.TimeThenHints)
            }
        }
    }
}

private fun noteFor(times: PlayTimes, rows: List<DailyBoardLine>?): String? = when (times) {
    PlayTimes.Loading -> "Loading today's times…"
    PlayTimes.SignedOut -> null // the sign-in prompt replaces the list
    PlayTimes.Unavailable -> "Today's times aren't available right now"
    is PlayTimes.Board -> when {
        times.rows.isEmpty() -> "No times posted yet today"
        rows == null -> when {
            !times.solved -> "Finish to see who's near you"
            // A7 (RG10): a day older than the game's yesterday can never post, so it is not posting.
            times.pastDay -> "Past days aren't ranked."
            else -> when (times.post) {
                // No post status read yet: the line says Posting.
                null -> DailyBoardNote.Posting.text
                else -> times.post.toBoardNote()?.text
            }
        }
        else -> null
    }
}

@Composable
private fun TimeLines(lines: List<DailyBoardLine>, order: RankOrder) {
    Column {
        lines.forEach { line ->
            when (line) {
                // The player's own row keeps reading "You" here, as it always has.
                is DailyBoardLine.Real -> DailyBoardRow(
                    if (line.row.isSelf) line.row.copy(name = "You") else line.row,
                    order,
                    place = line.place,
                )
                is DailyBoardLine.Ghost -> GhostRow(line.row, line.place, order)
            }
        }
    }
}
