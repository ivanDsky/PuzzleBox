package com.ivandsky.puzzlebox.app.screens

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel

/**
 * Profile's DI-bound entry point. [onOpenHistory] opens History's timeline (N3);
 * [onOpenCatalog] is offered only in debuggable builds (the catalog is a debug tool).
 * [slim] hides the record blocks (identity and sign-in only); a non-null [onBack] draws
 * the back icon. The defaults are the full screen without a back icon.
 */
@Composable
fun ProfileRoute(
    onOpenHistory: (GameId) -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    slim: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The credential sheet launches from the current Activity's context (I2) —
    // read in composition (LocalContext is composable-scoped), handed per call.
    val activityContext = LocalContext.current
    val debuggable = activityContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    ProfileContent(
        state = state,
        onSignIn = { viewModel.onSignIn(activityContext) },
        onSignOut = viewModel::onSignOut,
        onSetThemeMode = viewModel::onSetThemeMode,
        onOpenHistory = onOpenHistory,
        onSelectGame = viewModel::onSelectGame,
        onOpenCatalog = onOpenCatalog.takeIf { debuggable },
        modifier = modifier.testTag("route.profile"),
        slim = slim,
        onBack = onBack,
    )
}
