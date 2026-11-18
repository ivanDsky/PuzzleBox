package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val StepSize = 24.dp

private const val COUNTDOWN_STEP = "The timer counts down to past you’s time."

private val RuleSteps = listOf(
    "Hints count against you.",
    "Past you’s time stays hidden until the results.",
)

/** The numbered rules: the countdown one only while the timer shows [timeLeftShown], the rest renumbered. */
internal fun calloutSteps(timeLeftShown: Boolean): List<String> =
    if (timeLeftShown) listOf(COUNTDOWN_STEP) + RuleSteps else RuleSteps

/** A self duel's note on the selected puzzle: DONE once played, else what is in it, the rules of racing past you ([timeLeftShown]: the timer counts down). */
@Composable
internal fun Callout(done: Boolean, timeLeftShown: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
            .padding(Spacing.md)
            .testTag("duel.legs.callout"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = if (done) "DONE" else "IN THIS PUZZLE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done) {
            Text(
                text = "You played this one. Past you’s time shows in the results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            calloutSteps(timeLeftShown).forEachIndexed { index, step -> CalloutStep(index + 1, step) }
        }
    }
}

@Composable
internal fun CalloutStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            modifier = Modifier.size(StepSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

