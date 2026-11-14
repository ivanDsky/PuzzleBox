package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings Visibility block: the four-step ladder, each step saying what it opens; the effective tier of [own] is
 * selected. While [OwnTier.Unknown] (signed out, or the stored tier not read yet) none is selected and the steps are
 * shown disabled: there is no tier to change yet. Stateless: it reports the tapped tier.
 */
@Composable
internal fun VisibilitySection(own: OwnTier, onSelect: (VisibilityTier) -> Unit, modifier: Modifier = Modifier) {
    val selected = own.effective
    val known = own != OwnTier.Unknown
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.visibility"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Visibility")
        Column(Modifier.selectableGroup()) {
            VisibilityTier.entries.forEach { tier ->
                VisibilityRow(tier, selected = tier == selected, enabled = known, onClick = { onSelect(tier) })
            }
        }
    }
}

@Composable
private fun VisibilityRow(tier: VisibilityTier, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("settings.visibility.${tier.storageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(text = tier.title(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = tier.opens(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun VisibilityTier.title(): String = when (this) {
    VisibilityTier.Hidden -> "Hidden"
    VisibilityTier.Board -> "On leaderboards"
    VisibilityTier.Duel -> "+ Challengeable"
    VisibilityTier.Replays -> "+ Replays watchable"
}

private fun VisibilityTier.opens(): String = when (this) {
    VisibilityTier.Hidden -> "You stay off the leaderboards and can't be challenged. Past entries stay."
    VisibilityTier.Board -> "Your results appear on the leaderboards."
    VisibilityTier.Duel -> "Other players can challenge you to a duel."
    VisibilityTier.Replays -> "Other players can watch replays of your games."
}

private val MinRowHeight = 56.dp

@PuzzleBoxPreviews
@Composable
internal fun VisibilitySectionPreview() {
    PuzzleBoxTheme { VisibilitySection(own = OwnTier.Chosen(VisibilityTier.Duel), onSelect = {}) }
}
