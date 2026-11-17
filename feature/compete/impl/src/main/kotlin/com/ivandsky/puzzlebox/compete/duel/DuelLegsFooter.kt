package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

internal val MinTouch = 48.dp

/**
 * What follows the puzzle list: for a duel with someone, Waiting on them (Nudge) or the results; for a self duel the
 * results card once there is something to see, then Remove duel ([SelfFooter]).
 */
@Composable
internal fun LegsFooter(content: LegsContent) {
    val model = content.model
    val name = model.opponent.name
    if (model.self) {
        SelfFooter(content)
        return
    }
    when (model.footer) {
        DuelFooter.Playing -> Unit
        DuelFooter.Waiting -> FooterCard("Waiting on $name") {
            SecondaryButton(
                text = "Nudge",
                onClick = { content.actions.onNudge(nudgeText(name, content.code, content.deadline, content.now)) },
                modifier = Modifier.testTag("duel.legs.nudge"),
            )
        }
        DuelFooter.RevealReady -> FooterCard(if (isClosed(content.closesAt, content.now)) "Closed" else "Both finished") {
            PrimaryButton("See results", onClick = content.actions.onReveal, modifier = Modifier.testTag("duel.legs.reveal"))
        }
    }
}

/** The results card once there is something to see, then Remove duel. */
@Composable
internal fun SelfFooter(content: LegsContent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (content.model.footer == DuelFooter.RevealReady) {
            FooterCard("You played all ${content.model.rows.size}") {
                PrimaryButton("See results", onClick = content.actions.onReveal, modifier = Modifier.testTag("duel.legs.reveal"))
            }
        }
        TextButton(
            onClick = content.actions.onRemove,
            modifier = Modifier.heightIn(min = MinTouch).testTag("duel.legs.remove"),
        ) {
            Text("Remove duel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun FooterCard(title: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
            .padding(Spacing.md)
            .testTag("duel.legs.footer"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}
