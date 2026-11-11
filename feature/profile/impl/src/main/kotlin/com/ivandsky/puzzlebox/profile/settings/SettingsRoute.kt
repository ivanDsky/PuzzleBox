package com.ivandsky.puzzlebox.profile.settings

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Settings' DI-bound entry point. [onOpenCatalog] is offered only in debuggable builds
 * (the catalog is a debug tool), with the same check Profile uses. A non-null [onConnectLinkedIn]
 * (the shell's LinkedIn switch) opens the LinkedIn block and makes the view model resolve the link;
 * null resolves nothing LinkedIn.
 */
@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onConnectLinkedIn: (() -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(parameters = { parametersOf(onConnectLinkedIn != null) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    SettingsScreen(
        state = state,
        onSetThemeMode = viewModel::onSetThemeMode,
        onBack = onBack,
        onOpenCatalog = onOpenCatalog.takeIf { debuggable },
        onConnectLinkedIn = onConnectLinkedIn,
        onResumeLinkedIn = viewModel::onResumeLinkedIn,
        onDisconnectLinkedIn = viewModel::onDisconnectLinkedIn,
        modifier = modifier.testTag("route.settings"),
    )
}
