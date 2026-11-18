package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** What's new's entry point. [onBack] returns to Settings. */
@Composable
internal fun WhatsNewRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    WhatsNewScreen(onBack = onBack, modifier = modifier.testTag("route.whatsNew"))
}
