package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The widest the stacked result actions grow: equal-width buttons, never a full tablet pane. */
private val ActionsMaxWidth = 360.dp

/**
 * The new-record badge (tag `result.record_badge`): a tonal tag, not a button — chip
 * height and radius, a leading trophy and the tertiary (amber) pair, so it never borrows
 * the lime fill or the 48 dp height of the actions below it.
 */
@Composable
internal fun RecordTag(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.testTag("result.record_badge"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_result_record),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = "New personal record", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The primary action (tagged `result.next` or `result.done`) over "Play again", stacked
 * at one equal width capped at [ActionsMaxWidth].
 */
@Composable
internal fun ResultActions(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = ActionsMaxWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val fill = Modifier.fillMaxWidth()
        when (primary) {
            is ResultPrimaryAction.Next -> PrimaryButton(
                text = "Next: ${primary.name}",
                onClick = onPrimary,
                modifier = fill.testTag("result.next"),
            )
            ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> PrimaryButton(
                text = "Done",
                onClick = onPrimary,
                modifier = fill.testTag("result.done"),
            )
        }
        onPlayAgain?.let {
            SecondaryButton(
                text = "Play again",
                onClick = it,
                modifier = fill.testTag("result.playAgain"),
            )
        }
    }
}
