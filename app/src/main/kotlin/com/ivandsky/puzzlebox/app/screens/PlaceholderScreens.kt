package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Home", blurb = "Your daily puzzles will live here.", modifier = modifier)

@Composable
internal fun PlayScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Play", blurb = "Jump into today's games.", modifier = modifier)

@Composable
internal fun ProfileScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Profile", blurb = "Your stats and settings will live here.", modifier = modifier)

@Composable
private fun PlaceholderScreen(
    title: String,
    blurb: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = blurb,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
