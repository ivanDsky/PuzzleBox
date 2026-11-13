package com.ivandsky.puzzlebox.history.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.ranks.RanksScreen
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The Ranks hub's route for the tab root [HistoryRootKey], the successor of [historyEntry]'s root
 * entry. The module exports both; the app picks one by flag, so this feature never reads it
 * (A27). Same receiver and parameters as [historyEntry]: [navigator] and [layout] are what the
 * hub's links and tablet pane read once they exist.
 */
fun EntryProviderScope<NavKey>.ranksEntry(
    navigator: Navigator,
    layout: HistoryLayout,
) {
    entry<HistoryRootKey> {
        val viewModel = koinViewModel<RanksViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        RanksScreen(state, onSignIn = { scope.launch { viewModel.signIn(context) } })
    }
}
