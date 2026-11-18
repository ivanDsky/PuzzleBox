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
 * Settings' DI-bound entry point. [onOpenCatalog] and [debugSections] are shown only in debuggable builds
 * (debug tools). [visibility] adds the Visibility section. The Reminders block resolves its own view model and
 * draws nothing while the Reminders flag is off.
 */
@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    onOpenWhatsNew: () -> Unit,
    modifier: Modifier = Modifier,
    debugSections: (@Composable () -> Unit)? = null,
    visibility: VisibilitySlot? = null,
    viewModel: SettingsViewModel = koinViewModel(parameters = { parametersOf(*listOfNotNull(visibility).toTypedArray()) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    SettingsScreen(
        state = state,
        onSetThemeMode = viewModel::onSetThemeMode,
        onSetQueensAutoCross = viewModel::onSetQueensAutoCross,
        onSetSudokuPeerHighlight = viewModel::onSetSudokuPeerHighlight,
        onSetSudokuSameDigitHighlight = viewModel::onSetSudokuSameDigitHighlight,
        onSelectVisibility = viewModel::onSelectVisibility,
        onSetRanksFriendsDefault = viewModel::onSetRanksFriendsDefault,
        onSetVibration = viewModel::onSetVibration,
        onSetShowSelfDuelTimeLeft = viewModel::onSetShowSelfDuelTimeLeft,
        onSignIn = { viewModel.onSignIn(context) },
        onBack = onBack,
        onOpenCatalog = onOpenCatalog.takeIf { debuggable },
        onOpenWhatsNew = onOpenWhatsNew,
        reminders = { RemindersBlock() },
        debugSections = debugSections.takeIf { debuggable },
        modifier = modifier.testTag("route.settings"),
    )
}
