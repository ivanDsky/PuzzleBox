package com.ivandsky.puzzlebox.play.community.shelves.yours

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The Your puzzles page: back and the title over [yours], the app's own list (Continue with the drafts first, then
 * Published by you) that the old Yours tab shows, so a tap on a draft or a published puzzle does what it did there.
 * [bottomPadding] is left clear under the list (the slot takes no padding of its own, so the list ends above it). It has no filter bar. With no such list ([yours] null: the host has none) the page says there is nothing to show.
 */
@Composable
internal fun YourPuzzlesPage(
    yours: (@Composable (LazyListState) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize().testTag("yours.page")) {
        CompactTopBar(
            title = "Your puzzles",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("yours.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPadding)) {
            if (yours != null) {
                yours(listState)
            } else {
                EmptyState("No puzzles of yours here", "Puzzles you make and publish show up here.", Modifier.testTag("yours.empty"))
            }
        }
    }
}
