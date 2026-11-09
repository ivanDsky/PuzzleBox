package com.ivandsky.puzzlebox.profile.settings

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Settings' DI-bound entry point. [onOpenCatalog] is offered only in debuggable builds
 * (the catalog is a debug tool), with the same check Profile uses.
 */
@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    SettingsScreen(
        state = state,
        onSetThemeMode = viewModel::onSetThemeMode,
        onBack = onBack,
        onOpenCatalog = onOpenCatalog.takeIf { debuggable },
        modifier = modifier.testTag("route.settings"),
    )
}
