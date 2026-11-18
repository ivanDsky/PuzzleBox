package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/** What's new's DI-bound entry point. [onBack] returns to Settings. The page reads no flag: only the flagged Settings row leads here. */
@Composable
internal fun WhatsNewRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhatsNewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhatsNewScreen(state = state, onBack = onBack, modifier = modifier.testTag("route.whatsNew"))
}
