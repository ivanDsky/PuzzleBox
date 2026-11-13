package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]. Only the board
 * slot is filled; the top bar, calendar and sheets join it as their own slot composables.
 */
@Composable
internal fun RanksScreen(state: RanksUiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .testTag("route.ranks")
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        RanksBoardSlot(state.board)
    }
}
