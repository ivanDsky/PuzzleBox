package com.ivandsky.puzzlebox.profile.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * The DI-bound Reminders block of Settings: draws nothing with the flag off, else the section plus the system side of it,
 * the permission prompt (Android 13 and up; earlier versions have none, so a switch-on that is not allowed is blocked
 * at once) and the trip to the app's notification settings.
 */
@Composable
internal fun RemindersBlock(viewModel: RemindersViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onPermissionResult)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val ask = state?.askPermission == true
    LaunchedEffect(ask) {
        if (!ask) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onPermissionResult(false)
        }
        viewModel.onPermissionAsked()
    }
    val current = state ?: return
    RemindersSection(
        state = current,
        onSetEnabled = viewModel::onSetEnabled,
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        onSetTime = viewModel::onSetTime,
    )
}
