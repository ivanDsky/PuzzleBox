package com.ivandsky.puzzlebox.profile.linkedin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * The LinkedIn login's DI-bound entry point, reached from Settings Connect and from a day card's
 * Reconnect. [onDone] closes the route: once the link is connected, and on Back or Cancel, which
 * leave it as it was.
 */
@Composable
internal fun LinkedInLoginRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LinkedInLoginViewModel = koinViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    val connected = step == LinkedInLoginViewModel.Step.Done
    LaunchedEffect(connected) { if (connected) done() }
    LinkedInLoginScreen(
        step = step,
        savedToAccount = viewModel.savedToAccount,
        onAccept = viewModel::onAccept,
        onPageFinished = viewModel::onPageFinished,
        onRetry = viewModel::onRetry,
        onBack = onDone,
        modifier = modifier.testTag("route.linkedin.login"),
    )
}
