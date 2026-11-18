package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strandsui.HintUi

/**
 * The row under the grid: the found counter and the Hint pill, as wide as the board. A solved
 * board has no hint to give, so [hint] is null and the counter stands alone, centred where NYT
 * puts "View Results"; the row keeps the pill's height so the board above it doesn't grow on the
 * solve. [placement] says where the pieces sit (see [HintRowPlacement]).
 */
@Composable
internal fun StrandsHintRow(
    found: Int,
    total: Int,
    hint: HintUi?,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    placement: HintRowPlacement,
) {
    val tagged = modifier.heightIn(min = HintHeight).testTag("strands.hint_row")
    when (placement) {
        HintRowPlacement.Slots -> Row(
            modifier = tagged,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hint == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FoundCounter(found, total) }
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { FoundCounter(found, total) }
                StrandsHintControl(hint, onHint, reduceMotion, Modifier.weight(1f), fill = true)
            }
        }
        HintRowPlacement.Column -> Column(
            modifier = tagged,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FoundCounter(found, total)
            if (hint != null) StrandsHintControl(hint, onHint, reduceMotion, Modifier.fillMaxWidth(), fill = true)
        }
    }
}
