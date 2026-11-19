package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import kotlinx.coroutines.launch

/**
 * The Defaults block of the tune sheet: the scope the hub opens on (Friends or Global), with [gamesFilter] (GamesFilter)
 * the games it opens on (All games or LinkedIn games), and the player's visibility tier, all stored values. A pick writes
 * through [defaults] and the sheet stays open; it does not change what is shown now, that is Who, When and Games. While the
 * tier is [OwnTier.Unknown] none is selected and the steps are disabled.
 */
@Composable
internal fun TuneDefaultsBlock(defaults: RanksTuneDefaults, gamesFilter: Boolean, modifier: Modifier = Modifier) {
    val friends by defaults.friendsDefault.collectAsStateWithLifecycle(initialValue = true)
    val linkedIn by defaults.linkedInDefault.collectAsStateWithLifecycle(initialValue = false)
    val own by defaults.ownTier.collectAsStateWithLifecycle(initialValue = OwnTier.Unknown)
    val writer = rememberCoroutineScope()
    TuneDefaults(
        friendsDefault = friends,
        gamesDefault = if (!gamesFilter) null else if (linkedIn) Games.LinkedIn else Games.All,
        own = own,
        onScope = { writer.launch { defaults.onSelectFriendsDefault(it == RankScope.Friends) } },
        onGames = { writer.launch { defaults.onSelectLinkedInDefault(it == Games.LinkedIn) } },
        onTier = { writer.launch { defaults.onSelectTier(it) } },
        modifier = modifier,
    )
}

@Composable
private fun TuneDefaults(
    friendsDefault: Boolean,
    gamesDefault: Games?,
    own: OwnTier,
    onScope: (RankScope) -> Unit,
    onGames: (Games) -> Unit,
    onTier: (VisibilityTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().testTag("ranks.tune.defaults"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "Defaults",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text("Opens on", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedToggle(
            options = RankScope.entries,
            selected = if (friendsDefault) RankScope.Friends else RankScope.Global,
            onSelect = onScope,
            label = { it.name },
            tag = { "ranks.tune.defaults.scope.${it.name}" },
            modifier = Modifier.fillMaxWidth(),
        )
        if (gamesDefault != null) {
            Text("Games", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedToggle(
                options = Games.entries,
                selected = gamesDefault,
                onSelect = onGames,
                label = { it.label },
                tag = { "ranks.tune.defaults.games.${it.name}" },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Who can see you", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TierChoices(selected = own.effective, enabled = own != OwnTier.Unknown, onSelect = onTier)
    }
}

@Composable
private fun TierChoices(selected: VisibilityTier?, enabled: Boolean, onSelect: (VisibilityTier) -> Unit) {
    Column {
        Column(Modifier.selectableGroup()) {
            VisibilityTier.entries.forEach { tier ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinRowHeight)
                        .selectable(selected = tier == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(tier) })
                        .testTag("ranks.tune.defaults.tier.${tier.storageName}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    RadioButton(selected = tier == selected, onClick = null, enabled = enabled)
                    Text(tier.title(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        selected?.let {
            Text(it.opens(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun VisibilityTier.title(): String = when (this) {
    VisibilityTier.Hidden -> "Hidden"
    VisibilityTier.Board -> "On Ranks"
    VisibilityTier.Duel -> "+ Challengeable"
    VisibilityTier.Replays -> "+ Replays watchable"
}

private fun VisibilityTier.opens(): String = when (this) {
    VisibilityTier.Hidden -> "You stay off Ranks and can't be challenged. Past entries stay."
    VisibilityTier.Board -> "Your results appear in Ranks."
    VisibilityTier.Duel -> "Other players can challenge you to a duel."
    VisibilityTier.Replays -> "Other players can watch replays of your games."
}

private val MinRowHeight = 48.dp

@PuzzleBoxPreviews
@Composable
private fun TuneDefaultsPreview() {
    RanksPreviewHost {
        TuneDefaultsBlock(previewTuneDefaults(), gamesFilter = true)
    }
}
