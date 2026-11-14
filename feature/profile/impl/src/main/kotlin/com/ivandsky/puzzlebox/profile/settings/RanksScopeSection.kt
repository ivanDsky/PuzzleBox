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
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings Ranks block: which board the hub opens on, Friends or Global ([friends] selects Friends). It is the
 * default every landing uses (the tab, a game's rank row, Result's chip, Today's rank row); a pick made on the hub
 * itself is a session choice and does not change it. Stateless: it reports the tapped default.
 */
@Composable
internal fun RanksScopeSection(friends: Boolean, onSelect: (friends: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.ranks"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Ranks")
        Column(Modifier.selectableGroup()) {
            ScopeRow("Friends", "Ranks open on you and the people you've played.", selected = friends, tag = "settings.ranks.friends") { onSelect(true) }
            ScopeRow("Global", "Ranks open on everyone.", selected = !friends, tag = "settings.ranks.global") { onSelect(false) }
        }
    }
}

@Composable
private fun ScopeRow(title: String, detail: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val MinRowHeight = 56.dp

@PuzzleBoxPreviews
@Composable
internal fun RanksScopeSectionPreview() {
    PuzzleBoxTheme { RanksScopeSection(friends = true, onSelect = {}) }
}
