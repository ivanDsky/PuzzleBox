package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.mainTabBarBleed

/**
 * The Community tab's shelves home (epic community-browse), behind [com.ivandsky.puzzlebox.play.flags.PlayFlag.CommunityShelves]:
 * the head (Community, Enter code), the search stand-in, the two entry tiles, then [state]'s body: the skeleton while it
 * loads, the shelves of the plan, the empty hero, or the offline or refused message with Retry. On the tablet ([phone]
 * false) the bar reaches into the panes' gutters ([mainTabBarBleed]). [bottomPadding] is what the list leaves free at
 * its end, for the create button that floats over it.
 */
@Composable
internal fun CommunityShelvesScreen(
    state: ShelvesUiState,
    actions: ShelvesActions,
    phone: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    Column(modifier.testTag("community.shelves")) {
        LargeTopBar(
            title = { LargeTopBarTitle("Community") },
            modifier = Modifier.mainTabBarBleed(phone),
            subtitle = { LargeTopBarSubtitle("Puzzles from everyone, friends and you") },
            actions = { SecondaryButton(text = "Enter code", onClick = actions.onEnterCode, modifier = Modifier.testTag("community.enterCode")) },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "search", contentType = "search") { ShelvesSearchStub(actions.onSearch, Modifier.padding(horizontal = Spacing.lg)) }
            if (state is ShelvesUiState.Offline) item(key = "offline", contentType = "state") {
                Notice("community.offline", "You're offline", "Reconnect to see community puzzles. Saved friends' puzzles and yours still play.", actions)
            }
            if (state is ShelvesUiState.Refused) item(key = "refused", contentType = "state") {
                Notice("community.refused", "Couldn't load puzzles", "Something went wrong. Try again.", actions)
            }
            item(key = "entries", contentType = "entries") { ShelvesEntryTiles(entries(state), actions, Modifier.padding(horizontal = Spacing.lg)) }
            body(state, actions)
        }
    }
}

/** The entries [state] carries. */
private fun entries(state: ShelvesUiState): ShelvesEntries = when (state) {
    is ShelvesUiState.Loading -> state.entries
    is ShelvesUiState.Content -> state.entries
    is ShelvesUiState.Empty -> state.entries
    is ShelvesUiState.Offline -> state.entries
    is ShelvesUiState.Refused -> state.entries
}

private fun LazyListScope.body(state: ShelvesUiState, actions: ShelvesActions) {
    when (state) {
        is ShelvesUiState.Loading -> item(key = "skeleton", contentType = "skeleton") { ShelvesSkeleton(state.spotlight) }
        is ShelvesUiState.Content -> shelvesSections(state, actions)
        is ShelvesUiState.Empty -> item(key = "empty", contentType = "state") {
            ShelvesEmptyHero({ actions.onMake(null) }, Modifier.padding(horizontal = Spacing.lg))
        }
        is ShelvesUiState.Offline, is ShelvesUiState.Refused -> Unit
    }
}

/** An error [title] and [message] with Retry, centred. */
@Composable
private fun Notice(tag: String, title: String, message: String, actions: ShelvesActions) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        ErrorState(title = title, message = message, onRetry = actions.onRetry, retryLabel = "Retry", modifier = Modifier.testTag(tag))
    }
}
