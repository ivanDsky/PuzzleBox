package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The game and day the "Play this day" button opens: the same destination Your solve's Play again has. */
internal data class PlayThisDay(val game: GameId, val day: LocalDate)

/**
 * What the hub offers to play for the shown [board], [window] and [day], or null for none. Only a game's Day board
 * has one day to play (Overall has no single game, a week or month has no single puzzle), only while the viewer has
 * not [solved] it, and only inside [archive], the days the game can open by date; a game with no archive serves
 * [today] alone.
 */
internal fun playThisDay(board: RanksBoard, window: RanksSelection.Window, day: LocalDate, solved: Boolean, archive: ClosedRange<LocalDate>?, today: LocalDate): PlayThisDay? {
    if (board !is RanksBoard.Game || window != RanksSelection.Window.Day || solved) return null
    val openable = archive ?: (today..today)
    return PlayThisDay(board.id, day).takeIf { day in openable }
}

/** The button that takes the place of Your solve on a day the viewer has not played: [onPlay] opens [target], in the game's [accent]. */
@Composable
internal fun PlayThisDayRow(target: PlayThisDay, accent: AccentSlot, onPlay: (GameId, LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = accent.colors()
    FilledTonalButton(
        onClick = { onPlay(target.game, target.day) },
        modifier = modifier.padding(bottom = Spacing.md).fillMaxWidth().heightIn(min = RanksMetrics.TitleMinHeight).testTag("ranks.playthisday"),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.container, contentColor = colors.onContainer),
    ) { Text("Play this day") }
}
