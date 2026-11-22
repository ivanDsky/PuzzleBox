package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * A See all page: back and the scope's title, the search field (focused on the Search page), the filter bar, then the
 * count and the puzzles, with [bottomPadding] left clear under the last one, scrolled by [listState]. Stateless over [state]; [actions] says what a tap or a keystroke asks for.
 */
@Composable
internal fun SeeAllScreen(
    state: SeeAllUiState,
    actions: SeeAllActions,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier.fillMaxSize().testTag("seeall.page")) {
        CompactTopBar(
            title = { PageTitle(state.scope.title(state.games)) },
            navigationIcon = {
                IconButton(onClick = actions.onBack, modifier = Modifier.testTag("seeall.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        SeeAllSearchField(
            query = state.query,
            onQuery = actions.onQuery,
            focus = state.scope == SeeAllScope.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        SeeAllFilterBar(state, actions, Modifier.padding(top = Spacing.xs))
        LazyColumn(Modifier.fillMaxSize().testTag("seeall.list"), state = listState, contentPadding = PaddingValues(bottom = bottomPadding)) { seeAllBody(state, actions) }
    }
}

@Composable
private fun RowScope.PageTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.weight(1f).testTag("seeall.title"),
    )
}
