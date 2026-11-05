package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.games.strandsui.HintUi

/**
 * NYT's row under the grid: the found counter on the left, the Hint pill on the right, as wide
 * as the board. A solved board has no hint to give, so [hint] is null and the counter stands
 * alone, centred where NYT puts "View Results"; the row keeps the pill's height so the board
 * above it doesn't grow on the solve.
 */
@Composable
internal fun StrandsHintRow(
    found: Int,
    total: Int,
    hint: HintUi?,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = HintHeight).testTag("strands.hint_row"),
        horizontalArrangement = if (hint == null) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoundCounter(found = found, total = total, modifier = Modifier.weight(1f, fill = false))
        if (hint != null) StrandsHintControl(hint = hint, onHint = onHint, reduceMotion = reduceMotion)
    }
}
