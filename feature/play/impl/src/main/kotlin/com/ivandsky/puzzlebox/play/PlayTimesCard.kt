package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardList
import com.ivandsky.puzzlebox.gameui.board.DailyBoardNote
import com.ivandsky.puzzlebox.gameui.board.dailyBoardListHeight
import com.ivandsky.puzzlebox.gameui.board.toBoardNote
import com.ivandsky.puzzlebox.gameui.rankRuleText
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.SignInPrompt

/**
 * Today's times on this game's board (A15): the first five rows, the player's own row pinned under them when it
 * ranks lower, drawn by the same [DailyBoardList] as the game page's board, connections included. There is no friend
 * graph yet, so the card does not claim to be friends. The list area always reserves five rows and a pinned one, so
 * loading, notes and the rows share one height. Signed out, loading, unavailable and empty boards show a note in the
 * same area; a solved day whose own row is missing explains why under the rows. With [animated] the rows move,
 * arrive and leave with motion.
 */
@Composable
internal fun PlayTimesCard(
    times: PlayTimes,
    signIn: BoardSignIn,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    val board = times as? PlayTimes.Board
    StatsCard(modifier.testTag("play.stats.times")) {
        CardLabel(board?.order?.let(::rankRuleText) ?: "Times today", Modifier.padding(bottom = Spacing.sm))
        if (times == PlayTimes.SignedOut) {
            Box(Modifier.fillMaxWidth()) {
                SignInPrompt(title = "Sign in to see today's times", yourTimeMs = null, signIn = signIn, onSignIn = onSignIn)
            }
            return@StatsCard
        }
        Box(Modifier.fillMaxWidth().heightIn(min = dailyBoardListHeight(PlayTimes.Board.TOP, pinned = true))) {
            val note = noteFor(times)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.lg).testTag("play.stats.times.note"),
                )
            } else if (board != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A board with no rank rule reads as time-first rows.
                    DailyBoardList(
                        board.head.map { if (it is DailyBoardLine.Real) it.asYou() else it },
                        board.order ?: RankOrder.TimeThenHints,
                        pinned = board.pinned?.asYou(),
                        animated = animated,
                    )
                    ownRowNote(board)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("play.stats.times.note"),
                        )
                    }
                }
            }
        }
    }
}

private fun noteFor(times: PlayTimes): String? = when (times) {
    PlayTimes.Loading -> "Loading today's times…"
    PlayTimes.SignedOut -> null // the sign-in prompt replaces the list
    PlayTimes.Unavailable -> "Today's times aren't available right now"
    is PlayTimes.Board -> if (times.rows.isEmpty()) "No times posted yet today" else null
}

/** Why a solved day's own row is not on the board; null while it is there or the day is not solved. */
private fun ownRowNote(board: PlayTimes.Board): String? = when {
    !board.solved || board.hasOwnRow -> null
    // A7 (RG10): a day older than the game's yesterday can never post, so it is not posting.
    board.pastDay -> "Past days aren't ranked."
    // No post status read yet: the line says Posting.
    board.post == null -> DailyBoardNote.Posting.text
    else -> board.post.toBoardNote()?.text
}

// The player's own row keeps reading "You" here, as it always has.
private fun DailyBoardLine.Real.asYou(): DailyBoardLine.Real = if (row.isSelf) copy(row = row.copy(name = "You")) else this
