package com.ivandsky.puzzlebox.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Profile's DI-bound entry point. [onBack] and [onOpenSettings] are the top bar's back icon
 * and gear.
 */
@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The credential sheet launches from the current Activity's context (I2) —
    // read in composition (LocalContext is composable-scoped), handed per call.
    val activityContext = LocalContext.current
    ProfileContent(
        state = state,
        onSignIn = { viewModel.onSignIn(activityContext) },
        onSignOut = viewModel::onSignOut,
        modifier = modifier.testTag("route.profile"),
        onBack = onBack,
        onOpenSettings = onOpenSettings,
    )
}
