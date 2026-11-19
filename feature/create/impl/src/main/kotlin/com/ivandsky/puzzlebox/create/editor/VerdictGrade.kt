package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.ui.colors
import com.ivandsky.puzzlebox.create.ui.gradeLevel
import com.ivandsky.puzzlebox.create.ui.gradeState
import com.ivandsky.puzzlebox.create.ui.tone
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.resultui.DifficultyLabel

private val GradeIconSize = 16.dp

/** A Unique's level with its mark in the Unique tone, or the Needs guessing flag in the warn tone. */
@Composable
internal fun VerdictGrade(verdict: Verdict.Unique) {
    val (container, content) = verdict.tone().colors()
    val level = verdict.gradeLevel()
    val style = MaterialTheme.typography.labelLarge
    Surface(
        modifier = Modifier
            .testTag("create.verdict.grade")
            .semantics { stateDescription = verdict.gradeState().orEmpty() },
        shape = AppShapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (level != null) {
                DifficultyLabel(level, style, content)
            } else {
                Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(GradeIconSize))
                Text("Needs guessing", style = style)
            }
        }
    }
}
