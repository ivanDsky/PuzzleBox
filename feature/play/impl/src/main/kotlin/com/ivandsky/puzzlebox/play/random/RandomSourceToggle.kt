package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.ChoiceRows
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Sparkle

private fun label(source: RandomSource): String = if (source == RandomSource.Past) "Past puzzles" else "New today"

private fun tag(source: RandomSource): String = if (source == RandomSource.Past) "random.source.past" else "random.source.community"

private fun description(source: RandomSource): String =
    if (source == RandomSource.Past) "Ones you can still play from the archive" else "Shared by other people today"

/**
 * "Past puzzles | New today": [source] is the one drawn chosen; [enabled] false greys both. The choice is [ChoiceRows]
 * (a line under each, the chosen one reading "Picking a puzzle…" while [enabled] is false, the error ring while [failed]).
 */
@Composable
internal fun RandomSourceToggle(
    source: RandomSource,
    enabled: Boolean,
    onSource: (RandomSource) -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    val history = PuzzleIcons.History
    val sparkle = PuzzleIcons.Sparkle
    ChoiceRows(
        options = RandomSource.entries,
        selected = source,
        onSelect = onSource,
        label = ::label,
        description = { if (!enabled && it == source) "Picking a puzzle…" else description(it) },
        tag = ::tag,
        modifier = modifier,
        icon = { if (it == RandomSource.Past) history else sparkle },
        enabled = enabled,
        error = failed,
    )
}
