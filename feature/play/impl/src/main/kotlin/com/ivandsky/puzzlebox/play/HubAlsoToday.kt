package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.GameTile
import com.ivandsky.puzzlebox.model.GameId

/** The tile grid's width in tiles, on a phone and in the two-pane side column alike. */
private const val GRID_COLUMNS = 2

/**
 * Today's other unsolved games as a two-column grid of tiles in the order given (hub-card
 * §Tile grid), each in its [slot]; tapping one is [onPick] (an offline tile's tap is its
 * retry). Headed "Still to play" when no game is up next ([stillToPlay]), else "Also
 * today"; with no games it keeps one line saying so.
 */
@Composable
internal fun HubAlsoToday(
    games: List<GameCardModel>,
    stillToPlay: Boolean,
    slot: HubSlot,
    onPick: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (stillToPlay) {
            HubSectionLabel("STILL TO PLAY", Modifier.testTag("hub.label.still_to_play"))
        } else {
            HubSectionLabel("ALSO TODAY", Modifier.testTag("hub.label.also_today"))
        }
        if (games.isEmpty()) {
            HubEmptyLine("Nothing else left today", Modifier.testTag("hub.empty.also_today"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                games.chunked(GRID_COLUMNS).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        line.forEach { model ->
                            slot(model.game, Modifier.weight(1f)) { GameTile(model, onClick = { onPick(model.game) }) }
                        }
                        // A short last line keeps its tile at grid width.
                        repeat(GRID_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
