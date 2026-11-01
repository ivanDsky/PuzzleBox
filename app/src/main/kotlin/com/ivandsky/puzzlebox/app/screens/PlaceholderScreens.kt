package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Play and Profile stay placeholders until Phase 1; Home is the real :feature:home screen.

@Composable
internal fun PlayScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Play", blurb = "Jump into today's games.", modifier = modifier)

@Composable
internal fun ProfileScreen(modifier: Modifier = Modifier, onOpenCatalog: () -> Unit = {}) =
    PlaceholderScreen(
        title = "Profile",
        blurb = "Your stats and settings will live here.",
        action = {
            Button(onClick = onOpenCatalog) { Text("Design system catalog") }
        },
        modifier = modifier,
    )

// Catalog route fallback (ticket showkase-v2-05): the Showkase browser is debug-only,
// so a build that can't load it — release, or any non-debuggable build — shows a note
// instead of a catalog. The old catalog-screen fallback was deleted with the cutover.
@Composable
internal fun CatalogUnavailableScreen(modifier: Modifier = Modifier) = PlaceholderScreen(
    title = "Design system catalog",
    blurb = "The component browser ships in debug builds only.",
    modifier = modifier,
)

@Composable
private fun PlaceholderScreen(
    title: String,
    blurb: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
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
            action?.invoke()
        }
    }
}
