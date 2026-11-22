package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.community.AuthorAvatars
import com.ivandsky.puzzlebox.designsystem.community.EntryTile

private val SearchHeight = 48.dp
private val YoursDisc = 28.dp

/**
 * The search field's stand-in under the header: it looks like the field and a tap on it ([onSearch]) opens the Search
 * page, which has the real field.
 */
@Composable
internal fun ShelvesSearchStub(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .testTag("shelves.search")
            .fillMaxWidth()
            .heightIn(min = SearchHeight)
            .clip(RoundedCornerShape(SearchHeight / 2))
            .background(scheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onSearch)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
        Text(
            text = "Search puzzles, authors, games",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The two entry tiles side by side: From friends with the puzzles friends sent, and Your puzzles with what is live and
 * what is still a draft. They read what is saved, so they hold offline.
 */
@Composable
internal fun ShelvesEntryTiles(entries: ShelvesEntries, actions: ShelvesActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EntryTile(
            title = "From friends",
            subtitle = count(entries.friendPuzzles, "puzzle"),
            onClick = actions.onFriends,
            modifier = Modifier.weight(1f).testTag("shelves.entry.friends"),
            leading = { AuthorAvatars(entries.friendInitials) },
        )
        EntryTile(
            title = "Your puzzles",
            subtitle = "${entries.published} published · ${count(entries.drafts, "draft")}",
            onClick = actions.onYours,
            modifier = Modifier.weight(1f).testTag("shelves.entry.yours"),
            leading = {
                Icon(
                    PuzzleIcons.Profile,
                    contentDescription = null,
                    modifier = Modifier.size(YoursDisc).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(5.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        )
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
