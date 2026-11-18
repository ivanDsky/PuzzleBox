package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle

/** "Past puzzles | New today": [source] is the one drawn chosen; [enabled] false greys both segments. */
@Composable
internal fun RandomSourceToggle(
    source: RandomSource,
    enabled: Boolean,
    onSource: (RandomSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedToggle(
        options = RandomSource.entries,
        selected = source,
        onSelect = onSource,
        label = { if (it == RandomSource.Past) "Past puzzles" else "New today" },
        tag = { if (it == RandomSource.Past) "random.source.past" else "random.source.community" },
        modifier = modifier,
        enabled = enabled,
    )
}
