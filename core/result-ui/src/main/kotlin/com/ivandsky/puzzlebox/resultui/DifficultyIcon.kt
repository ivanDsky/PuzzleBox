package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.editor.Difficulty

private val IconSize = 16.dp

/**
 * The level's mark alone, 16dp unless [modifier] sets a size. It carries no description: the level
 * word beside it is the label. Tests find it by the tag `difficulty.<level lowercase>`.
 */
@Composable
fun DifficultyIcon(
    level: Difficulty.Level,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = level.icon,
        contentDescription = null,
        modifier = modifier.size(IconSize).testTag("difficulty.${level.name.lowercase()}"),
        tint = tint,
    )
}
