package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val StepHeight = 26.dp
private val StepShape = RoundedCornerShape(8.dp)
private const val StepColorMillis = 200

/**
 * The chapter's boards as steps under the title row (prototype 03, variant A): one rounded rectangle per board, so it is
 * never mistaken for the in-a-row pips. Boards before [board] are done (a check), [board] is the current one (its number,
 * the primary-text border) and turns done the moment it is finished ([currentDone]), the rest wait. The count is [boards],
 * 4 for a technique that has no review board. One image to a screen reader: "Board 3 of 5".
 */
@Composable
internal fun BoardSteps(board: Int, boards: Int, currentDone: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(StepHeight).testTag("train.steps").clearAndSetSemantics { contentDescription = "Board $board of $boards" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (step in 1..boards) Step(step, done = step < board || (step == board && currentDone), current = step == board, Modifier.weight(1f))
    }
}

@Composable
private fun Step(number: Int, done: Boolean, current: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val spec = if (rememberReducedMotion()) snap() else tween<androidx.compose.ui.graphics.Color>(StepColorMillis)
    val fill by animateColorAsState(
        when {
            done -> scheme.primary
            current -> scheme.surface
            else -> scheme.surfaceVariant
        },
        spec,
        label = "stepFill",
    )
    val edge by animateColorAsState(
        when {
            done -> scheme.primary
            current -> LocalPrimaryText.current
            else -> scheme.surfaceVariant
        },
        spec,
        label = "stepEdge",
    )
    Box(modifier.fillMaxHeight().clip(StepShape).background(fill).border(2.dp, edge, StepShape), contentAlignment = Alignment.Center) {
        if (done) {
            Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onPrimary)
        } else {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (current) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
    }
}
