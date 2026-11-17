package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale

private val PlayIcon = 16.dp
private val MinHeight = 48.dp

/** A self duel's compare action: the friend row's text button with a play mark, since it replays both solves. */
@Composable
internal fun WatchCompareAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight).pressScale(interactionSource, reduced = false),
        shape = PillShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalPrimaryText.current),
        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.sm),
    ) {
        Icon(PuzzleIcons.WatchAgain, contentDescription = null, modifier = Modifier.size(PlayIcon))
        Spacer(Modifier.size(Spacing.xs))
        Text(WatchCompareLabel)
    }
}
