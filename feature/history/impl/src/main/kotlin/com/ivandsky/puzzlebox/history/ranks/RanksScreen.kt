package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]. The board slot
 * sits under the page dots; a horizontal swipe on it reports [onSwipe] (+1 next board, -1 previous)
 * and a vertical drag scrolls the hub. The top bar, calendar and sheets join as their own slot composables.
 */
@Composable
internal fun RanksScreen(
    state: RanksUiState,
    onSignIn: () -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .testTag("route.ranks")
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        PageDots(state.boardCount, state.position, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"))
        BoardSwipe(onSwipe) { RanksBoardSlot(state.board, state.signIn, onSignIn) }
    }
}
