package com.ivandsky.puzzlebox.insights.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An empty page: the Insights hub, recap and stats routes show it until their screens land. Nothing opens them yet. */
@Composable
internal fun InsightsPlaceholder() {
    Box(Modifier.fillMaxSize())
}
