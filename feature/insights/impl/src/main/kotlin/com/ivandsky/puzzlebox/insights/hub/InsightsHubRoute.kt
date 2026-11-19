package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * The hub's route: its view model's list on [InsightsHubScreen]. [onOpenRecap] gets the id of the period tapped;
 * [onOpenStats] opens the all-games stats page, null to show no row for it.
 */
@Composable
internal fun InsightsHubRoute(onOpenRecap: (periodId: String) -> Unit, onOpenStats: (() -> Unit)?, onBack: () -> Unit) {
    val viewModel = koinViewModel<InsightsHubViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsHubScreen(state = state, onOpen = { onOpenRecap(it.id) }, onBack = onBack, onOpenStats = onOpenStats)
}
