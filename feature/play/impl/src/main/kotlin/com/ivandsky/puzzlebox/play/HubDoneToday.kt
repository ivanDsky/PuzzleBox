package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.DoneChip
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId

/**
 * Today's solved games as wrapping chips in the order given (hub-card §Done-today area),
 * each in its [slot]; tapping one is [onPick]. With none solved it keeps one line saying so.
 */
@Composable
internal fun HubDoneToday(
    games: List<GameCardModel>,
    slot: HubSlot,
    onPick: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HubSectionLabel("DONE TODAY", Modifier.testTag("hub.label.done_today"))
        if (games.isEmpty()) {
            HubEmptyLine("Nothing solved yet today", Modifier.testTag("hub.empty.done_today"))
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                games.forEach { model ->
                    slot(model.game, Modifier) { DoneChip(model, onClick = { onPick(model.game) }) }
                }
            }
        }
    }
}
