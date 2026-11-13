package com.ivandsky.puzzlebox.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Profile's DI-bound entry point. [onBack] and [onOpenSettings] are the top bar's back icon
 * and gear. A non-null [onConnectLinkedIn] (the shell's LinkedIn switch) opens the LinkedIn row and makes the
 * view model resolve the link; null resolves nothing LinkedIn.
 */
@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onConnectLinkedIn: (() -> Unit)? = null,
    viewModel: ProfileViewModel = koinViewModel(parameters = { parametersOf(onConnectLinkedIn != null) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The credential sheet launches from the current Activity's context (I2) —
    // read in composition (LocalContext is composable-scoped), handed per call.
    val activityContext = LocalContext.current
    // Ticks each minute while Profile is visible; the lifecycle-aware collect stops it in the background.
    val nowMs by viewModel.nowMs.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onSignIn = { viewModel.onSignIn(activityContext) },
        onSignOut = viewModel::onSignOut,
        modifier = modifier.testTag("route.profile"),
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        nowMs = nowMs,
        onConnectLinkedIn = onConnectLinkedIn,
        onResumeLinkedIn = viewModel::onResumeLinkedIn,
        onDisconnectLinkedIn = viewModel::onDisconnectLinkedIn,
        onSyncNow = viewModel::onSyncNow,
        onDeleteCloudData = viewModel::onDeleteCloudData,
        onDeleteSavedBoards = viewModel::onDeleteSavedBoards,
    )
}
