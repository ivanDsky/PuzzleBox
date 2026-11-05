package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games hub's list (tag `route.hub`): one card per playable game (`hub.row.<gameId>`),
 * in the order given, each ending in a chevron. A game with a save for today swaps its
 * subtitle for an "In progress" tonal tag (A13). Stateless; picking a card is [onPick].
 */
@Composable
internal fun GamePicker(
    rows: List<HubRow>,
    onPick: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Home's card pairing (HomeScreen): cards float ABOVE the shell's ground in both
    // themes — white on cool paper in light, a lighter slate on near-black in dark.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow
    Column(
        modifier
            .fillMaxWidth()
            .testTag("route.hub")
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Pick a game",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { (game, name, inProgress) ->
            Surface(
                // Surface's own onClick clips the ripple to the card's rounded shape.
                onClick = { onPick(game) },
                shape = MaterialTheme.shapes.large,
                color = cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub.row.${game.value}")
                    // §9 touch floors: hub rows clear 48 dp with the padded content.
                    .heightIn(min = 56.dp),
            ) {
                Row(
                    // The chevron's 24 dp box carries its own optical inset: a tighter end pad.
                    Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.md,
                        top = Spacing.md,
                        bottom = Spacing.md,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (inProgress) {
                            InProgressTag()
                        } else {
                            Text(
                                text = "Today's daily puzzle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Decorative: the whole card is the click target and reads its name.
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The A13 hub cue: a small tonal tag on the secondary pair, not another subtitle line. */
@Composable
private fun InProgressTag() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = "In progress",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
